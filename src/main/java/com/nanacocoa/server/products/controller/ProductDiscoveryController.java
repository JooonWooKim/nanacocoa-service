package com.nanacocoa.server.products.controller;

import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.products.dto.reqeust.CategoryRequests;
import com.nanacocoa.server.products.dto.response.DiscoveryResponses.*;
import com.nanacocoa.server.products.dto.response.InventoryResponse;
import com.nanacocoa.server.products.service.ProductDiscoveryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductDiscoveryController {
    private final ProductDiscoveryService discovery;

    @GetMapping("/search")
    public ResponseEntity<SuccessMessage<Search>> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long category, @RequestParam(defaultValue = "1") int page) {
        return response(HttpStatus.OK, "상품 검색 성공", discovery.search(q, category, page));
    }

    @GetMapping("/categories")
    public ResponseEntity<SuccessMessage<List<Category>>> categories() {
        return response(HttpStatus.OK, "카테고리 조회 성공", discovery.publicCategories());
    }

    @GetMapping("/admin/categories")
    public ResponseEntity<SuccessMessage<List<AdminCategory>>> adminCategories(@AuthenticationPrincipal UserDetailsImpl user) {
        return response(HttpStatus.OK, "카테고리 조회 성공", discovery.adminCategories(user));
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<SuccessMessage<AdminCategory>> create(@Valid @RequestBody CategoryRequests.Create body,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return response(HttpStatus.CREATED, "카테고리 추가 성공", discovery.create(body, user));
    }

    @PatchMapping("/admin/categories/{id}")
    public ResponseEntity<SuccessMessage<AdminCategory>> update(@PathVariable Long id,
            @Valid @RequestBody CategoryRequests.Change body, @AuthenticationPrincipal UserDetailsImpl user) {
        return response(HttpStatus.OK, "카테고리 수정 성공", discovery.update(id, body, user));
    }

    @PutMapping("/admin/{productId}/category")
    public ResponseEntity<SuccessMessage<InventoryResponse>> assign(@PathVariable Long productId,
            @Valid @RequestBody CategoryRequests.Assignment body, @AuthenticationPrincipal UserDetailsImpl user) {
        return response(HttpStatus.OK, "상품 분류 변경 성공", discovery.assign(productId, body, user));
    }

    private static <T> ResponseEntity<SuccessMessage<T>> response(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new SuccessMessage<>(message, data));
    }
}
