package com.nanacocoa.server.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ShippingStatus status;

    @Column(length = 32)
    private String carrierCode;

    @Column(length = 64)
    private String trackingNumber;

    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;

    @Column(nullable = false)
    private long revision;

    public Shipment(Order order) {
        this.order = order;
    }

    public void ship(String carrierCode, String trackingNumber, LocalDateTime shippedAt) {
        this.status = ShippingStatus.SHIPPED;
        this.shippedAt = shippedAt;
        updateTracking(carrierCode, trackingNumber);
    }

    public void updateTracking(String carrierCode, String trackingNumber) {
        this.carrierCode = carrierCode;
        this.trackingNumber = trackingNumber;
    }

    public void deliver(LocalDateTime deliveredAt) {
        this.status = ShippingStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    public void recordChange() {
        revision++;
    }
}
