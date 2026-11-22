package com.nanacocoa.server.member.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Version @Column(nullable = false) private Long version;
    @Column(nullable = false, length = 80) private String label;
    @Column(name = "recipient_name", nullable = false, length = 100) private String recipientName;
    @Column(name = "phone_number", nullable = false, length = 30) private String phoneNumber;
    @Column(name = "postal_code", nullable = false, length = 5) private String postalCode;
    @Column(name = "basic_address", nullable = false, length = 500) private String basicAddress;
    @Column(name = "detail_address", nullable = false, length = 500) private String detailAddress;

    public ShippingAddress(Long memberId, String label, String recipientName, String phoneNumber,
                           String postalCode, String basicAddress, String detailAddress) {
        this.memberId = memberId;
        change(label, recipientName, phoneNumber, postalCode, basicAddress, detailAddress);
    }
    public void change(String label, String recipientName, String phoneNumber,
                       String postalCode, String basicAddress, String detailAddress) {
        this.label = label; this.recipientName = recipientName; this.phoneNumber = phoneNumber;
        this.postalCode = postalCode; this.basicAddress = basicAddress; this.detailAddress = detailAddress;
    }
}
