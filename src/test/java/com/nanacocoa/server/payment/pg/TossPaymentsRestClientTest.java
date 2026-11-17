package com.nanacocoa.server.payment.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.payment.config.TossPaymentsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPaymentsRestClientTest {
	private static final String ORIGIN = "https://pg.invalid";
	private static final String BASIC_AUTH = "Basic Zml4dHVyZTo=";
	private MockRestServiceServer pg;
	private TossPaymentsRestClient client;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder().baseUrl(ORIGIN);
		pg = MockRestServiceServer.bindTo(builder).build();
		var properties = new TossPaymentsProperties();
		properties.setSecretKey("fixture");
		client = new TossPaymentsRestClient(builder.build(), properties, new ObjectMapper());
	}

	@Test
	void confirmsLooksUpAndCancelsThroughSimulatedPg() {
		String payment = """
				{"paymentKey":"fixture-payment","orderId":"fixture-order","totalAmount":38000,"status":"DONE"}
				""";
		pg.expect(requestTo(ORIGIN + "/v1/payments/confirm"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
				.andExpect(header("Idempotency-Key", "approval-fixture"))
				.andExpect(content().json("""
						{"paymentKey":"fixture-payment","orderId":"fixture-order","amount":38000}
						"""))
				.andRespond(withSuccess(payment, MediaType.APPLICATION_JSON));
		pg.expect(requestTo(ORIGIN + "/v1/payments/orders/fixture-order"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
				.andRespond(withSuccess(payment, MediaType.APPLICATION_JSON));
		pg.expect(requestTo(ORIGIN + "/v1/payments/fixture-payment/cancel"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
				.andExpect(header("Idempotency-Key", "cancel-fixture"))
				.andExpect(content().json("""
						{"cancelReason":"구매 취소"}
						"""))
				.andRespond(withSuccess("""
						{"paymentKey":"fixture-payment","orderId":"fixture-order","totalAmount":38000,
						 "status":"CANCELED","cancels":[{"transactionKey":"fixture-cancel"}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.confirm("fixture-payment", "fixture-order", 38000, "approval-fixture").isDone()).isTrue();
		assertThat(client.findByOrderId("fixture-order").getTotalAmount()).isEqualTo(38000L);
		assertThat(client.cancel("fixture-payment", "구매 취소", "cancel-fixture").lastCancellationTransactionKey())
				.isEqualTo("fixture-cancel");
		pg.verify();
	}

	@Test
	void classifiesTimeoutAndProviderErrorsWithoutNetwork() {
		pg.expect(requestTo(ORIGIN + "/v1/payments/confirm"))
				.andRespond(withException(new SocketTimeoutException("fixture timeout")));
		pg.expect(requestTo(ORIGIN + "/v1/payments/orders/fixture-order"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND)
						.body("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"fixture\"}")
						.contentType(MediaType.APPLICATION_JSON));
		pg.expect(requestTo(ORIGIN + "/v1/payments/fixture-payment/cancel"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.body("{\"code\":\"CANCEL_REJECTED\",\"message\":\"fixture\"}")
						.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.confirm("fixture-payment", "fixture-order", 38000, "approval-fixture"))
				.isInstanceOf(TossPaymentsException.class)
				.hasFieldOrPropertyWithValue("kind", TossPaymentsException.Kind.UNCERTAIN)
				.hasFieldOrPropertyWithValue("code", "PG_TIMEOUT");
		assertThatThrownBy(() -> client.findByOrderId("fixture-order"))
				.hasFieldOrPropertyWithValue("kind", TossPaymentsException.Kind.NOT_FOUND);
		assertThatThrownBy(() -> client.cancel("fixture-payment", "구매 취소", "cancel-fixture"))
				.hasFieldOrPropertyWithValue("kind", TossPaymentsException.Kind.DEFINITIVE)
				.hasFieldOrPropertyWithValue("code", "CANCEL_REJECTED");
		pg.verify();
	}
}
