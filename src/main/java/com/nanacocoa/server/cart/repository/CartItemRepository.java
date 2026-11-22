package com.nanacocoa.server.cart.repository;
import com.nanacocoa.server.cart.entity.CartItem;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CartItemRepository extends JpaRepository<CartItem,Long> {
    List<CartItem> findByMemberIdOrderById(Long memberId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartItem c where c.memberId=:member and c.productId=:product")
    Optional<CartItem> productForUpdate(@Param("member") Long member,@Param("product") Long product);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartItem c where c.memberId=:member and c.id=:id")
    Optional<CartItem> ownedForUpdate(@Param("member") Long member,@Param("id") Long id);
}
