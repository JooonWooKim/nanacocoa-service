package com.nanacocoa.server.order.dto.response;

import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderItem;
import com.nanacocoa.server.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record OrderHistoryResponse(
		String orderId, LocalDateTime createdAt, OrderStatus status, long totalAmount,
		String recipientName, String phoneNumber, String shippingAddress, String customerRequest,
		List<OrderItemResponse> items, PaymentResult paymentResult) {

	public static OrderHistoryResponse from(Order order, PaymentResult paymentResult) {
		return new OrderHistoryResponse(order.getOrderId(), order.getCreatedAt(), order.getStatus(),
				order.getTotalAmount(), order.getRecipientName(), order.getPhoneNumber(),
				order.getShippingAddress(), order.getCustomerRequest(),
				order.getItems().stream().sorted(Comparator.comparing(OrderItem::getId))
						.map(OrderItemResponse::from).toList(),
				paymentResult);
	}

	public enum PaymentResult {
		UNPAID, PENDING, PAID, FAILED, CANCELLATION_PENDING, PAYMENT_CANCELED, ORDER_CANCELED
	}

	public record Summary(String orderId, LocalDateTime createdAt, String productName,
						  long totalAmount, OrderStatus status) {
		public static Summary from(Order order, String productName) {
			return new Summary(order.getOrderId(), order.getCreatedAt(), productName,
					order.getTotalAmount(), order.getStatus());
		}
	}

	public record OrderPage(List<Summary> orders, int page, boolean hasNext) {}
}
