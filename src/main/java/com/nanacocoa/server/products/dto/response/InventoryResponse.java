package com.nanacocoa.server.products.dto.response;

import com.nanacocoa.server.products.entity.Products;

/** 관리자 전용 응답이며 공개 상품 조회에는 포함하지 않습니다. */
public record InventoryResponse(Long id, String name, long stockQuantity,
		long reservedQuantity, long stockVersion, String availability) {
	public static InventoryResponse from(Products product) {
		return new InventoryResponse(product.getId(), product.getName(), product.getStockQuantity(),
			product.getReservedQuantity(), product.getStockVersion(), product.getAvailability());
	}
}
