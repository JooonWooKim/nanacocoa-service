package com.nanacocoa.server.payment.config;

import com.nanacocoa.server.payment.lock.OrderPaymentLock;
import com.nanacocoa.server.payment.lock.PaymentLockManager;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("redis-unavailable")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:redis-unavailable-startup;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"spring.sql.init.mode=never",
		"spring.data.redis.host=127.0.0.1",
		"spring.data.redis.port=1",
		"spring.data.redis.connect-timeout=100ms",
		"spring.data.redis.timeout=100ms",
		"payment.reconciliation.enabled=false"
})
class PaymentLockConfigTest {
	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private OrderPaymentLock orderPaymentLock;

	@Test
	void applicationStartsWithLazyRedissonWhenRedisIsUnavailable() {
		assertThat(redissonClient.getConfig().isLazyInitialization()).isTrue();
		assertThat(orderPaymentLock).isInstanceOf(PaymentLockManager.class);
	}
}
