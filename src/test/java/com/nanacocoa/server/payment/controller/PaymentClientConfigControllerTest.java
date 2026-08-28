package com.nanacocoa.server.payment.controller;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.payment.dto.response.PaymentClientConfigResponse;
import com.nanacocoa.server.payment.service.PaymentClientConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentClientConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PaymentClientConfigControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentClientConfigService paymentClientConfigService;

	@Test
	void returnsOnlyBrowserSafeClientConfig() throws Exception {
		when(paymentClientConfigService.getClientConfig())
				.thenReturn(new PaymentClientConfigResponse("test_ck_browser_safe"));

		mockMvc.perform(get("/api/payments/client-config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("결제설정조회성공"))
				.andExpect(jsonPath("$.data.clientKey").value("test_ck_browser_safe"))
				.andExpect(jsonPath("$.data.secretKey").doesNotExist());
	}

	@Test
	void returnsServiceUnavailableWhenClientKeyIsMissing() throws Exception {
		when(paymentClientConfigService.getClientConfig())
				.thenThrow(new NanacocoaException(PAYMENT_PROVIDER_UNAVAILABLE));

		mockMvc.perform(get("/api/payments/client-config"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("PAYMENT_11"));
	}
}
