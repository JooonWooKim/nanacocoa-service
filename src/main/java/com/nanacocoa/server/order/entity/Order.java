package com.nanacocoa.server.order.entity;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.products.entity.Products;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_PAYMENT_STATUS;
import static com.nanacocoa.server.common.exception.ErrorCode.ORDER_AMOUNT_OVERFLOW;

@Entity(name = "PurchaseOrder")
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false, unique = true, length = 64)
	private String orderId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "recipient_name", nullable = false, length = 100)
	private String recipientName;

	@Column(name = "phone_number", nullable = false, length = 30)
	private String phoneNumber;

	@Column(name = "shipping_address", nullable = false, length = 500)
	private String shippingAddress;

	@Column(name = "customer_request", length = 500)
	private String customerRequest;

	@Column(name = "total_amount", nullable = false)
	private Long totalAmount = 0L;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OrderStatus status = OrderStatus.CREATED;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	private Order(
			String orderId,
			Member member,
			String recipientName,
			String phoneNumber,
			String shippingAddress,
			String customerRequest) {
		this.orderId = orderId;
		this.member = member;
		this.recipientName = recipientName;
		this.phoneNumber = phoneNumber;
		this.shippingAddress = shippingAddress;
		this.customerRequest = customerRequest;
	}

	public static Order create(
			String orderId,
			Member member,
			String recipientName,
			String phoneNumber,
			String shippingAddress,
			String customerRequest) {
		return new Order(orderId, member, recipientName, phoneNumber, shippingAddress, customerRequest);
	}

	public void addItem(Products product, int quantity) {
		try {
			long lineAmount = Math.multiplyExact(product.getPrice(), quantity);
			totalAmount = Math.addExact(totalAmount, lineAmount);
			items.add(OrderItem.create(this, product, quantity, lineAmount));
		} catch (ArithmeticException exception) {
			throw new NanacocoaException(ORDER_AMOUNT_OVERFLOW);
		}
	}

	public List<OrderItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	public boolean isOwnedBy(String email) {
		return member.getEmail().equals(email);
	}

	public void startPayment() {
		changeStatus(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);
	}

	public void completePayment() {
		changeStatus(OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
	}

	public void restoreCreated() {
		changeStatus(OrderStatus.PAYMENT_PENDING, OrderStatus.CREATED);
	}

	public void cancelPaidOrder() {
		changeStatus(OrderStatus.PAID, OrderStatus.CANCELED);
	}

	private void changeStatus(OrderStatus expected, OrderStatus next) {
		if (status != expected) {
			throw new NanacocoaException(INVALID_PAYMENT_STATUS);
		}
		status = next;
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
