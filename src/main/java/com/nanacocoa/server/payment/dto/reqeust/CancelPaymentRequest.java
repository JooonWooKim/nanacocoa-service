package com.nanacocoa.server.payment.dto.reqeust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentRequest {
	@NotBlank
	@Size(max = 200)
	private String cancelReason;
}
