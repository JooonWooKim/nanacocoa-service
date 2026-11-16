package com.nanacocoa.server.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentEvent {
    public enum Action { SHIP, CORRECT_TRACKING, DELIVER, VERIFY_UNSHIPPED, IMPORT_SHIPPED, IMPORT_DELIVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(nullable = false, length = 36)
    private String requestKey;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShippingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShippingStatus nextStatus;

    @Column(length = 32)
    private String previousCarrier;

    @Column(length = 64)
    private String previousTracking;

    @Column(length = 32)
    private String nextCarrier;

    @Column(length = 64)
    private String nextTracking;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false, length = 500)
    private String reason;

    public ShipmentEvent(Shipment shipment, String requestKey, String fingerprint, Action action,
                         ShippingStatus previousStatus, ShippingStatus nextStatus, String previousCarrier,
                         String previousTracking, Long operatorId, LocalDateTime recordedAt, String reason) {
        this.shipment = shipment;
        this.requestKey = requestKey;
        this.fingerprint = fingerprint;
        this.action = action;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.previousCarrier = previousCarrier;
        this.previousTracking = previousTracking;
        this.nextCarrier = shipment.getCarrierCode();
        this.nextTracking = shipment.getTrackingNumber();
        this.operatorId = operatorId;
        this.recordedAt = recordedAt;
        this.reason = reason;
    }
}
