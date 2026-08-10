package com.nanacocoa.server.products.dto.reqeust;

import com.nanacocoa.server.products.entity.Products;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterProductsRequest {
	@NotBlank
	private String name;

	@NotNull
	@Positive
	private Long price;

	@NotBlank
	private String summary;

	@NotBlank
	private String detailTitle;

	@NotBlank
	private String description;

	private String imageUrl;

	public Products toProducts() {
		return toProducts(imageUrl);
	}

	public Products toProducts(String storedImageUrl) {
		return Products.builder()
				.name(name)
				.price(price)
				.summary(summary)
				.detailTitle(detailTitle)
				.description(description)
				.imageUrl(storedImageUrl)
				.build();
	}
}
