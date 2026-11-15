package com.nanacocoa.server.order.repository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import com.nanacocoa.server.order.entity.Order;
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
