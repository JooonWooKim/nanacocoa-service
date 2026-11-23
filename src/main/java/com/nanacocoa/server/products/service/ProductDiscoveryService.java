package com.nanacocoa.server.products.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.products.dto.reqeust.CategoryRequests;
import com.nanacocoa.server.products.dto.response.DiscoveryResponses.*;
import com.nanacocoa.server.products.dto.response.InventoryResponse;
import com.nanacocoa.server.products.dto.response.ProductsResponse;
import com.nanacocoa.server.products.entity.ProductCategory;
import com.nanacocoa.server.products.repository.ProductCategoryRepository;
import com.nanacocoa.server.products.repository.ProductsRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class ProductDiscoveryService {
    private static final int PAGE_SIZE = 24;
    private final ProductCategoryRepository categories;
    private final ProductsRepository products;
    private final MemberRepository members;

    @Transactional
    public Search search(String query, Long category, int page) {
        String term = query == null ? "" : query.strip();
        if (term.length() > 100 || page < 1 || category != null && category < 1) {
            throw new NanacocoaException(INVALID_REQUEST);
        }
        if (category != null) {
            categories.findForSearch(category).filter(ProductCategory::isVisible)
                    .orElseThrow(() -> new NanacocoaException(CATEGORY_UNAVAILABLE));
        }
        String normalized = term.toLowerCase(Locale.ROOT);
        long total = products.countSearch(normalized, category);
        long pages = total == 0 ? 0 : (total - 1) / PAGE_SIZE + 1;
        List<ProductsResponse> items = page > pages ? List.of()
                : products.search(normalized, category, PageRequest.of(page - 1, PAGE_SIZE))
                    .stream().map(ProductsResponse::from).toList();
        return new Search(items, total, page, pages);
    }

    @Transactional(readOnly = true)
    public List<Category> publicCategories() {
        return categories.findByVisibleTrueOrderBySortOrderAscIdAsc().stream().map(Category::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCategory> adminCategories(UserDetailsImpl user) {
        requireAdmin(user);
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(AdminCategory::from).toList();
    }

    @Transactional
    public AdminCategory create(CategoryRequests.Create request, UserDetailsImpl user) {
        requireAdmin(user);
        String name = validateName(request.name());
        long last = categories.findFirstByOrderBySortOrderDescIdDesc().map(ProductCategory::getSortOrder).orElse(-1L);
        long position = last >= Integer.MAX_VALUE ? Integer.MAX_VALUE : last + 1;
        return saveCategory(new ProductCategory(name, nameKey(name), position));
    }

    @Transactional
    public AdminCategory update(Long id, CategoryRequests.Change request, UserDetailsImpl user) {
        requireAdmin(user);
        if (request.name() == null && request.sortOrder() == null && request.visible() == null) {
            throw new NanacocoaException(INVALID_REQUEST);
        }
        ProductCategory category = categories.findForUpdate(id)
                .orElseThrow(() -> new NanacocoaException(CATEGORY_UNAVAILABLE));
        if (request.name() != null) {
            String name = validateName(request.name());
            category.rename(name, nameKey(name));
        }
        if (request.sortOrder() != null) category.reorder(request.sortOrder());
        if (request.visible() != null) category.show(request.visible());
        return saveCategory(category);
    }

    @Transactional
    public InventoryResponse assign(Long id, CategoryRequests.Assignment request, UserDetailsImpl user) {
        requireAdmin(user);
        var product = products.findByIdForUpdate(id).orElseThrow(() -> new NanacocoaException(NOT_FOUND_PRODUCTS));
        Long target = validatedCategory(request.categoryId());
        product.assignCategory(target);
        products.flush();
        return InventoryResponse.from(product);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Long validatedCategory(Long id) {
        if (id == null) return null;
        return categories.findForUpdate(id).filter(ProductCategory::isVisible)
                .orElseThrow(() -> new NanacocoaException(CATEGORY_UNAVAILABLE)).getId();
    }

    private AdminCategory saveCategory(ProductCategory category) {
        try {
            return AdminCategory.from(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException duplicate) {
            throw new NanacocoaException(CATEGORY_NAME_CONFLICT);
        }
    }

    private String validateName(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 80) throw new NanacocoaException(INVALID_REQUEST);
        return name;
    }

    private String nameKey(String name) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private void requireAdmin(UserDetailsImpl user) {
        if (user == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        members.findByEmail(user.getUsername()).orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED))
                .checkAdmin();
    }
}
