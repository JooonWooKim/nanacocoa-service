package com.nanacocoa.server.member.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_address_registrations", uniqueConstraints = @UniqueConstraint(
    name = "uk_address_registration_key", columnNames = {"member_id", "request_key"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddressRegistration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "request_key", nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 64) private String fingerprint;
    public ShippingAddressRegistration(Long memberId, String requestKey, String fingerprint) {
        this.memberId = memberId; this.requestKey = requestKey; this.fingerprint = fingerprint;
    }
}
