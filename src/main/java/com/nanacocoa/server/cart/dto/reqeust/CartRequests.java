package com.nanacocoa.server.cart.dto.reqeust;
import jakarta.validation.constraints.*;
public final class CartRequests {
    private CartRequests(){}
    public record Add(@NotNull @Positive Long productId,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictInteger.class) @NotNull @Positive Integer quantity){}
    public record Update(@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictInteger.class) @NotNull @Positive Integer quantity,@NotNull @PositiveOrZero Long expectedVersion){}
    public record Selection(@NotNull @Positive Long itemId,@NotNull @PositiveOrZero Long expectedVersion,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictInteger.class) @NotNull @Positive Integer quantity,@NotNull @Positive Long expectedUnitPrice){}
}
