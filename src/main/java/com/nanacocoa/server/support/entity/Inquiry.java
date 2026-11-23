package com.nanacocoa.server.support.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "support_inquiries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Inquiry {
    public enum Status { PENDING, ANSWERED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long memberId;
    @Column(name = "order_id") private Long orderRecordId;
    @Column(nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 2000) private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(length = 2000) private String replyBody;
    private Long repliedBy;
    private LocalDateTime repliedAt;

    public Inquiry(Long memberId, Long orderRecordId, String requestKey, String title, String body, LocalDateTime now) {
        this.memberId = memberId;
        this.orderRecordId = orderRecordId;
        this.requestKey = requestKey;
        this.title = title;
        this.body = body;
        this.createdAt = now;
        this.status = Status.PENDING;
    }

    public void answer(String body, Long operator, LocalDateTime now) {
        replyBody = body;
        repliedBy = operator;
        repliedAt = now;
        status = Status.ANSWERED;
    }
}
