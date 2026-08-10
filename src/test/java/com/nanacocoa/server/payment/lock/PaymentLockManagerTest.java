package com.nanacocoa.server.payment.lock;

import com.nanacocoa.server.common.exception.NanacocoaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_IN_PROGRESS;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_LOCK_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLockManagerTest {
	@Mock
	private RedissonClient redissonClient;
	@Mock
	private RLock lock;

	private PaymentLockManager lockManager;

	@BeforeEach
	void setUp() {
		lockManager = new PaymentLockManager(redissonClient, Duration.ofSeconds(2));
	}

	@Test
	void executesActionAndReleasesOwnedLock() throws Exception {
		when(redissonClient.getLock("payment:order:NC_order-123")).thenReturn(lock);
		when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(true);
		when(lock.isHeldByCurrentThread()).thenReturn(true);

		String result = lockManager.withOrderLock("NC_order-123", () -> "done");

		assertThat(result).isEqualTo("done");
		verify(lock).unlock();
	}

	@Test
	void rejectsConcurrentRequestWhenWaitTimeExpires() throws Exception {
		when(redissonClient.getLock("payment:order:NC_order-123")).thenReturn(lock);
		when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(false);

		assertThatThrownBy(() -> lockManager.withOrderLock("NC_order-123", () -> "done"))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(PAYMENT_IN_PROGRESS));
	}

	@Test
	void failsClosedWhenRedisIsUnavailable() {
		when(redissonClient.getLock("payment:order:NC_order-123"))
				.thenThrow(new IllegalStateException("redis unavailable"));
		AtomicBoolean actionExecuted = new AtomicBoolean(false);

		assertThatThrownBy(() -> lockManager.withOrderLock("NC_order-123", () -> {
			actionExecuted.set(true);
			return "done";
		}))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(PAYMENT_LOCK_UNAVAILABLE));
		assertThat(actionExecuted).isFalse();
	}
}
