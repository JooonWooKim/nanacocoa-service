package com.nanacocoa.server.order.repository;

import com.nanacocoa.server.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
	Optional<Order> findByOrderId(String orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from PurchaseOrder o join fetch o.member where o.orderId = :orderId")
	Optional<Order> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
