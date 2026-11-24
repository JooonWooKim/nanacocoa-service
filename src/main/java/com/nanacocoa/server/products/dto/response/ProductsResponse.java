package com.nanacocoa.server.products.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
	private String availability;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Boolean restockNotificationEnabled;

	public static ProductsResponse from(Products products) {
		return new ProductsResponse(
				products.getId(),
				products.getName(),
				products.getPrice(),
				products.getSummary(),
				products.getDetailTitle(),
				products.getDescription(),
				products.getImageUrl(), products.getAvailability(), null
		);
	}

	public static ProductsResponse detail(Products product, boolean restockEnabled) {
		var result = from(product);
		if (restockEnabled) result.restockNotificationEnabled = true;
		return result;
	}
}
