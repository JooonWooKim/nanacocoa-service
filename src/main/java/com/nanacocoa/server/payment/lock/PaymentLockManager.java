package com.nanacocoa.server.payment.lock;

import com.nanacocoa.server.common.exception.NanacocoaException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_LOCK_INTERRUPTED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_LOCK_UNAVAILABLE;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_IN_PROGRESS;

@Component
@Profile("!test")
public class PaymentLockManager implements OrderPaymentLock {
	private static final Logger log = LoggerFactory.getLogger(PaymentLockManager.class);
	private static final String LOCK_PREFIX = "payment:order:";

	private final RedissonClient redissonClient;
	private final Duration waitTime;

	public PaymentLockManager(
			RedissonClient redissonClient,
			@Value("${payment.lock.wait-time:2s}") Duration waitTime) {
		this.redissonClient = redissonClient;
		this.waitTime = waitTime;
	}

	@Override
	public <T> T withOrderLock(String orderId, Supplier<T> action) {
		RLock lock;
		boolean acquired;
		try {
			lock = redissonClient.getLock(LOCK_PREFIX + orderId);
			acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new NanacocoaException(PAYMENT_LOCK_INTERRUPTED);
		} catch (RuntimeException exception) {
			log.error("Redis payment lock acquisition failed. orderId={}", orderId, exception);
			throw new NanacocoaException(PAYMENT_LOCK_UNAVAILABLE);
		}

		if (!acquired) {
			throw new NanacocoaException(PAYMENT_IN_PROGRESS);
		}

		try {
			return action.get();
		} finally {
			try {
				if (lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
			} catch (RuntimeException exception) {
				log.error("Redis payment lock release failed. orderId={}", orderId, exception);
			}
		}
	}
}
