package com.nanacocoa.server.order.entity;

import com.nanacocoa.server.products.entity.Products;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Products product;

	@Column(name = "product_name", nullable = false, length = 255)
	private String productName;

	@Column(name = "unit_price", nullable = false)
	private Long unitPrice;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "line_amount", nullable = false)
	private Long lineAmount;

	private OrderItem(Order order, Products product, int quantity, long lineAmount) {
		this.order = order;
		this.product = product;
		this.productName = product.getName();
		this.unitPrice = product.getPrice();
		this.quantity = quantity;
		this.lineAmount = lineAmount;
	}

	static OrderItem create(Order order, Products product, int quantity, long lineAmount) {
		return new OrderItem(order, product, quantity, lineAmount);
	}
}
