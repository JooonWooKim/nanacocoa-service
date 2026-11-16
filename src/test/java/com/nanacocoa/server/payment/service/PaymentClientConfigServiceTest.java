package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.payment.config.TossPaymentsProperties;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import org.junit.jupiter.api.Test;

import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentClientConfigServiceTest {
	@Test
	void returnsConfiguredClientKey() {
		TossPaymentsProperties properties = new TossPaymentsProperties();
		properties.setClientKey("test_ck_configured");
		PaymentClientConfigService service = new PaymentClientConfigService(properties, mock(TossPaymentsClient.class));

		assertThat(service.getClientConfig().getClientKey()).isEqualTo("test_ck_configured");
	}

	@Test
	void rejectsMissingClientKey() {
		PaymentClientConfigService service = new PaymentClientConfigService(
				new TossPaymentsProperties(), mock(TossPaymentsClient.class));

		assertThatThrownBy(service::getClientConfig)
				.isInstanceOf(NanacocoaException.class)
				.hasFieldOrPropertyWithValue("errorCode", PAYMENT_PROVIDER_UNAVAILABLE);
	}
}
