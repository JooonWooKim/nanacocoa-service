package com.nanacocoa.server.notification.repository;

import com.nanacocoa.server.notification.entity.OrderNotification;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.OrderNotification.Type;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {
    interface Confirmation {
        Long getOrderId();
        Long getMemberId();
        String getOrderNumber();
        String getEventType();
        LocalDateTime getConfirmedAt();
    }

    @Query(value = """
        SELECT o.id AS orderId, o.member_id AS memberId, o.order_id AS orderNumber,
               e.event_type AS eventType, MIN(e.confirmed_at) AS confirmedAt
        FROM (
            SELECT p.order_id, 'PAYMENT_COMPLETED' AS event_type, p.notification_confirmed_at AS confirmed_at
              FROM payments p WHERE p.notification_confirmed_at >= :cutoff
            UNION ALL
            SELECT s.order_id, 'SHIPPED', se.notification_confirmed_at
              FROM shipment_events se JOIN shipments s ON s.id = se.shipment_id
             WHERE se.notification_confirmed_at >= :cutoff AND se.action = 'SHIP'
            UNION ALL
            SELECT p.order_id, 'REFUND_COMPLETED', c.notification_confirmed_at
              FROM payment_cancellations c JOIN payments p ON p.id = c.payment_id
             WHERE c.notification_confirmed_at >= :cutoff AND c.status = 'SUCCESS'
        ) e JOIN orders o ON o.id = e.order_id
        WHERE NOT EXISTS (
            SELECT 1 FROM order_notifications n WHERE n.order_id = e.order_id AND n.event_type = e.event_type
        )
        GROUP BY o.id, o.member_id, o.order_id, e.event_type
        ORDER BY MIN(e.confirmed_at), o.id, e.event_type
        LIMIT 100
        """, nativeQuery = true)
    List<Confirmation> undiscovered(@Param("cutoff") LocalDateTime cutoff);

    interface ReplyConfirmation {
        Long getInquiryId();
        Long getMemberId();
        LocalDateTime getConfirmedAt();
    }

    @Query(value = """
        SELECT i.id AS inquiryId, i.member_id AS memberId, i.notification_confirmed_at AS confirmedAt
        FROM support_inquiries i
        WHERE i.status = 'ANSWERED' AND i.notification_confirmed_at >= :cutoff
          AND NOT EXISTS (SELECT 1 FROM order_notifications n WHERE n.inquiry_id = i.id)
        ORDER BY i.notification_confirmed_at, i.id
        LIMIT 100
        """, nativeQuery = true)
    List<ReplyConfirmation> undiscoveredReplies(@Param("cutoff") LocalDateTime cutoff);

    boolean existsByOrderIdAndEventType(Long orderId, Type eventType);
    boolean existsByInquiryId(Long inquiryId);

    @Query("""
        select n.id from OrderNotification n
        where n.status = :status and n.confirmedAt >= :cutoff
          and ((:replies = true and n.inquiryId is not null) or (:replies = false and n.inquiryId is null))
        order by n.id
        """)
    List<Long> pendingIds(@Param("status") Status status, @Param("cutoff") LocalDateTime cutoff,
                          @Param("replies") boolean replies, Pageable pageable);
    Slice<OrderNotification> findByStatus(Status status, Pageable pageable);
    Slice<OrderNotification> findByMemberIdAndStatus(Long memberId, Status status, Pageable pageable);
    long countByMemberIdAndStatusAndReadAtIsNull(Long memberId, Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from OrderNotification n where n.id = :id")
    Optional<OrderNotification> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderNotification> findByIdAndMemberIdAndStatus(Long id, Long memberId, Status status);
}
