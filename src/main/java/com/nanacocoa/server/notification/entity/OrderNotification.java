package com.nanacocoa.server.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_notifications")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OrderNotification {
    public enum Type { PAYMENT_COMPLETED, SHIPPED, REFUND_COMPLETED }
    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long orderId;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false, length = 64) private String orderNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24) private Type eventType;
    @Column(nullable = false) private LocalDateTime confirmedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private int cycleAttempts;
    @Column(length = 32) private String failureCode;
    private LocalDateTime failedAt;
    private LocalDateTime readAt;

    public OrderNotification(Long orderId, Long memberId, String orderNumber, Type eventType, LocalDateTime confirmedAt) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.orderNumber = orderNumber;
        this.eventType = eventType;
        this.confirmedAt = confirmedAt;
        this.status = Status.PENDING;
    }

    public void publish() {
        attemptCount++;
        cycleAttempts++;
        status = Status.PUBLISHED;
    }

    public void fail(LocalDateTime now) {
        attemptCount++;
        cycleAttempts++;
        failureCode = "PUBLISH_FAILED";
        failedAt = now;
        if (cycleAttempts >= 3) status = Status.FAILED;
    }

    public void retry() {
        if (status != Status.FAILED) return;
        status = Status.PENDING;
        cycleAttempts = 0;
    }

    public void read(LocalDateTime now) {
        if (readAt == null) readAt = now;
    }
}
