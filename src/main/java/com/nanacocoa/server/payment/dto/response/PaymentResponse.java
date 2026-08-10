package com.nanacocoa.server.payment.dto.response;

import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PaymentResponse {
	private Long paymentId;
	private String orderId;
	private Long amount;
	private PaymentStatus status;
	private String paymentMethod;
	private String failureCode;
	private LocalDateTime approvedAt;
	private LocalDateTime canceledAt;

	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(
				payment.getId(),
				payment.getOrder().getOrderId(),
				payment.getAmount(),
				payment.getStatus(),
				payment.getPgMethod(),
				payment.getFailureCode(),
				payment.getApprovedAt(),
				payment.getCanceledAt()
		);
	}

	public boolean isPending() {
		return status == PaymentStatus.PENDING || status == PaymentStatus.CANCEL_PENDING;
	}
}
