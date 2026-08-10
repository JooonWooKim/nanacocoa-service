package com.nanacocoa.server.order.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.order.dto.reqeust.CreateOrderRequest;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
	private final OrderService orderService;

	@PostMapping
	public ResponseEntity<SuccessMessage<OrderResponse>> createOrder(
			@Valid @RequestBody CreateOrderRequest request,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		OrderResponse response = orderService.createOrder(request, userDetails);
		return new ResponseEntity<>(new SuccessMessage<>("주문생성성공", response), HttpStatus.CREATED);
	}
}
