package com.nanacocoa.server.payment.pg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentResponse {
	private String paymentKey;
	private String orderId;
	private Long totalAmount;
	private String status;
	private String method;
	private OffsetDateTime approvedAt;
	private List<TossCancelResponse> cancels = new ArrayList<>();

	public boolean isDone() {
		return "DONE".equals(status);
	}

	public boolean isCanceled() {
		return "CANCELED".equals(status);
	}

	public LocalDateTime approvedAtLocal() {
		return approvedAt == null ? LocalDateTime.now() : approvedAt.toLocalDateTime();
	}

	public LocalDateTime canceledAtLocal() {
		TossCancelResponse cancel = lastCancel();
		return cancel == null || cancel.getCanceledAt() == null
				? LocalDateTime.now()
				: cancel.getCanceledAt().toLocalDateTime();
	}

	public String lastCancellationTransactionKey() {
		TossCancelResponse cancel = lastCancel();
		return cancel == null ? null : cancel.getTransactionKey();
	}

	private TossCancelResponse lastCancel() {
		return cancels == null || cancels.isEmpty() ? null : cancels.get(cancels.size() - 1);
	}
}
