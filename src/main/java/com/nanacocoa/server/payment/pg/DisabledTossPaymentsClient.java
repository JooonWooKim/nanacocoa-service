package com.nanacocoa.server.payment.pg;

public final class DisabledTossPaymentsClient implements TossPaymentsClient {
	@Override
	public boolean isDisabled() {
		return true;
	}

	@Override
	public TossPaymentResponse confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
		throw unavailable();
	}

	@Override
	public TossPaymentResponse findByOrderId(String orderId) {
		throw unavailable();
	}

	@Override
	public TossPaymentResponse cancel(String paymentKey, String reason, String idempotencyKey) {
		throw unavailable();
	}

	private TossPaymentsException unavailable() {
		return new TossPaymentsException(TossPaymentsException.Kind.UNAVAILABLE, "PG_DISABLED",
				"결제 연동이 비활성화되었습니다.");
	}
}
