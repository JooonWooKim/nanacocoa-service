package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PaymentFingerprintService {
	public String approval(String email, ConfirmPaymentRequest request) {
		return hash(canonical("APPROVAL", email, request.getOrderId(), request.getPaymentKey(), request.getAmount().toString()));
	}

	public String cancellation(String email, Long paymentId, String reason) {
		return hash(canonical("CANCEL", email, paymentId.toString(), reason));
	}

	private String canonical(String... values) {
		StringBuilder builder = new StringBuilder();
		for (String value : values) {
			builder.append(value.length()).append(':').append(value).append('|');
		}
		return builder.toString();
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
