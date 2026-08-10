package com.nanacocoa.server.payment.lock;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@Profile("test")
public class LocalPaymentLock implements OrderPaymentLock {
	@Override
	public <T> T withOrderLock(String orderId, Supplier<T> action) {
		return action.get();
	}
}
