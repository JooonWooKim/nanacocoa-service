package com.nanacocoa.server.payment.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.payment.config.TossPaymentsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class TossPaymentsRestClient implements TossPaymentsClient {
	private final RestClient tossPaymentsRestClient;
	private final TossPaymentsProperties properties;
	private final ObjectMapper objectMapper;

	@Override
	public TossPaymentResponse confirm(
			String paymentKey,
			String orderId,
			long amount,
			String idempotencyKey) {
		return execute(false, () -> tossPaymentsRestClient.post()
				.uri("/v1/payments/confirm")
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new ConfirmRequest(paymentKey, orderId, amount))
				.retrieve()
				.body(TossPaymentResponse.class));
	}

	@Override
	public TossPaymentResponse findByOrderId(String orderId) {
		return execute(true, () -> tossPaymentsRestClient.get()
				.uri("/v1/payments/orders/{orderId}", orderId)
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.retrieve()
				.body(TossPaymentResponse.class));
	}

	@Override
	public TossPaymentResponse cancel(String paymentKey, String reason, String idempotencyKey) {
		return execute(false, () -> tossPaymentsRestClient.post()
				.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CancelRequest(reason))
				.retrieve()
				.body(TossPaymentResponse.class));
	}

	private TossPaymentResponse execute(boolean notFoundIsExpected, ProviderCall call) {
		if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
			throw new TossPaymentsException(
					TossPaymentsException.Kind.UNAVAILABLE,
					"MISSING_SECRET_KEY",
					"토스페이먼츠 시크릿 키가 설정되지 않았습니다."
			);
		}

		try {
			TossPaymentResponse response = call.execute();
			if (response == null) {
				throw new TossPaymentsException(
						TossPaymentsException.Kind.UNCERTAIN,
						"EMPTY_PG_RESPONSE",
						"토스페이먼츠 응답 본문이 비어 있습니다."
				);
			}
			return response;
		} catch (TossPaymentsException exception) {
			throw exception;
		} catch (ResourceAccessException exception) {
			throw new TossPaymentsException(
					TossPaymentsException.Kind.UNCERTAIN,
					"PG_TIMEOUT",
					"토스페이먼츠 응답을 확인할 수 없습니다.",
					exception
			);
		} catch (RestClientResponseException exception) {
			TossErrorResponse error = parseError(exception);
			int status = exception.getStatusCode().value();
			if (status == 404 && notFoundIsExpected) {
				throw new TossPaymentsException(TossPaymentsException.Kind.NOT_FOUND, error.code(), error.message(), exception);
			}
			if (status == 409 || status >= 500) {
				throw new TossPaymentsException(TossPaymentsException.Kind.UNCERTAIN, error.code(), error.message(), exception);
			}
			if (status == 401 || status == 403) {
				throw new TossPaymentsException(TossPaymentsException.Kind.UNAVAILABLE, error.code(), error.message(), exception);
			}
			throw new TossPaymentsException(TossPaymentsException.Kind.DEFINITIVE, error.code(), error.message(), exception);
		}
	}

	private String authorization() {
		String credential = properties.getSecretKey() + ":";
		return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
	}

	private TossErrorResponse parseError(RestClientResponseException exception) {
		try {
			return objectMapper.readValue(exception.getResponseBodyAsByteArray(), TossErrorResponse.class);
		} catch (Exception ignored) {
			return new TossErrorResponse("PG_HTTP_" + exception.getStatusCode().value(), "결제사 요청에 실패했습니다.");
		}
	}

	@FunctionalInterface
	private interface ProviderCall {
		TossPaymentResponse execute();
	}

	private record ConfirmRequest(String paymentKey, String orderId, long amount) {
	}

	private record CancelRequest(String cancelReason) {
	}

	private record TossErrorResponse(String code, String message) {
	}
}
