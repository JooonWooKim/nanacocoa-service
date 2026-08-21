package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.payment.config.TossPaymentsProperties;
import com.nanacocoa.server.payment.dto.response.PaymentClientConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class PaymentClientConfigService {
	private final TossPaymentsProperties properties;

	public PaymentClientConfigResponse getClientConfig() {
		String clientKey = properties.getClientKey();
		if (clientKey == null || clientKey.isBlank()) {
			throw new NanacocoaException(PAYMENT_PROVIDER_UNAVAILABLE);
		}
		return new PaymentClientConfigResponse(clientKey);
	}
}
