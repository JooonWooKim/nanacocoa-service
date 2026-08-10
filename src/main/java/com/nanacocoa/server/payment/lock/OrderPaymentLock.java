package com.nanacocoa.server.payment.lock;

import java.util.function.Supplier;

public interface OrderPaymentLock {
	<T> T withOrderLock(String orderId, Supplier<T> action);
}
