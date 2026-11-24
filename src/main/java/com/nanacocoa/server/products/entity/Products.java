package com.nanacocoa.server.products.entity;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Products {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String name;

	@Column(nullable = false)
	private Long price;

	@Column(nullable = false, length = 500)
	private String summary;

	@Column(name = "detail_title", nullable = false, length = 255)
	private String detailTitle;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "stock_quantity", nullable = false)
	@Setter(AccessLevel.NONE)
	private long stockQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	@Setter(AccessLevel.NONE)
	private long reservedQuantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "sale_status", nullable = false, length = 32)
	@Setter(AccessLevel.NONE)
	private SaleStatus saleStatus = SaleStatus.STOPPED;

	@Column(name = "category_id")
	@Setter(AccessLevel.NONE)
	private Long categoryId;

	public void assignCategory(Long categoryId) {
		this.categoryId = categoryId;
	}

	@Version
	@Column(name = "stock_version", nullable = false)
	@Setter(AccessLevel.NONE)
	private long stockVersion;

	@Column(nullable = false)
	@Setter(AccessLevel.NONE)
	private long availabilityEpoch;

	public boolean isPurchasable() {
		return saleStatus == SaleStatus.ON_SALE && stockQuantity > 0;
	}

	public String getAvailability() {
		return saleStatus == SaleStatus.STOPPED ? "STOPPED" : stockQuantity == 0 ? "SOLD_OUT" : "ON_SALE";
	}

	public void updateInventory(long quantity, SaleStatus status, long expectedVersion) {
		if (quantity < 0 || quantity > 1_000_000_000L || status == null) throw new NanacocoaException(
			ErrorCode.INVALID_REQUEST);
		if (stockVersion != expectedVersion) throw new NanacocoaException(
			ErrorCode.STOCK_CONFLICT);
		boolean previouslyAvailable = isPurchasable();
		stockQuantity = quantity;
		saleStatus = status;
		recordAvailabilityChange(previouslyAvailable);
	}

	public void reserve(int quantity) {
		if (quantity <= 0 || saleStatus != SaleStatus.ON_SALE || stockQuantity < quantity)
			throw new NanacocoaException(
				ErrorCode.INSUFFICIENT_STOCK);
		stockQuantity -= quantity;
		recordAvailabilityChange(true);
		reservedQuantity = Math.addExact(reservedQuantity, quantity);
	}

	public void release(int quantity) {
		requireReserved(quantity);
		boolean previouslyAvailable = isPurchasable();
		reservedQuantity -= quantity;
		stockQuantity = Math.addExact(stockQuantity, quantity);
		recordAvailabilityChange(previouslyAvailable);
	}

	public void sellReserved(int quantity) {
		requireReserved(quantity);
		reservedQuantity -= quantity;
	}

	public void restock(int quantity) {
		if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
		boolean previouslyAvailable = isPurchasable();
		stockQuantity = Math.addExact(stockQuantity, quantity);
		recordAvailabilityChange(previouslyAvailable);
	}

	private void recordAvailabilityChange(boolean previouslyAvailable) {
		if (previouslyAvailable != isPurchasable()) availabilityEpoch++;
	}

	private void requireReserved(int quantity) {
		if (quantity <= 0 || reservedQuantity < quantity)
			throw new NanacocoaException(
				ErrorCode.STOCK_CONFLICT);
	}

	@Builder
	public Products(String name, Long price, String summary, String detailTitle, String description, String imageUrl) {
		this.name = name;
		this.price = price;
		this.summary = summary;
		this.detailTitle = detailTitle;
		this.description = description;
		this.imageUrl = imageUrl;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
