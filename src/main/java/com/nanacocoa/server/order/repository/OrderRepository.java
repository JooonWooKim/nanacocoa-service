package com.nanacocoa.server.order.repository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
	Optional<Order> findByOrderId(String orderId);

	Slice<Order> findByMemberId(Long memberId, Pageable pageable);

	Optional<Order> findByOrderIdAndMemberId(String orderId, Long memberId);

	@Query("""
			select i.order.id as orderId, i.productName as productName
			from OrderItem i
			where i.order.id in :orderIds
			and i.id = (select min(firstItem.id) from OrderItem firstItem where firstItem.order = i.order)
			""")
	List<RepresentativeItem> findRepresentativeItems(@Param("orderIds") List<Long> orderIds);

    String SHIPPING_STATE = """
        case
            when s.status = SHIPPED then 'SHIPPED'
            when s.status = DELIVERED then 'DELIVERED'
            when o.status = CANCELED then 'CANCELED'
            when pending.id is not null then 'ON_HOLD'
            when o.legacyShippingReview = true and s.id is null then 'ON_HOLD'
            when o.status = PAID then
                case when paid.id is not null and o.stockState = SOLD
                    and trim(o.recipientName) <> '' and trim(o.phoneNumber) <> ''
                    and trim(o.shippingAddress) <> '' then 'PREPARING' else 'ON_HOLD' end
            else 'BEFORE_SHIPPING'
        end
        """;

    String SHIPPING_SELECT = """
        select distinct o.orderId as orderId, o.status as orderStatus, s.shippedAt as shippedAt,
            o.createdAt as createdAt, o.id as internalOrderId,
            case when pending.id is not null then 'CANCELLATION_PENDING'
                when paid.id is not null then 'SUCCESS'
                when o.status = PAYMENT_PENDING then 'PENDING'
                when refund.id is not null then 'CANCELED'
                when failed.id is not null then 'FAILED'
                else 'UNPAID' end as paymentStatus,
        """ + SHIPPING_STATE + """
            as shippingStatus
        from PurchaseOrder o
        left join Shipment s on s.order = o
        left join Payment paid on paid.order = o and paid.status = SUCCESS
        left join Payment pending on pending.order = o and pending.status = CANCEL_PENDING
        left join Payment canceled on canceled.order = o and canceled.status = CANCELED
            and o.status = CANCELED
        left join PaymentCancellation refund on refund.payment = canceled and refund.status = SUCCESS
        left join Payment failed on failed.order = o and failed.status in (FAILED, CANCELED)
            and o.status = CREATED
        where (:orderNumber is null or o.orderId = :orderNumber)
        """;

    @Query(SHIPPING_SELECT + " and (:state = 'ALL' or (" + SHIPPING_STATE + ") = :state) order by o.createdAt asc, o.id asc")
    Slice<ShippingSummary> findShippingOrders(@Param("orderNumber") String orderNumber,
                                              @Param("state") String state, Pageable pageable);

    @Query(SHIPPING_SELECT)
    Optional<ShippingSummary> findShippingSummary(@Param("orderNumber") String orderNumber);

    interface ShippingSummary {
        String getOrderId();
        OrderStatus getOrderStatus();
        String getPaymentStatus();
        String getShippingStatus();
        LocalDateTime getShippedAt();
    }

	interface RepresentativeItem {
		Long getOrderId();
		String getProductName();
	}

	@Query("select o.orderId from PurchaseOrder o where o.status = com.nanacocoa.server.order.entity.OrderStatus.CREATED and o.reservationExpiresAt <= :now order by o.reservationExpiresAt")
	List<String> findExpiredOrderIds(@Param("now") LocalDateTime now, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from PurchaseOrder o where o.orderId = :orderId")
	Optional<Order> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
