package com.nanacocoa.server.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "toss-payments")
public class TossPaymentsProperties {
	static final String LIVE_BASE_URL = "https://api.tosspayments.com";

	private boolean enabled;
	private String baseUrl = LIVE_BASE_URL;
	private String clientKey = "";
	private String secretKey = "";
	private Duration connectTimeout = Duration.ofSeconds(2);
	private Duration readTimeout = Duration.ofSeconds(10);
}
