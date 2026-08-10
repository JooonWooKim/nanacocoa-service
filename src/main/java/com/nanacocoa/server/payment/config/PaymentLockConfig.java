package com.nanacocoa.server.payment.config;

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentLockConfig {
	@Bean
	RedissonAutoConfigurationCustomizer paymentLockRedissonCustomizer() {
		return config -> config.setLazyInitialization(true);
	}
}
