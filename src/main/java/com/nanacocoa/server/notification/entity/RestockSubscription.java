package com.nanacocoa.server.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_restock_subscriptions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RestockSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long productId;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false) private long subscribedEpoch;
    private Boolean active;

    public RestockSubscription(Long productId, Long memberId, long subscribedEpoch) {
        this.productId = productId;
        this.memberId = memberId;
        this.subscribedEpoch = subscribedEpoch;
        this.active = true;
    }

    public boolean isActive() { return Boolean.TRUE.equals(active); }

    public void close() { active = null; }
}
