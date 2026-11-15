package com.nanacocoa.server.order.controller;

import org.springframework.web.bind.annotation.PathVariable;
import com.nanacocoa.server.order.dto.reqeust.RestockOrderRequest;
import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.order.dto.reqeust.CreateOrderRequest;
import com.nanacocoa.server.order.dto.response.OrderHistoryResponse;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
	private final OrderService orderService;

	@GetMapping
	public ResponseEntity<SuccessMessage<OrderHistoryResponse.OrderPage>> listOrders(
			@RequestParam(required = false) String page, @AuthenticationPrincipal UserDetailsImpl userDetails) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(new SuccessMessage<>("주문목록조회성공", orderService.listOrders(page, userDetails)));
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<SuccessMessage<OrderHistoryResponse>> getOrder(
			@PathVariable String orderId, @AuthenticationPrincipal UserDetailsImpl userDetails) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(new SuccessMessage<>("주문상세조회성공", orderService.getOrder(orderId, userDetails)));
	}

	@PostMapping("/{orderId}/reservation")
	public ResponseEntity<SuccessMessage<OrderResponse>> reserveForRetry(
			@PathVariable String orderId, @AuthenticationPrincipal UserDetailsImpl userDetails) {
		return ResponseEntity.ok(new SuccessMessage<>("주문재고확인성공", orderService.reserveForRetry(orderId, userDetails)));
	}

	@PostMapping("/{orderId}/cancel")
	public ResponseEntity<SuccessMessage<OrderResponse>> cancelUnpaid(
			@PathVariable String orderId,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return ResponseEntity.ok(new SuccessMessage<>("미결제주문취소성공", orderService.cancelUnpaid(orderId, userDetails)));
	}

	@PostMapping("/{orderId}/restock")
	public ResponseEntity<SuccessMessage<OrderResponse>> restock(
			@PathVariable String orderId,
			@Valid @RequestBody RestockOrderRequest request,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return ResponseEntity.ok(new SuccessMessage<>("검수후재입고성공", orderService.restock(orderId, request.reason(), userDetails)));
	}

	@PostMapping
	public ResponseEntity<SuccessMessage<OrderResponse>> createOrder(
			@Valid @RequestBody CreateOrderRequest request,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		OrderResponse response = orderService.createOrder(request, userDetails);
		return new ResponseEntity<>(new SuccessMessage<>("주문생성성공", response), HttpStatus.CREATED);
	}
}
