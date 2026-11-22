package com.nanacocoa.server.member.dto.response;

import java.util.List;

public record ShippingAddressResponse(long revision, String checkoutAccount, List<Item> items) {
    public record Item(Long id, Long version, String label, String recipientName, String phoneNumber,
                       String postalCode, String basicAddress, String detailAddress, boolean defaultAddress) {}
}
