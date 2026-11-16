package com.nanacocoa.server.payment.pg;

public interface TossPaymentsClient {
	boolean isDisabled();

	TossPaymentResponse confirm(String paymentKey, String orderId, long amount, String idempotencyKey);

	TossPaymentResponse findByOrderId(String orderId);

	TossPaymentResponse cancel(String paymentKey, String reason, String idempotencyKey);
}
