package com.nanacocoa.server.products.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ProductImageStorage {
	StoredProductImage upload(MultipartFile image, ProductImageFormat format);

	void delete(String objectKey);
}
