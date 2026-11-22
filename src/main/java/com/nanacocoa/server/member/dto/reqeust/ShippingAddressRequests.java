package com.nanacocoa.server.member.dto.reqeust;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class ShippingAddressRequests {
    private ShippingAddressRequests() {}
    public record Details(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotNull @Pattern(regexp = "[0-9]{5}") String postalCode,
        @NotBlank @Size(max = 500) String basicAddress,
        @Size(max = 500) String detailAddress) {}
    public record Create(@NotNull @Valid Details address, boolean makeDefault,
                         @NotNull @PositiveOrZero Long expectedRevision) {}
    public record Update(@NotNull @Valid Details address,
                         @NotNull @PositiveOrZero Long expectedVersion,
                         @NotNull @PositiveOrZero Long expectedRevision) {}
    public record Version(@NotNull @PositiveOrZero Long expectedVersion,
                          @NotNull @PositiveOrZero Long expectedRevision) {}
}
