package com.nanacocoa.server.products.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.products.dto.reqeust.RegisterProductsRequest;
import com.nanacocoa.server.products.dto.response.ProductsResponse;
import com.nanacocoa.server.products.service.ProductsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductsController {
	private final ProductsService productsService;

	@PostMapping(value = {"", "/"}, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SuccessMessage<ProductsResponse>> registerProducts(
			@Valid @RequestBody RegisterProductsRequest request,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		ProductsResponse response = productsService.registerProducts(request, userDetails);
		return new ResponseEntity<>(new SuccessMessage<>("상품등록성공", response), HttpStatus.CREATED);
	}

	@PostMapping(value = {"", "/"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<SuccessMessage<ProductsResponse>> registerProductsWithImage(
			@Valid @RequestPart("product") RegisterProductsRequest request,
			@RequestPart("image") MultipartFile image,
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		ProductsResponse response = productsService.registerProducts(request, image, userDetails);
		return new ResponseEntity<>(new SuccessMessage<>("상품등록성공", response), HttpStatus.CREATED);
	}

	@GetMapping({"", "/"})
	public ResponseEntity<SuccessMessage<List<ProductsResponse>>> getProducts() {
		List<ProductsResponse> response = productsService.getProducts();
		return new ResponseEntity<>(new SuccessMessage<>("상품목록조회성공", response), HttpStatus.OK);
	}

	@GetMapping("/{productId}")
	public ResponseEntity<SuccessMessage<ProductsResponse>> getProduct(@PathVariable Long productId) {
		ProductsResponse response = productsService.getProduct(productId);
		return new ResponseEntity<>(new SuccessMessage<>("상품조회성공", response), HttpStatus.OK);
	}
}
