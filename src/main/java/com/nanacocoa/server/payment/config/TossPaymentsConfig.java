package com.nanacocoa.server.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.payment.pg.DisabledTossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsRestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.regex.Pattern;

@Configuration
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsConfig {
	@Bean
	TossPaymentsClient tossPaymentsClient(
			TossPaymentsProperties properties, Environment environment, ObjectMapper objectMapper) {
		if (!properties.isEnabled()) {
			return new DisabledTossPaymentsClient();
		}
		validateLiveSettings(properties, environment);

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.getConnectTimeout())
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());

		RestClient restClient = RestClient.builder()
				.baseUrl(TossPaymentsProperties.LIVE_BASE_URL)
				.requestFactory(requestFactory)
				.build();
		return new TossPaymentsRestClient(restClient, properties, objectMapper);
	}

	private void validateLiveSettings(TossPaymentsProperties properties, Environment environment) {
		var profiles = Arrays.asList(environment.getActiveProfiles());
		if (!profiles.contains("prod") || profiles.contains("test")) {
			throw new IllegalStateException("TOSS_PAYMENTS_ENABLED requires the active prod profile without the test profile.");
		}
		if (!TossPaymentsProperties.LIVE_BASE_URL.equals(properties.getBaseUrl())) {
			throw new IllegalStateException("TOSS_PAYMENTS_BASE_URL must be the official HTTPS Toss Payments API origin.");
		}
		if (!hasKey(properties.getClientKey(), "live_ck_") || !hasKey(properties.getSecretKey(), "live_sk_")) {
			throw new IllegalStateException("Toss Payments requires a live client key and secret key for the payment window.");
		}
	}

	private boolean hasKey(String key, String prefix) {
		return key != null && Pattern.matches(prefix + "[A-Za-z0-9]+", key);
	}
}
