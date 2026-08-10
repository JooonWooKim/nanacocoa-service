package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.dto.response.PaymentResponse;
import com.nanacocoa.server.payment.entity.CancellationStatus;
import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentCancellation;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.pg.TossPaymentResponse;
import com.nanacocoa.server.payment.repository.PaymentCancellationRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.nanacocoa.server.common.exception.ErrorCode.IDEMPOTENCY_KEY_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_PAYMENT_STATUS;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_ORDER;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_PAYMENT;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_ALREADY_PROCESSED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_AMOUNT_MISMATCH;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_APPROVAL_FAILED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_CANCEL_FAILED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_IN_PROGRESS;
import static com.nanacocoa.server.common.exception.ErrorCode.UNSUPPORTED_PAYMENT_STATUS;

@Service
public class PaymentTransactionalService {
	private final PaymentRepository paymentRepository;
	private final PaymentCancellationRepository cancellationRepository;
	private final OrderRepository orderRepository;
	private final Duration retryDelay;

	public PaymentTransactionalService(
			PaymentRepository paymentRepository,
			PaymentCancellationRepository cancellationRepository,
			OrderRepository orderRepository,
			@Value("${payment.reconciliation.retry-delay:30s}") Duration retryDelay) {
		this.paymentRepository = paymentRepository;
		this.cancellationRepository = cancellationRepository;
		this.orderRepository = orderRepository;
		this.retryDelay = retryDelay;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ApprovalWork prepareApproval(
			ConfirmPaymentRequest request,
			String idempotencyKey,
			String fingerprint,
			String memberEmail) {
		validateIdempotencyKey(idempotencyKey);

		Payment existing = paymentRepository.findByApprovalIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			return replayApproval(existing, fingerprint);
		}

		Order order = orderRepository.findByOrderIdForUpdate(request.getOrderId())
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
		if (!order.isOwnedBy(memberEmail)) {
			throw new NanacocoaException(NOT_FOUND_ORDER);
		}
		if (!Objects.equals(order.getTotalAmount(), request.getAmount())) {
			throw new NanacocoaException(PAYMENT_AMOUNT_MISMATCH);
		}
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			throw new NanacocoaException(PAYMENT_IN_PROGRESS);
		}
		if (order.getStatus() != OrderStatus.CREATED) {
			throw new NanacocoaException(PAYMENT_ALREADY_PROCESSED);
		}
		if (paymentRepository.findByPaymentKey(request.getPaymentKey()).isPresent()) {
			throw new NanacocoaException(PAYMENT_ALREADY_PROCESSED);
		}

		order.startPayment();
		Payment payment = Payment.pending(
				order,
				request.getPaymentKey(),
				idempotencyKey,
				fingerprint,
				UUID.randomUUID().toString(),
				order.getTotalAmount(),
				nextRetryAt()
		);
		paymentRepository.saveAndFlush(payment);
		return ApprovalWork.from(payment, false);
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public ApprovalWork replayApproval(String idempotencyKey, String fingerprint) {
		validateIdempotencyKey(idempotencyKey);
		Payment payment = paymentRepository.findByApprovalIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new NanacocoaException(PAYMENT_IN_PROGRESS));
		return replayApproval(payment, fingerprint);
	}

	private ApprovalWork replayApproval(Payment payment, String fingerprint) {
		if (!payment.getRequestFingerprint().equals(fingerprint)) {
			throw new NanacocoaException(IDEMPOTENCY_KEY_REUSED);
		}
		if (payment.getStatus() == PaymentStatus.FAILED) {
			throw new NanacocoaException(PAYMENT_APPROVAL_FAILED);
		}
		return ApprovalWork.from(payment, true);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentResponse completeApproval(Long paymentId, TossPaymentResponse pgResponse) {
		Payment payment = paymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.SUCCESS) {
			return PaymentResponse.from(payment);
		}
		validateApprovalResponse(payment, pgResponse);
		payment.complete(pgResponse.getStatus(), pgResponse.getMethod(), pgResponse.approvedAtLocal());
		paymentRepository.flush();
		return PaymentResponse.from(payment);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failApproval(Long paymentId, String code, String message) {
		Payment payment = paymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.fail(code, message);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentResponse recordUncertainApproval(Long paymentId, String code, String message) {
		Payment payment = paymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.recordUncertainResult(code, message, nextRetryAt());
		}
		return PaymentResponse.from(payment);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void requestCompensation(Long paymentId, String code, String message) {
		Payment payment = paymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.requestCompensation(code, message, nextRetryAt());
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentResponse completeCompensation(Long paymentId, TossPaymentResponse pgResponse) {
		Payment payment = paymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			return PaymentResponse.from(payment);
		}
		validateCanceledResponse(payment, pgResponse);
		payment.canceledByCompensation(pgResponse.getStatus(), pgResponse.canceledAtLocal());
		return PaymentResponse.from(payment);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CancellationWork prepareCancellation(
			Long paymentId,
			String reason,
			String idempotencyKey,
			String fingerprint,
			String memberEmail) {
		validateIdempotencyKey(idempotencyKey);
		PaymentCancellation existing = cancellationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			return replayCancellation(existing, fingerprint);
		}

		Payment payment = paymentForUpdate(paymentId);
		if (!payment.getOrder().isOwnedBy(memberEmail)) {
			throw new NanacocoaException(NOT_FOUND_PAYMENT);
		}
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			return CancellationWork.alreadyCanceled(payment);
		}
		if (payment.getStatus() == PaymentStatus.CANCEL_PENDING
				|| cancellationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
				paymentId,
				CancellationStatus.PENDING
		).isPresent()) {
			throw new NanacocoaException(PAYMENT_IN_PROGRESS);
		}
		if (payment.getStatus() != PaymentStatus.SUCCESS) {
			throw new NanacocoaException(INVALID_PAYMENT_STATUS);
		}

		payment.startCancellation();
		PaymentCancellation cancellation = PaymentCancellation.pending(
				payment,
				idempotencyKey,
				fingerprint,
				reason,
				nextRetryAt()
		);
		cancellationRepository.saveAndFlush(cancellation);
		return CancellationWork.from(cancellation, false);
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public CancellationWork replayCancellation(String idempotencyKey, String fingerprint) {
		validateIdempotencyKey(idempotencyKey);
		PaymentCancellation cancellation = cancellationRepository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new NanacocoaException(PAYMENT_IN_PROGRESS));
		return replayCancellation(cancellation, fingerprint);
	}

	private CancellationWork replayCancellation(PaymentCancellation cancellation, String fingerprint) {
		if (!cancellation.getRequestFingerprint().equals(fingerprint)) {
			throw new NanacocoaException(IDEMPOTENCY_KEY_REUSED);
		}
		if (cancellation.getStatus() == CancellationStatus.FAILED) {
			throw new NanacocoaException(PAYMENT_CANCEL_FAILED);
		}
		return CancellationWork.from(cancellation, true);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentResponse completeCancellation(Long cancellationId, TossPaymentResponse pgResponse) {
		PaymentCancellation cancellation = cancellationForUpdate(cancellationId);
		Payment payment = cancellation.getPayment();
		if (cancellation.getStatus() == CancellationStatus.SUCCESS
				&& payment.getStatus() == PaymentStatus.CANCELED) {
			return PaymentResponse.from(payment);
		}
		validateCanceledResponse(payment, pgResponse);
		cancellation.complete(pgResponse.lastCancellationTransactionKey());
		payment.canceledByUser(pgResponse.getStatus(), pgResponse.canceledAtLocal());
		return PaymentResponse.from(payment);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failCancellation(Long cancellationId, String code, String message) {
		PaymentCancellation cancellation = cancellationForUpdate(cancellationId);
		if (cancellation.getStatus() == CancellationStatus.PENDING) {
			cancellation.fail(code, message);
			if (cancellation.getPayment().getStatus() == PaymentStatus.CANCEL_PENDING) {
				cancellation.getPayment().cancellationFailed();
			}
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentResponse recordUncertainCancellation(Long cancellationId, String code, String message) {
		PaymentCancellation cancellation = cancellationForUpdate(cancellationId);
		if (cancellation.getStatus() == CancellationStatus.PENDING) {
			cancellation.recordUncertainResult(code, message, nextRetryAt());
		}
		return PaymentResponse.from(cancellation.getPayment());
	}

	@Transactional(readOnly = true)
	public PaymentResponse getOwnedPayment(Long paymentId, String memberEmail) {
		Payment payment = paymentRepository.findDetailsById(paymentId)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
		if (!payment.getOrder().isOwnedBy(memberEmail)) {
			throw new NanacocoaException(NOT_FOUND_PAYMENT);
		}
		return PaymentResponse.from(payment);
	}

	@Transactional(readOnly = true)
	public String getOwnedOrderId(Long paymentId, String memberEmail) {
		Payment payment = paymentRepository.findDetailsById(paymentId)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
		if (!payment.getOrder().isOwnedBy(memberEmail)) {
			throw new NanacocoaException(NOT_FOUND_PAYMENT);
		}
		return payment.getOrder().getOrderId();
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public ApprovalWork getRecoveryApproval(Long paymentId) {
		Payment payment = paymentRepository.findDetailsById(paymentId)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
		return ApprovalWork.from(payment, true);
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public CancellationWork getRecoveryCancellation(Long cancellationId) {
		PaymentCancellation cancellation = cancellationRepository.findById(cancellationId)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
		cancellation.getPayment().getOrder().getOrderId();
		return CancellationWork.from(cancellation, true);
	}

	private Payment paymentForUpdate(Long paymentId) {
		return paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
	}

	private PaymentCancellation cancellationForUpdate(Long cancellationId) {
		return cancellationRepository.findByIdForUpdate(cancellationId)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PAYMENT));
	}

	private void validateApprovalResponse(Payment payment, TossPaymentResponse response) {
		if (!Objects.equals(payment.getPaymentKey(), response.getPaymentKey())
				|| !Objects.equals(payment.getOrder().getOrderId(), response.getOrderId())
				|| !Objects.equals(payment.getAmount(), response.getTotalAmount())) {
			throw new NanacocoaException(PAYMENT_AMOUNT_MISMATCH);
		}
		if (!response.isDone()) {
			throw new NanacocoaException(UNSUPPORTED_PAYMENT_STATUS);
		}
	}

	private void validateCanceledResponse(Payment payment, TossPaymentResponse response) {
		if (!Objects.equals(payment.getPaymentKey(), response.getPaymentKey()) || !response.isCanceled()) {
			throw new NanacocoaException(UNSUPPORTED_PAYMENT_STATUS);
		}
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 300) {
			throw new NanacocoaException(IDEMPOTENCY_KEY_REQUIRED);
		}
	}

	private LocalDateTime nextRetryAt() {
		return LocalDateTime.now().plus(retryDelay);
	}

	public record ApprovalWork(
			Long paymentId,
			String paymentKey,
			String orderId,
			long amount,
			String approvalIdempotencyKey,
			String compensationIdempotencyKey,
			PaymentStatus status,
			PaymentRecoveryAction recoveryAction,
			LocalDateTime createdAt,
			boolean replay,
			PaymentResponse response) {
		static ApprovalWork from(Payment payment, boolean replay) {
			return new ApprovalWork(
					payment.getId(),
					payment.getPaymentKey(),
					payment.getOrder().getOrderId(),
					payment.getAmount(),
					payment.getApprovalIdempotencyKey(),
					payment.getCompensationIdempotencyKey(),
					payment.getStatus(),
					payment.getRecoveryAction(),
					payment.getCreatedAt(),
					replay,
					PaymentResponse.from(payment)
			);
		}
	}

	public record CancellationWork(
			Long cancellationId,
			Long paymentId,
			String paymentKey,
			String orderId,
			String idempotencyKey,
			String reason,
			CancellationStatus status,
			boolean replay,
			PaymentResponse response) {
		static CancellationWork from(PaymentCancellation cancellation, boolean replay) {
			Payment payment = cancellation.getPayment();
			return new CancellationWork(
					cancellation.getId(),
					payment.getId(),
					payment.getPaymentKey(),
					payment.getOrder().getOrderId(),
					cancellation.getIdempotencyKey(),
					cancellation.getReason(),
					cancellation.getStatus(),
					replay,
					PaymentResponse.from(payment)
			);
		}

		static CancellationWork alreadyCanceled(Payment payment) {
			return new CancellationWork(
					null,
					payment.getId(),
					payment.getPaymentKey(),
					payment.getOrder().getOrderId(),
					null,
					null,
					CancellationStatus.SUCCESS,
					true,
					PaymentResponse.from(payment)
			);
		}
	}
}
