package com.nanacocoa.server.products.dto.reqeust;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class CategoryRequests {
    private CategoryRequests() {}

    public record Create(@NotBlank String name) {}
    public record Change(String name, @PositiveOrZero @Max(Integer.MAX_VALUE) Long sortOrder, Boolean visible) {}
    public record Assignment(@com.fasterxml.jackson.annotation.JsonProperty(required = true) @Positive Long categoryId) {}
}
