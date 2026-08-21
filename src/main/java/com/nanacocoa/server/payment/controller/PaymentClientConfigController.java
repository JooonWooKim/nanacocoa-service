package com.nanacocoa.server.payment.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.payment.dto.response.PaymentClientConfigResponse;
import com.nanacocoa.server.payment.service.PaymentClientConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/client-config")
@RequiredArgsConstructor
public class PaymentClientConfigController {
	private final PaymentClientConfigService paymentClientConfigService;

	@GetMapping
	public ResponseEntity<SuccessMessage<PaymentClientConfigResponse>> getClientConfig() {
		PaymentClientConfigResponse response = paymentClientConfigService.getClientConfig();
		return ResponseEntity.ok(new SuccessMessage<>("결제설정조회성공", response));
	}
}
