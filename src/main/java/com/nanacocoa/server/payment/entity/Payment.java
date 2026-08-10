package com.nanacocoa.server.payment.entity;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_PAYMENT_STATUS;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(name = "payment_key", nullable = false, unique = true, length = 200)
	private String paymentKey;

	@Column(name = "approval_idempotency_key", nullable = false, unique = true, length = 300)
	private String approvalIdempotencyKey;

	@Column(name = "request_fingerprint", nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String requestFingerprint;

	@Column(name = "compensation_idempotency_key", nullable = false, unique = true, length = 300)
	private String compensationIdempotencyKey;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PaymentStatus status;

	@Column(name = "pg_status", length = 50)
	private String pgStatus;

	@Column(name = "pg_method", length = 50)
	private String pgMethod;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Enumerated(EnumType.STRING)
	@Column(name = "recovery_action", nullable = false, length = 32)
	private PaymentRecoveryAction recoveryAction;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private Payment(
			Order order,
			String paymentKey,
			String approvalIdempotencyKey,
			String requestFingerprint,
			String compensationIdempotencyKey,
			long amount,
			LocalDateTime nextRetryAt) {
		this.order = order;
		this.paymentKey = paymentKey;
		this.approvalIdempotencyKey = approvalIdempotencyKey;
		this.requestFingerprint = requestFingerprint;
		this.compensationIdempotencyKey = compensationIdempotencyKey;
		this.amount = amount;
		this.status = PaymentStatus.PENDING;
		this.recoveryAction = PaymentRecoveryAction.VERIFY_APPROVAL;
		this.nextRetryAt = nextRetryAt;
	}

	public static Payment pending(
			Order order,
			String paymentKey,
			String approvalIdempotencyKey,
			String requestFingerprint,
			String compensationIdempotencyKey,
			long amount,
			LocalDateTime nextRetryAt) {
		return new Payment(
				order,
				paymentKey,
				approvalIdempotencyKey,
				requestFingerprint,
				compensationIdempotencyKey,
				amount,
				nextRetryAt
		);
	}

	public void complete(String pgStatus, String pgMethod, LocalDateTime approvedAt) {
		requireStatus(PaymentStatus.PENDING);
		this.status = PaymentStatus.SUCCESS;
		this.pgStatus = pgStatus;
		this.pgMethod = pgMethod;
		this.approvedAt = approvedAt;
		this.failureCode = null;
		this.failureMessage = null;
		clearRecovery();
		order.completePayment();
	}

	public void fail(String code, String message) {
		requireStatus(PaymentStatus.PENDING);
		this.status = PaymentStatus.FAILED;
		setFailure(code, message);
		clearRecovery();
		order.restoreCreated();
	}

	public void recordUncertainResult(String code, String message, LocalDateTime retryAt) {
		requireStatus(PaymentStatus.PENDING);
		setFailure(code, message);
		this.recoveryAction = PaymentRecoveryAction.VERIFY_APPROVAL;
		reschedule(retryAt);
	}

	public void requestCompensation(String code, String message, LocalDateTime retryAt) {
		requireStatus(PaymentStatus.PENDING);
		setFailure(code, message);
		this.recoveryAction = PaymentRecoveryAction.COMPENSATE_APPROVAL;
		reschedule(retryAt);
	}

	public void startCancellation() {
		requireStatus(PaymentStatus.SUCCESS);
		this.status = PaymentStatus.CANCEL_PENDING;
	}

	public void cancellationFailed() {
		requireStatus(PaymentStatus.CANCEL_PENDING);
		this.status = PaymentStatus.SUCCESS;
	}

	public void canceledByUser(String pgStatus, LocalDateTime canceledAt) {
		requireStatus(PaymentStatus.CANCEL_PENDING);
		this.status = PaymentStatus.CANCELED;
		this.pgStatus = pgStatus;
		this.canceledAt = canceledAt;
		order.cancelPaidOrder();
	}

	public void canceledByCompensation(String pgStatus, LocalDateTime canceledAt) {
		if (status != PaymentStatus.PENDING) {
			throw new NanacocoaException(INVALID_PAYMENT_STATUS);
		}
		this.status = PaymentStatus.CANCELED;
		this.pgStatus = pgStatus;
		this.canceledAt = canceledAt;
		clearRecovery();
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			order.restoreCreated();
		}
	}

	public void reschedule(LocalDateTime retryAt) {
		this.retryCount++;
		this.nextRetryAt = retryAt;
	}

	private void clearRecovery() {
		this.recoveryAction = PaymentRecoveryAction.NONE;
		this.nextRetryAt = null;
	}

	private void requireStatus(PaymentStatus expected) {
		if (status != expected) {
			throw new NanacocoaException(INVALID_PAYMENT_STATUS);
		}
	}

	private void setFailure(String code, String message) {
		this.failureCode = truncate(code, 100);
		this.failureMessage = truncate(message, 500);
	}

	private String truncate(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
