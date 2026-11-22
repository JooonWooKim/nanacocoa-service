package com.nanacocoa.server.member.entity;

import jakarta.persistence.*;
import lombok.*;

/** 기본 주소를 회원별 단일 행의 포인터로 저장한다. 변경은 회원 잠금 아래 수행한다. */
@Entity @Table(name = "shipping_address_books")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddressBook {
    @Id @Column(name = "member_id") private Long memberId;
    @Column(nullable = false) private long revision;
    @Column(name = "default_address_id") private Long defaultAddressId;
    public ShippingAddressBook(Long memberId) { this.memberId = memberId; }
    public void designate(Long id) { defaultAddressId = id; }
    public void changed() { revision = Math.incrementExact(revision); }
}
