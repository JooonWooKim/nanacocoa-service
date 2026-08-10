package com.nanacocoa.server.products.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductImageFormat {
	JPEG("jpg", "image/jpeg"),
	PNG("png", "image/png"),
	WEBP("webp", "image/webp");

	private final String extension;
	private final String contentType;
}
