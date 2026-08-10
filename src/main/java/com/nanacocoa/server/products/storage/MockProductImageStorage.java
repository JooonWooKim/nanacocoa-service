package com.nanacocoa.server.products.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(prefix = "product-image", name = "storage-mode", havingValue = "mock", matchIfMissing = true)
public class MockProductImageStorage implements ProductImageStorage {
	private static final String MOCK_OBJECT_KEY = "mock/products/default";
	private static final String MOCK_PUBLIC_URL = "IMG_2398.JPG";

	@Override
	public StoredProductImage upload(MultipartFile image, ProductImageFormat format) {
		return new StoredProductImage(MOCK_OBJECT_KEY, MOCK_PUBLIC_URL);
	}

	@Override
	public void delete(String objectKey) {
		// 목업 모드는 실제 파일을 저장하지 않습니다.
	}
}
