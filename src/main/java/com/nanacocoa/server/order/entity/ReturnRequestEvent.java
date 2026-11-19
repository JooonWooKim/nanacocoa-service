package com.nanacocoa.server.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.nanacocoa.server.payment.entity.PaymentCancellation;

@Entity
@Table(name = "return_request_events", uniqueConstraints =
    @UniqueConstraint(name = "uk_return_action_key", columnNames = {"return_request_id", "request_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequestEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest request;
    @Column(name = "request_key", nullable = false, length = 36)
    private String requestKey;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ReturnRequest.Action action;
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_id")
    private PaymentCancellation cancellation;

    public ReturnRequestEvent(ReturnRequest request, String key, String fingerprint,
                              ReturnRequest.Action action, Long operatorId, String reason) {
        this.request = request; this.requestKey = key; this.fingerprint = fingerprint;
        this.action = action; this.operatorId = operatorId; this.reason = reason;
        this.recordedAt = LocalDateTime.now();
    }
    public void linkCancellation(PaymentCancellation cancellation) { this.cancellation = cancellation; }
}
