package com.nanacocoa.server.payment.pg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossCancelResponse {
	private String transactionKey;
	private String cancelStatus;
	private OffsetDateTime canceledAt;
}
