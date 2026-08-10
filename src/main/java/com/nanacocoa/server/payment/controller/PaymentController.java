package com.nanacocoa.server.payment.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.payment.dto.reqeust.CancelPaymentRequest;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.dto.response.PaymentResponse;
import com.nanacocoa.server.payment.facade.PaymentFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
	private final PaymentFacade paymentFacade;

	@PostMapping("/confirm")
	public ResponseEntity<SuccessMessage<PaymentResponse>> confirm(
			@Valid @RequestBody ConfirmPaymentRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		PaymentResponse response = paymentFacade.confirm(request, idempotencyKey, userDetails);
		return response(response, "결제승인성공", "결제승인확인중");
	}

	@GetMapping("/{paymentId}")
	public ResponseEntity<SuccessMessage<PaymentResponse>> getPayment(
			@PathVariable Long paymentId,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		PaymentResponse response = paymentFacade.getPayment(paymentId, userDetails);
		return ResponseEntity.ok(new SuccessMessage<>("결제조회성공", response));
	}

	@PostMapping("/{paymentId}/cancel")
	public ResponseEntity<SuccessMessage<PaymentResponse>> cancel(
			@PathVariable Long paymentId,
			@Valid @RequestBody CancelPaymentRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		PaymentResponse response = paymentFacade.cancel(paymentId, request, idempotencyKey, userDetails);
		return response(response, "결제취소성공", "결제취소확인중");
	}

	private ResponseEntity<SuccessMessage<PaymentResponse>> response(
			PaymentResponse response,
			String successMessage,
			String pendingMessage) {
		HttpStatus status = response.isPending() ? HttpStatus.ACCEPTED : HttpStatus.OK;
		String message = response.isPending() ? pendingMessage : successMessage;
		return new ResponseEntity<>(new SuccessMessage<>(message, response), status);
	}
}
