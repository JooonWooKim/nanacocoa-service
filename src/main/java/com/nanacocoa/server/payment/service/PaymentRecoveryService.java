package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentRecoveryService {
	private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryService.class);
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

	private void reconcileApprovalSafely(Long paymentId, String orderId) {
		try {
			paymentLockManager.withOrderLock(orderId, () -> {
				reconcileApproval(paymentId);
				return null;
			});
		} catch (RuntimeException exception) {
			log.warn("Payment approval reconciliation deferred. paymentId={}", paymentId, exception);
		}
	}

	private void reconcileApproval(Long paymentId) {
		ApprovalWork work = transactionalService.getRecoveryApproval(paymentId);
		if (work.status() != PaymentStatus.PENDING || work.recoveryAction() == PaymentRecoveryAction.NONE) {
			return;
		}

		TossPaymentResponse response;
		try {
			response = tossPaymentsClient.findByOrderId(work.orderId());
		} catch (TossPaymentsException exception) {
			handleApprovalLookupFailure(work, exception);
			return;
		}

		if (response.isCanceled()) {
			transactionalService.completeCompensation(work.paymentId(), response);
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
			return;
		}

		try {
			transactionalService.completeApproval(work.paymentId(), response);
		} catch (RuntimeException exception) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"RECOVERY_COMPLETION_FAILED",
					exception.getMessage()
			);
		}
	}

	private void handleApprovalLookupFailure(ApprovalWork work, TossPaymentsException exception) {
		if (exception.isNotFound() && work.recoveryAction() == PaymentRecoveryAction.VERIFY_APPROVAL) {
			if (work.createdAt().plus(approvalExpiry).isBefore(LocalDateTime.now())) {
				transactionalService.failApproval(work.paymentId(), exception.getCode(), exception.getMessage());
			} else {
				retryApprovalWithSameIdempotencyKey(work);
			}
			return;
		}
		if (work.recoveryAction() == PaymentRecoveryAction.COMPENSATE_APPROVAL) {
			transactionalService.requestCompensation(work.paymentId(), exception.getCode(), exception.getMessage());
		} else {
			transactionalService.recordUncertainApproval(work.paymentId(), exception.getCode(), exception.getMessage());
		}
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
		} catch (TossPaymentsException exception) {
			if (exception.isDefinitive()) {
				transactionalService.failApproval(work.paymentId(), exception.getCode(), exception.getMessage());
			} else {
				transactionalService.recordUncertainApproval(work.paymentId(), exception.getCode(), exception.getMessage());
			}
		} catch (RuntimeException exception) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"RECOVERY_COMPLETION_FAILED",
					exception.getMessage()
			);
		}
	}

	private void compensateRecoveredApproval(ApprovalWork work, TossPaymentResponse lookupResponse) {
		if (!lookupResponse.isDone()) {
			transactionalService.requestCompensation(
					work.paymentId(),
					"COMPENSATION_LOOKUP_PENDING",
					"보상할 승인 결제 상태를 확인할 수 없습니다."
			);
			return;
		}

		try {
			TossPaymentResponse cancelResponse = tossPaymentsClient.cancel(
					work.paymentKey(),
					COMPENSATION_REASON,
					work.compensationIdempotencyKey()
			);
			transactionalService.completeCompensation(work.paymentId(), cancelResponse);
		} catch (TossPaymentsException exception) {
			transactionalService.requestCompensation(work.paymentId(), exception.getCode(), exception.getMessage());
		}
	}

	private void reconcileCancellationSafely(Long cancellationId, String orderId) {
		try {
			paymentLockManager.withOrderLock(orderId, () -> {
				reconcileCancellation(cancellationId);
				return null;
			});
		} catch (RuntimeException exception) {
			log.warn("Payment cancellation reconciliation deferred. cancellationId={}", cancellationId, exception);
		}
	}

	private void reconcileCancellation(Long cancellationId) {
		CancellationWork work = transactionalService.getRecoveryCancellation(cancellationId);
		if (work.status() != CancellationStatus.PENDING) {
			return;
		}

		TossPaymentResponse lookup;
		try {
			lookup = tossPaymentsClient.findByOrderId(work.orderId());
		} catch (TossPaymentsException exception) {
			transactionalService.recordUncertainCancellation(
					work.cancellationId(),
					exception.getCode(),
					exception.getMessage()
			);
			return;
		}

		if (lookup.isCanceled()) {
			transactionalService.completeCancellation(work.cancellationId(), lookup);
			return;
		}

		if (!lookup.isDone()) {
			transactionalService.recordUncertainCancellation(
					work.cancellationId(),
					"CANCEL_LOOKUP_PENDING",
					"취소 가능한 결제 상태를 확인할 수 없습니다."
			);
			return;
		}

		try {
			TossPaymentResponse cancelResponse = tossPaymentsClient.cancel(
					work.paymentKey(),
					work.reason(),
					work.idempotencyKey()
			);
			transactionalService.completeCancellation(work.cancellationId(), cancelResponse);
		} catch (TossPaymentsException exception) {
			if (exception.isDefinitive()) {
				transactionalService.failCancellation(work.cancellationId(), exception.getCode(), exception.getMessage());
			} else {
				transactionalService.recordUncertainCancellation(
						work.cancellationId(),
						exception.getCode(),
						exception.getMessage()
				);
			}
		} catch (NanacocoaException exception) {
			throw exception;
		}
	}
}
