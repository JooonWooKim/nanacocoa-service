package com.nanacocoa.server.order.dto.response;

import com.nanacocoa.server.order.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderItemResponse {
	private Long productId;
	private String productName;
	private Long unitPrice;
	private Integer quantity;
	private Long lineAmount;

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(
				item.getProduct().getId(),
				item.getProductName(),
				item.getUnitPrice(),
				item.getQuantity(),
				item.getLineAmount()
		);
	}
}
