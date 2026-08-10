package com.nanacocoa.server.payment.facade;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.payment.dto.reqeust.CancelPaymentRequest;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.dto.response.PaymentResponse;
import com.nanacocoa.server.payment.entity.CancellationStatus;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.lock.OrderPaymentLock;
import com.nanacocoa.server.payment.pg.TossPaymentResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsException;
import com.nanacocoa.server.payment.service.PaymentFingerprintService;
import com.nanacocoa.server.payment.service.PaymentTransactionalService;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.ApprovalWork;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.CancellationWork;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import static com.nanacocoa.server.common.exception.ErrorCode.AUTHENTICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_APPROVAL_FAILED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_CANCEL_FAILED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class PaymentFacade {
	private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);
	private static final String COMPENSATION_REASON = "내부 결제 확정 실패에 따른 자동 보상 취소";

	private final OrderPaymentLock paymentLockManager;
	private final PaymentTransactionalService transactionalService;
	private final PaymentFingerprintService fingerprintService;
	private final TossPaymentsClient tossPaymentsClient;

	public PaymentResponse confirm(
			ConfirmPaymentRequest request,
			String idempotencyKey,
			UserDetailsImpl userDetails) {
		String email = authenticatedEmail(userDetails);
		String fingerprint = fingerprintService.approval(email, request);
		return paymentLockManager.withOrderLock(
				request.getOrderId(),
				() -> confirmLocked(request, idempotencyKey, fingerprint, email)
		);
	}

	public PaymentResponse getPayment(Long paymentId, UserDetailsImpl userDetails) {
		return transactionalService.getOwnedPayment(paymentId, authenticatedEmail(userDetails));
	}

	public PaymentResponse cancel(
			Long paymentId,
			CancelPaymentRequest request,
			String idempotencyKey,
			UserDetailsImpl userDetails) {
		String email = authenticatedEmail(userDetails);
		String orderId = transactionalService.getOwnedOrderId(paymentId, email);
		String fingerprint = fingerprintService.cancellation(email, paymentId, request.getCancelReason());
		return paymentLockManager.withOrderLock(
				orderId,
				() -> cancelLocked(paymentId, request, idempotencyKey, fingerprint, email)
		);
	}

	private PaymentResponse confirmLocked(
			ConfirmPaymentRequest request,
			String idempotencyKey,
			String fingerprint,
			String email) {
		ApprovalWork work;
		try {
			work = transactionalService.prepareApproval(request, idempotencyKey, fingerprint, email);
		} catch (DataIntegrityViolationException exception) {
			work = transactionalService.replayApproval(idempotencyKey, fingerprint);
		}

		if (work.status() != PaymentStatus.PENDING) {
			return work.response();
		}

		TossPaymentResponse pgResponse;
		try {
			pgResponse = tossPaymentsClient.confirm(
					work.paymentKey(),
					work.orderId(),
					work.amount(),
					work.approvalIdempotencyKey()
			);
		} catch (TossPaymentsException exception) {
			return handleApprovalProviderFailure(work, exception);
		}

		try {
			return transactionalService.completeApproval(work.paymentId(), pgResponse);
		} catch (RuntimeException completionFailure) {
			compensateApproval(work, completionFailure);
			throw new NanacocoaException(PAYMENT_APPROVAL_FAILED);
		}
	}

	private PaymentResponse handleApprovalProviderFailure(ApprovalWork work, TossPaymentsException exception) {
		if (exception.isDefinitive()) {
			transactionalService.failApproval(work.paymentId(), exception.getCode(), exception.getMessage());
			throw new NanacocoaException(PAYMENT_APPROVAL_FAILED);
		}
		if (exception.getKind() == TossPaymentsException.Kind.UNAVAILABLE) {
			transactionalService.failApproval(work.paymentId(), exception.getCode(), exception.getMessage());
			throw new NanacocoaException(PAYMENT_PROVIDER_UNAVAILABLE);
		}
		return transactionalService.recordUncertainApproval(
				work.paymentId(),
				exception.getCode(),
				exception.getMessage()
		);
	}

	private void compensateApproval(ApprovalWork work, RuntimeException completionFailure) {
		log.error(
				"Internal payment completion failed; starting compensation. paymentId={}, orderId={}",
				work.paymentId(),
				work.orderId(),
				completionFailure
		);
		try {
			transactionalService.requestCompensation(
					work.paymentId(),
					"INTERNAL_COMPLETION_FAILED",
					completionFailure.getMessage()
			);
		} catch (RuntimeException recoveryPersistenceFailure) {
			log.error("Failed to persist compensation intent. paymentId={}", work.paymentId(), recoveryPersistenceFailure);
		}

		try {
			TossPaymentResponse cancelResponse = tossPaymentsClient.cancel(
					work.paymentKey(),
					COMPENSATION_REASON,
					work.compensationIdempotencyKey()
			);
			transactionalService.completeCompensation(work.paymentId(), cancelResponse);
		} catch (RuntimeException compensationFailure) {
			log.error("Payment compensation remains unresolved. paymentId={}", work.paymentId(), compensationFailure);
			try {
				transactionalService.requestCompensation(
						work.paymentId(),
						"COMPENSATION_UNCERTAIN",
						compensationFailure.getMessage()
				);
			} catch (RuntimeException ignored) {
				log.error("Failed to reschedule payment compensation. paymentId={}", work.paymentId(), ignored);
			}
		}
	}

	private PaymentResponse cancelLocked(
			Long paymentId,
			CancelPaymentRequest request,
			String idempotencyKey,
			String fingerprint,
			String email) {
		CancellationWork work;
		try {
			work = transactionalService.prepareCancellation(
					paymentId,
					request.getCancelReason(),
					idempotencyKey,
					fingerprint,
					email
			);
		} catch (DataIntegrityViolationException exception) {
			work = transactionalService.replayCancellation(idempotencyKey, fingerprint);
		}

		if (work.cancellationId() == null || work.status() == CancellationStatus.SUCCESS) {
			return work.response();
		}

		TossPaymentResponse pgResponse;
		try {
			pgResponse = tossPaymentsClient.cancel(
					work.paymentKey(),
					work.reason(),
					work.idempotencyKey()
			);
		} catch (TossPaymentsException exception) {
			return handleCancellationProviderFailure(work, exception);
		}

		try {
			return transactionalService.completeCancellation(work.cancellationId(), pgResponse);
		} catch (RuntimeException completionFailure) {
			log.error("Payment cancellation DB completion failed. cancellationId={}", work.cancellationId(), completionFailure);
			return transactionalService.recordUncertainCancellation(
					work.cancellationId(),
					"CANCEL_COMPLETION_UNCERTAIN",
					completionFailure.getMessage()
			);
		}
	}

	private PaymentResponse handleCancellationProviderFailure(
			CancellationWork work,
			TossPaymentsException exception) {
		if (exception.isDefinitive()) {
			transactionalService.failCancellation(work.cancellationId(), exception.getCode(), exception.getMessage());
			throw new NanacocoaException(PAYMENT_CANCEL_FAILED);
		}
		if (exception.getKind() == TossPaymentsException.Kind.UNAVAILABLE) {
			transactionalService.failCancellation(work.cancellationId(), exception.getCode(), exception.getMessage());
			throw new NanacocoaException(PAYMENT_PROVIDER_UNAVAILABLE);
		}
		return transactionalService.recordUncertainCancellation(
				work.cancellationId(),
				exception.getCode(),
				exception.getMessage()
		);
	}

	private String authenticatedEmail(UserDetailsImpl userDetails) {
		if (userDetails == null) {
			throw new NanacocoaException(AUTHENTICATION_REQUIRED);
		}
		return userDetails.getUsername();
	}
}
