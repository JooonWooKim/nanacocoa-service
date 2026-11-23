package com.nanacocoa.server.products.dto.response;

import com.nanacocoa.server.products.entity.ProductCategory;
import java.util.List;

public final class DiscoveryResponses {
    private DiscoveryResponses() {}

    public record Search(List<ProductsResponse> items, long totalItems, int page, long totalPages) {}
    public record Category(Long id, String name, long sortOrder) {
        public static Category from(ProductCategory category) {
            return new Category(category.getId(), category.getName(), category.getSortOrder());
        }
    }
    public record AdminCategory(Long id, String name, long sortOrder, boolean visible) {
        public static AdminCategory from(ProductCategory category) {
            return new AdminCategory(category.getId(), category.getName(), category.getSortOrder(), category.isVisible());
        }
    }
}
