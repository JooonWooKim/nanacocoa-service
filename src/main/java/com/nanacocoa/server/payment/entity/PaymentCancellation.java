package com.nanacocoa.server.payment.entity;

import com.nanacocoa.server.common.exception.NanacocoaException;
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
@Table(name = "payment_cancellations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 300)
	private String idempotencyKey;

	@Column(name = "request_fingerprint", nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String requestFingerprint;

	@Column(nullable = false, length = 200)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CancellationStatus status;

	@Column(name = "pg_transaction_key", unique = true, length = 64)
	private String pgTransactionKey;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private PaymentCancellation(
			Payment payment,
			String idempotencyKey,
			String requestFingerprint,
			String reason,
			LocalDateTime nextRetryAt) {
		this.payment = payment;
		this.idempotencyKey = idempotencyKey;
		this.requestFingerprint = requestFingerprint;
		this.reason = reason;
		this.status = CancellationStatus.PENDING;
		this.nextRetryAt = nextRetryAt;
	}

	public static PaymentCancellation pending(
			Payment payment,
			String idempotencyKey,
			String requestFingerprint,
			String reason,
			LocalDateTime nextRetryAt) {
		return new PaymentCancellation(payment, idempotencyKey, requestFingerprint, reason, nextRetryAt);
	}

	public void complete(String transactionKey) {
		requirePending();
		status = CancellationStatus.SUCCESS;
		pgTransactionKey = transactionKey;
		failureCode = null;
		failureMessage = null;
		nextRetryAt = null;
	}

	public void fail(String code, String message) {
		requirePending();
		status = CancellationStatus.FAILED;
		setFailure(code, message);
		nextRetryAt = null;
	}

	public void recordUncertainResult(String code, String message, LocalDateTime retryAt) {
		requirePending();
		setFailure(code, message);
		retryCount++;
		nextRetryAt = retryAt;
	}

	private void requirePending() {
		if (status != CancellationStatus.PENDING) {
			throw new NanacocoaException(INVALID_PAYMENT_STATUS);
		}
	}

	private void setFailure(String code, String message) {
		failureCode = truncate(code, 100);
		failureMessage = truncate(message, 500);
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
