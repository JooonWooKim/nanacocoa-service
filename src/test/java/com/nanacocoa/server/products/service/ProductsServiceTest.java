package com.nanacocoa.server.products.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.products.dto.reqeust.RegisterProductsRequest;
import com.nanacocoa.server.products.repository.ProductsRepository;
import com.nanacocoa.server.products.storage.ProductImageFormat;
import com.nanacocoa.server.products.storage.ProductImageStorage;
import com.nanacocoa.server.products.storage.ProductImageValidator;
import com.nanacocoa.server.products.storage.StoredProductImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static com.nanacocoa.server.common.exception.ErrorCode.ALREADY_EXIST_PRODUCTS;
import static com.nanacocoa.server.common.exception.ErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductsServiceTest {
	@Mock
	private ProductsRepository productsRepository;
	@Mock
	private MemberRepository memberRepository;
	@Mock
	private ProductImageStorage productImageStorage;

	private ProductsService productsService;
	private UserDetailsImpl adminUser;

	@BeforeEach
	void setUp() {
		productsService = new ProductsService(
				productsRepository,
				memberRepository,
				productImageStorage,
				new ProductImageValidator()
		);
		Member admin = Member.builder()
				.email("admin@example.com")
				.name("운영자")
				.password("encoded-password")
				.build();
		admin.setAdmin(true);
		adminUser = new UserDetailsImpl(admin, admin.getEmail());
		when(memberRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void duplicateProductDoesNotUploadImage() {
		RegisterProductsRequest request = request("중복 상품");
		when(productsRepository.existsByName(request.getName())).thenReturn(true);

		assertThatThrownBy(() -> productsService.registerProducts(request, pngImage(), adminUser))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ALREADY_EXIST_PRODUCTS));

		verify(productImageStorage, never()).upload(any(), any());
		verify(productsRepository, never()).saveAndFlush(any());
	}

	@Test
	void storageFailureDoesNotSaveProduct() {
		RegisterProductsRequest request = request("업로드 실패 상품");
		when(productsRepository.existsByName(request.getName())).thenReturn(false);
		when(productImageStorage.upload(any(), any(ProductImageFormat.class)))
				.thenThrow(new NanacocoaException(PRODUCT_IMAGE_UPLOAD_FAILED));

		assertThatThrownBy(() -> productsService.registerProducts(request, pngImage(), adminUser))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(PRODUCT_IMAGE_UPLOAD_FAILED));

		verify(productsRepository, never()).saveAndFlush(any());
	}

	@Test
	void databaseRollbackDeletesUploadedImage() {
		RegisterProductsRequest request = request("DB 저장 실패 상품");
		when(productsRepository.existsByName(request.getName())).thenReturn(false);
		when(productImageStorage.upload(any(), any(ProductImageFormat.class)))
				.thenReturn(new StoredProductImage("products/2026/07/image.png", "https://cdn.example.com/image.png"));
		when(productsRepository.saveAndFlush(any()))
				.thenThrow(new DataIntegrityViolationException("forced database failure"));
		TransactionSynchronizationManager.initSynchronization();

		assertThatThrownBy(() -> productsService.registerProducts(request, pngImage(), adminUser))
				.isInstanceOf(DataIntegrityViolationException.class);

		TransactionSynchronizationManager.getSynchronizations().forEach(
				synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
		);
		verify(productImageStorage).delete("products/2026/07/image.png");
	}

	private RegisterProductsRequest request(String name) {
		return new RegisterProductsRequest(
				name,
				38000L,
				"상품 요약",
				"상품 상세 제목",
				"상품 상세 설명",
				null
		);
	}

	private MockMultipartFile pngImage() {
		byte[] bytes = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
		return new MockMultipartFile("image", "product.png", "image/png", bytes);
	}
}
