package com.nanacocoa.server.notification.repository;

import com.nanacocoa.server.notification.entity.RestockSubscription;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RestockSubscriptionRepository extends JpaRepository<RestockSubscription, Long> {
    Optional<RestockSubscription> findByProductIdAndMemberIdAndActiveTrue(Long productId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RestockSubscription s where s.productId = :productId and s.memberId = :memberId and s.active = true")
    Optional<RestockSubscription> lockActive(@Param("productId") Long productId, @Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RestockSubscription s where s.id = :id")
    Optional<RestockSubscription> lockById(@Param("id") Long id);

    interface Candidate {
        Long getSubscriptionId();
        Long getProductId();
    }

    @Query(value = """
        SELECT s.id AS subscriptionId, s.product_id AS productId
        FROM product_restock_subscriptions s
        JOIN products p ON p.id = s.product_id
        JOIN product_restock_events e ON e.product_id = p.id AND e.availability_epoch = p.availability_epoch
        LEFT JOIN order_notifications n ON n.restock_subscription_id = s.id
        WHERE s.active = true AND p.sale_status = 'ON_SALE' AND p.stock_quantity > 0
          AND e.availability_epoch > s.subscribed_epoch AND e.confirmed_at >= :cutoff
          AND (n.id IS NULL OR n.status = 'PENDING')
        ORDER BY s.id
        LIMIT 100
        """, nativeQuery = true)
    List<Candidate> ready(@Param("cutoff") LocalDateTime cutoff);
}
