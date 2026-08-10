package com.nanacocoa.server.payment.controller;

import com.nanacocoa.server.payment.dto.response.PaymentResponse;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.facade.PaymentFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PaymentControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentFacade paymentFacade;

	@Test
	void returnsAcceptedWhileApprovalIsPending() throws Exception {
		PaymentResponse pending = new PaymentResponse(
				10L, "NC_order-123", 38000L, PaymentStatus.PENDING, null, "PG_TIMEOUT", null, null
		);
		when(paymentFacade.confirm(any(), eq("idem-key"), isNull())).thenReturn(pending);

		mockMvc.perform(post("/api/payments/confirm")
					.header("Idempotency-Key", "idem-key")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "paymentKey": "payment-key",
							  "orderId": "NC_order-123",
							  "amount": 38000
							}
							"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.message").value("결제승인확인중"))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	void returnsOkAfterFullCancellation() throws Exception {
		PaymentResponse canceled = new PaymentResponse(
				10L,
				"NC_order-123",
				38000L,
				PaymentStatus.CANCELED,
				"카드",
				null,
				LocalDateTime.now(),
				LocalDateTime.now()
		);
		when(paymentFacade.cancel(eq(10L), any(), eq("cancel-idem"), isNull())).thenReturn(canceled);

		mockMvc.perform(post("/api/payments/10/cancel")
					.header("Idempotency-Key", "cancel-idem")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "cancelReason": "고객 요청"
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("결제취소성공"))
				.andExpect(jsonPath("$.data.status").value("CANCELED"));
	}
}
