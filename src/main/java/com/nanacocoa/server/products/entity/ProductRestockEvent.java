package com.nanacocoa.server.products.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_restock_events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductRestockEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long productId;
    @Column(nullable = false) private long availabilityEpoch;
    @Column(nullable = false) private LocalDateTime confirmedAt;

    public ProductRestockEvent(Long productId, long availabilityEpoch, LocalDateTime confirmedAt) {
        this.productId = productId;
        this.availabilityEpoch = availabilityEpoch;
        this.confirmedAt = confirmedAt;
    }
}
