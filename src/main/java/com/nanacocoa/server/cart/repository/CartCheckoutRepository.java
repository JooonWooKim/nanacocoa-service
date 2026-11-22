package com.nanacocoa.server.cart.repository;
import com.nanacocoa.server.cart.entity.CartCheckout;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CartCheckoutRepository extends JpaRepository<CartCheckout,Long>{
    Optional<CartCheckout> findByOrderId(Long orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartCheckout c where c.orderId=:order")
    Optional<CartCheckout> orderForUpdate(@Param("order") Long order);
    @Query("select c.orderId from CartCheckout c where c.state='READY' order by c.id")
    List<Long> readyOrders(Pageable pageable);
}
