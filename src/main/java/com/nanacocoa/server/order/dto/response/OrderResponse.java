package com.nanacocoa.server.order.dto.response;

import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderResponse {
	private String orderId;
	private String recipientName;
	private String phoneNumber;
	private String shippingAddress;
	private String customerRequest;
	private Long totalAmount;
	private OrderStatus status;
	private List<OrderItemResponse> items;
	private LocalDateTime createdAt;

	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getOrderId(),
				order.getRecipientName(),
				order.getPhoneNumber(),
				order.getShippingAddress(),
				order.getCustomerRequest(),
				order.getTotalAmount(),
				order.getStatus(),
				order.getItems().stream().map(OrderItemResponse::from).toList(),
				order.getCreatedAt()
		);
	}
}
