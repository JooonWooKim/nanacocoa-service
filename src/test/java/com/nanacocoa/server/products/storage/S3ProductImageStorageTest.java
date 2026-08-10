package com.nanacocoa.server.products.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ProductImageStorageTest {
	@Mock
	private S3Client s3Client;

	@Test
	void uploadsWithGeneratedKeyAndReturnsCdnUrl() {
		ProductImageProperties properties = new ProductImageProperties();
		properties.setBucket("nanacocoa-products");
		properties.setPublicBaseUrl("https://cdn.example.com/");
		S3ProductImageStorage storage = new S3ProductImageStorage(s3Client, properties);
		MockMultipartFile image = new MockMultipartFile(
				"image", "unsafe-name.png", "application/octet-stream", new byte[]{1, 2, 3, 4});
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().build());

		StoredProductImage storedImage = storage.upload(image, ProductImageFormat.PNG);

		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
		PutObjectRequest request = requestCaptor.getValue();
		assertThat(request.bucket()).isEqualTo("nanacocoa-products");
		assertThat(request.key()).matches("products/\\d{4}/\\d{2}/[0-9a-f-]+\\.png");
		assertThat(request.contentType()).isEqualTo("image/png");
		assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
		assertThat(storedImage.objectKey()).isEqualTo(request.key());
		assertThat(storedImage.publicUrl()).isEqualTo("https://cdn.example.com/" + request.key());
	}
}
