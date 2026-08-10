package com.nanacocoa.server.products.storage;

import com.nanacocoa.server.common.exception.NanacocoaException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

import static com.nanacocoa.server.common.exception.ErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product-image", name = "storage-mode", havingValue = "s3")
public class S3ProductImageStorage implements ProductImageStorage {
	private static final Logger logger = LoggerFactory.getLogger(S3ProductImageStorage.class);
	private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

	private final S3Client productImageS3Client;
	private final ProductImageProperties properties;

	@Override
	public StoredProductImage upload(MultipartFile image, ProductImageFormat format) {
		String objectKey = createObjectKey(format);
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(properties.getBucket())
				.key(objectKey)
				.contentType(format.getContentType())
				.cacheControl(CACHE_CONTROL)
				.build();

		try (InputStream inputStream = image.getInputStream()) {
			productImageS3Client.putObject(request, RequestBody.fromInputStream(inputStream, image.getSize()));
			return new StoredProductImage(objectKey, createPublicUrl(objectKey));
		} catch (IOException | S3Exception | SdkClientException e) {
			throw new NanacocoaException(PRODUCT_IMAGE_UPLOAD_FAILED);
		}
	}

	@Override
	public void delete(String objectKey) {
		try {
			productImageS3Client.deleteObject(DeleteObjectRequest.builder()
					.bucket(properties.getBucket())
					.key(objectKey)
					.build());
		} catch (RuntimeException e) {
			logger.error("S3 상품 이미지 삭제 보상에 실패했습니다. objectKey={}", objectKey, e);
		}
	}

	private String createObjectKey(ProductImageFormat format) {
		LocalDate today = LocalDate.now();
		return "products/%d/%02d/%s.%s".formatted(
				today.getYear(),
				today.getMonthValue(),
				UUID.randomUUID(),
				format.getExtension()
		);
	}

	private String createPublicUrl(String objectKey) {
		String baseUrl = properties.getPublicBaseUrl().replaceAll("/+$", "");
		return baseUrl + "/" + objectKey;
	}
}
