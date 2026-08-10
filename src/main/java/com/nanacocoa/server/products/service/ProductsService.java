package com.nanacocoa.server.products.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.products.dto.reqeust.RegisterProductsRequest;
import com.nanacocoa.server.products.dto.response.ProductsResponse;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import com.nanacocoa.server.products.storage.ProductImageFormat;
import com.nanacocoa.server.products.storage.ProductImageStorage;
import com.nanacocoa.server.products.storage.ProductImageValidator;
import com.nanacocoa.server.products.storage.StoredProductImage;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.nanacocoa.server.common.exception.ErrorCode.ALREADY_EXIST_PRODUCTS;
import static com.nanacocoa.server.common.exception.ErrorCode.AUTHENTICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_PRODUCTS;

@Service
@AllArgsConstructor
public class ProductsService {
	private final ProductsRepository productsRepository;
	private final MemberRepository memberRepository;
	private final ProductImageStorage productImageStorage;
	private final ProductImageValidator productImageValidator;

	@Transactional
	public ProductsResponse registerProducts(RegisterProductsRequest request, UserDetailsImpl userDetails) {
		validateRegistration(request, userDetails);

		Products savedProduct = productsRepository.save(request.toProducts());
		return ProductsResponse.from(savedProduct);
	}

	@Transactional
	public ProductsResponse registerProducts(
			RegisterProductsRequest request,
			MultipartFile image,
			UserDetailsImpl userDetails) {
		validateRegistration(request, userDetails);

		ProductImageFormat format = productImageValidator.validate(image);
		StoredProductImage storedImage = productImageStorage.upload(image, format);
		registerImageRollback(storedImage.objectKey());

		Products savedProduct = productsRepository.saveAndFlush(request.toProducts(storedImage.publicUrl()));
		return ProductsResponse.from(savedProduct);
	}

	private void validateRegistration(RegisterProductsRequest request, UserDetailsImpl userDetails) {
		if (userDetails == null) {
			throw new NanacocoaException(AUTHENTICATION_REQUIRED);
		}

		Member member = memberRepository.findByEmail(userDetails.getUsername()).orElseThrow(
				() -> new NanacocoaException(AUTHENTICATION_REQUIRED)
		);
		member.checkAdmin();

		if (productsRepository.existsByName(request.getName())) {
			throw new NanacocoaException(ALREADY_EXIST_PRODUCTS);
		}
	}

	private void registerImageRollback(String objectKey) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					productImageStorage.delete(objectKey);
				}
			}
		});
	}

	@Transactional(readOnly = true)
	public List<ProductsResponse> getProducts() {
		return productsRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
				.map(ProductsResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ProductsResponse getProduct(Long productId) {
		Products product = productsRepository.findById(productId).orElseThrow(
				() -> new NanacocoaException(NOT_FOUND_PRODUCTS)
		);
		return ProductsResponse.from(product);
	}
}
