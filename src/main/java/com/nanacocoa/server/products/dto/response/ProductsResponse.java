package com.nanacocoa.server.products.dto.response;

import com.nanacocoa.server.products.entity.Products;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductsResponse {
	private Long id;
	private String name;
	private Long price;
	private String summary;
	private String detailTitle;
	private String description;
	private String imageUrl;

	public static ProductsResponse from(Products products) {
		return new ProductsResponse(
				products.getId(),
				products.getName(),
				products.getPrice(),
				products.getSummary(),
				products.getDetailTitle(),
				products.getDescription(),
				products.getImageUrl()
		);
	}
}
