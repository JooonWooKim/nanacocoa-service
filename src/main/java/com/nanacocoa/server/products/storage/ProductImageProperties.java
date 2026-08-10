package com.nanacocoa.server.products.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "product-image")
public class ProductImageProperties {
	private String storageMode = "mock";
	private String bucket;
	private String publicBaseUrl;
	private String region;
}
