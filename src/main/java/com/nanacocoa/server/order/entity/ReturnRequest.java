package com.nanacocoa.server.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.nanacocoa.server.payment.entity.PaymentCancellation;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests", uniqueConstraints = {
    @UniqueConstraint(name = "uk_return_request_key", columnNames = {"order_id", "request_key"}),
    @UniqueConstraint(name = "uk_return_active_order", columnNames = "active_order_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequest {
    public enum Type { CANCELLATION, RETURN }
    public enum Status { REQUESTED, APPROVED, REJECTED, COLLECTING, RECEIVED, INSPECTED }
    public enum Action { APPROVE, REJECT, COLLECT, RECEIVE, INSPECT, REFUND, RESTOCK }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "active_order_id")
    private Long activeOrderId;
    @Column(name = "request_key", nullable = false, length = 36)
    private String requestKey;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Status status;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;
    @Column(name = "policy_guidance", nullable = false, columnDefinition = "TEXT")
    private String policyGuidance;
    private Boolean resellable;
    private Boolean refundEligible;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_id")
    private PaymentCancellation cancellation;
    @Column(nullable = false)
    private long revision;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ReturnRequest(Order order, String key, String fingerprint, Type type, String reason,
                         String policyVersion, String policyGuidance) {
        this.order = order; this.activeOrderId = order.getId(); this.requestKey = key;
        this.fingerprint = fingerprint; this.type = type; this.reason = reason;
        this.policyVersion = policyVersion; this.policyGuidance = policyGuidance;
        this.status = Status.REQUESTED; this.createdAt = LocalDateTime.now();
    }
    public void change(Action action, Boolean resellable, Boolean refundEligible) {
        switch (action) {
            case APPROVE -> status = Status.APPROVED;
            case REJECT -> { status = Status.REJECTED; activeOrderId = null; }
            case COLLECT -> status = Status.COLLECTING;
            case RECEIVE -> status = Status.RECEIVED;
            case INSPECT -> { status = Status.INSPECTED; this.resellable = resellable; this.refundEligible = refundEligible; }
            case REFUND, RESTOCK -> { }
        }
        revision++;
    }
    public void linkCancellation(PaymentCancellation cancellation) { this.cancellation = cancellation; }
}
