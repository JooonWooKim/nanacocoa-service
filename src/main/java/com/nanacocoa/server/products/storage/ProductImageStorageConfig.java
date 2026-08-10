package com.nanacocoa.server.products.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(ProductImageProperties.class)
public class ProductImageStorageConfig {

	@Bean
	@ConditionalOnProperty(prefix = "product-image", name = "storage-mode", havingValue = "s3")
	S3Client productImageS3Client(ProductImageProperties properties) {
		validateS3Properties(properties);
		return S3Client.builder()
				.region(Region.of(properties.getRegion()))
				.build();
	}

	private void validateS3Properties(ProductImageProperties properties) {
		if (!StringUtils.hasText(properties.getBucket())
				|| !StringUtils.hasText(properties.getPublicBaseUrl())
				|| !StringUtils.hasText(properties.getRegion())) {
			throw new IllegalStateException(
					"s3 이미지 저장소에는 PRODUCT_IMAGE_BUCKET, PRODUCT_IMAGE_PUBLIC_BASE_URL, AWS_REGION 설정이 필요합니다."
			);
		}
	}
}
