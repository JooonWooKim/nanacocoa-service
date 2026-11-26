package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.observability.IncidentTrace;
import com.nanacocoa.server.payment.entity.CancellationStatus;
import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentCancellation;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.lock.OrderPaymentLock;
import com.nanacocoa.server.payment.pg.TossPaymentResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsException;
import com.nanacocoa.server.payment.repository.PaymentCancellationRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.ApprovalWork;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.CancellationWork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.nanacocoa.server.common.observability.IncidentTrace.Operation.*;
import static com.nanacocoa.server.common.observability.IncidentTrace.Result.*;

@Service
public class PaymentRecoveryService {
	private static final String COMPENSATION_REASON = "내부 결제 확정 실패에 따른 자동 보상 취소";

	private final PaymentRepository paymentRepository;
	private final PaymentCancellationRepository cancellationRepository;
	private final PaymentTransactionalService transactionalService;
	private final OrderPaymentLock paymentLockManager;
	private final TossPaymentsClient tossPaymentsClient;
	private final Duration approvalExpiry;

	public PaymentRecoveryService(
			PaymentRepository paymentRepository,
			PaymentCancellationRepository cancellationRepository,
			PaymentTransactionalService transactionalService,
			OrderPaymentLock paymentLockManager,
			TossPaymentsClient tossPaymentsClient,
			@Value("${payment.reconciliation.approval-expiry:11m}") Duration approvalExpiry) {
		this.paymentRepository = paymentRepository;
		this.cancellationRepository = cancellationRepository;
		this.transactionalService = transactionalService;
		this.paymentLockManager = paymentLockManager;
		this.tossPaymentsClient = tossPaymentsClient;
		this.approvalExpiry = approvalExpiry;
	}

	public void reconcileDueApprovals(int batchSize) {
		List<Payment> payments = paymentRepository.findRecoverable(
				PaymentRecoveryAction.NONE,
				LocalDateTime.now(),
				PageRequest.of(0, batchSize)
		);
		for (Payment payment : payments) {
			reconcileApprovalSafely(payment.getId(), payment.getOrder().getOrderId());
		}
	}

	public void reconcileDueCancellations(int batchSize) {
		List<PaymentCancellation> cancellations = cancellationRepository.findRecoverable(
				CancellationStatus.PENDING,
				LocalDateTime.now(),
				PageRequest.of(0, batchSize)
		);
		for (PaymentCancellation cancellation : cancellations) {
			reconcileCancellationSafely(
					cancellation.getId(),
					cancellation.getPayment().getOrder().getOrderId()
			);
		}
	}

	// 수동 실행과 배치 실행은 동일한 주문 락과 최신 복구 상태를 사용한다.
	public void reconcileOneApproval(Long id, String orderId) {
		paymentLockManager.withOrderLock(orderId, () -> { reconcileApproval(id, true); return null; });
	}

	public void reconcileOneCancellation(Long id, String orderId) {
		paymentLockManager.withOrderLock(orderId, () -> { reconcileCancellation(id, true); return null; });
	}

	private boolean ready(LocalDateTime retryAt, boolean manual) {
		if (retryAt != null && !retryAt.isAfter(LocalDateTime.now())) return true;
		if (manual) throw new NanacocoaException(com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_RECOVERY_DEFERRED);
		return false;
	}

	private void reconcileApprovalSafely(Long paymentId, String orderId) {
		try (var trace = IncidentTrace.recoveryAttempt(RECOVERY_APPROVAL)) {
			try {
				paymentLockManager.withOrderLock(orderId, () -> {
					reconcileApproval(paymentId, false);
					return null;
				});
			} catch (RuntimeException exception) {
				IncidentTrace.mark(INTERNAL_ERROR);
			}
		}
	}

	private void reconcileApproval(Long paymentId, boolean manual) {
		ApprovalWork work = transactionalService.getRecoveryApproval(paymentId);
		if (work.status() != PaymentStatus.PENDING || work.recoveryAction() == PaymentRecoveryAction.NONE) {
			return;
		}

		if (!ready(work.nextRetryAt(), manual)) return;
		if (work.recoveryAction() == PaymentRecoveryAction.COMPENSATE_APPROVAL) IncidentTrace.markCompensation();

		TossPaymentResponse response;
		try {
			response = tossPaymentsClient.findByOrderId(work.orderId());
		} catch (TossPaymentsException exception) {
			handleApprovalLookupFailure(work, exception);
			return;
		}

		if (!Objects.equals(work.paymentKey(), response.getPaymentKey())
				|| !Objects.equals(work.orderId(), response.getOrderId())
				|| !Objects.equals(work.amount(), response.getTotalAmount())) {
			rescheduleApproval(work, "PG_PAYMENT_MISMATCH", "결제사 조회 결과가 요청한 결제와 일치하지 않습니다.");
			return;
		}

		if ("ABORTED".equals(response.getStatus()) || "EXPIRED".equals(response.getStatus())) {
			transactionalService.failApproval(work.paymentId(), response.getStatus(), "결제사가 승인 실패를 확인했습니다.");
			IncidentTrace.mark(FAILED);
			return;
		}

		if (response.isCanceled()) {
			IncidentTrace.markCompensation();
			transactionalService.completeCompensation(work.paymentId(), response);
			IncidentTrace.mark(SUCCESS);
			return;
		}

		if (work.recoveryAction() == PaymentRecoveryAction.COMPENSATE_APPROVAL) {
			compensateRecoveredApproval(work, response);
			return;
		}

		if (!response.isDone()) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"UNSUPPORTED_PG_STATUS",
					"지원하지 않는 PG 상태: " + response.getStatus()
			);
			IncidentTrace.mark(PENDING);
			return;
		}

		try {
			transactionalService.completeApproval(work.paymentId(), response);
			IncidentTrace.mark(SUCCESS);
		} catch (RuntimeException exception) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"RECOVERY_COMPLETION_FAILED",
					exception.getMessage()
			);
			IncidentTrace.mark(PENDING);
		}
	}

	private void handleApprovalLookupFailure(ApprovalWork work, TossPaymentsException exception) {
		if (exception.isNotFound() && work.recoveryAction() == PaymentRecoveryAction.VERIFY_APPROVAL) {
			if (work.createdAt().plus(approvalExpiry).isBefore(LocalDateTime.now())) {
				transactionalService.failApproval(work.paymentId(), exception.getCode(), exception.getMessage());
				IncidentTrace.mark(FAILED);
			} else {
				retryApprovalWithSameIdempotencyKey(work);
			}
			return;
		}
		rescheduleApproval(work, exception.getCode(), exception.getMessage());
	}

	private void rescheduleApproval(ApprovalWork work, String code, String message) {
		if (work.recoveryAction() == PaymentRecoveryAction.COMPENSATE_APPROVAL) {
			transactionalService.requestCompensation(work.paymentId(), code, message);
		} else {
			transactionalService.recordUncertainApproval(work.paymentId(), code, message);
		}
		IncidentTrace.mark(PENDING);
	}

	private void retryApprovalWithSameIdempotencyKey(ApprovalWork work) {
		try {
			TossPaymentResponse response = tossPaymentsClient.confirm(
					work.paymentKey(),
					work.orderId(),
					work.amount(),
					work.approvalIdempotencyKey()
			);
			transactionalService.completeApproval(work.paymentId(), response);
			IncidentTrace.mark(SUCCESS);
		} catch (TossPaymentsException exception) {
			if (exception.isDefinitive()) {
				transactionalService.failApproval(work.paymentId(), exception.getCode(), exception.getMessage());
				IncidentTrace.mark(FAILED);
			} else {
				transactionalService.recordUncertainApproval(work.paymentId(), exception.getCode(), exception.getMessage());
				IncidentTrace.mark(PENDING);
			}
		} catch (RuntimeException exception) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"RECOVERY_COMPLETION_FAILED",
					exception.getMessage()
			);
			IncidentTrace.mark(PENDING);
		}
	}

	private void compensateRecoveredApproval(ApprovalWork work, TossPaymentResponse lookupResponse) {
		if (!lookupResponse.isDone()) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"COMPENSATION_LOOKUP_PENDING",
					"보상할 승인 결제 상태를 확인할 수 없습니다."
			);
			IncidentTrace.mark(PENDING);
			return;
		}

		try {
			TossPaymentResponse cancelResponse = tossPaymentsClient.cancel(
					work.paymentKey(),
					COMPENSATION_REASON,
					work.compensationIdempotencyKey()
			);
			transactionalService.completeCompensation(work.paymentId(), cancelResponse);
			IncidentTrace.mark(SUCCESS);
		} catch (TossPaymentsException exception) {
			transactionalService.requestCompensation(work.paymentId(), exception.getCode(), exception.getMessage());
			IncidentTrace.mark(PENDING);
		}
	}

	private void reconcileCancellationSafely(Long cancellationId, String orderId) {
		try (var trace = IncidentTrace.recoveryAttempt(RECOVERY_CANCELLATION)) {
			try {
				paymentLockManager.withOrderLock(orderId, () -> {
					reconcileCancellation(cancellationId, false);
					return null;
				});
			} catch (RuntimeException exception) {
				IncidentTrace.mark(INTERNAL_ERROR);
			}
		}
	}

	private void reconcileCancellation(Long cancellationId, boolean manual) {
		CancellationWork work = transactionalService.getRecoveryCancellation(cancellationId);
		if (work.status() != CancellationStatus.PENDING) {
			return;
		}

		if (!ready(work.nextRetryAt(), manual)) return;

		TossPaymentResponse lookup;
		try {
			lookup = tossPaymentsClient.findByOrderId(work.orderId());
		} catch (TossPaymentsException exception) {
			transactionalService.recordUncertainCancellation(
					work.cancellationId(),
					exception.getCode(),
					exception.getMessage()
			);
			IncidentTrace.mark(PENDING);
			return;
		}

		if (!Objects.equals(work.paymentKey(), lookup.getPaymentKey())
				|| !Objects.equals(work.orderId(), lookup.getOrderId())
				|| !Objects.equals(work.amount(), lookup.getTotalAmount())) {
			transactionalService.recordUncertainCancellation(work.cancellationId(), "PG_PAYMENT_MISMATCH", "결제사 조회 결과 불일치");
			IncidentTrace.mark(PENDING);
			return;
		}

		if (lookup.isCanceled()) {
			transactionalService.completeCancellation(work.cancellationId(), lookup);
			IncidentTrace.mark(SUCCESS);
			return;
		}

		if (!lookup.isDone()) {
			transactionalService.recordUncertainCancellation(
					work.cancellationId(),
					"CANCEL_LOOKUP_PENDING",
					"취소 가능한 결제 상태를 확인할 수 없습니다."
			);
			IncidentTrace.mark(PENDING);
			return;
		}

		try {
			TossPaymentResponse cancelResponse = tossPaymentsClient.cancel(
					work.paymentKey(),
					work.reason(),
					work.idempotencyKey()
			);
			transactionalService.completeCancellation(work.cancellationId(), cancelResponse);
			IncidentTrace.mark(SUCCESS);
		} catch (TossPaymentsException exception) {
			if (exception.isDefinitive()) {
				transactionalService.failCancellation(work.cancellationId(), exception.getCode(), exception.getMessage());
				IncidentTrace.mark(FAILED);
			} else {
				transactionalService.recordUncertainCancellation(
						work.cancellationId(),
						exception.getCode(),
						exception.getMessage()
				);
				IncidentTrace.mark(PENDING);
			}
		} catch (NanacocoaException exception) {
			throw exception;
		}
	}
}
