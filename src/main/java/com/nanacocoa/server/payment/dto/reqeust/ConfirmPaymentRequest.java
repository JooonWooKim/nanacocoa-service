package com.nanacocoa.server.payment.dto.reqeust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {
	@NotBlank
	@Size(max = 200)
	private String paymentKey;

	@NotBlank
	@Size(min = 6, max = 64)
	private String orderId;

	@NotNull
	@Positive
	private Long amount;
}
