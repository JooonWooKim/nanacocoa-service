package com.nanacocoa.server.cart.repository;
import com.nanacocoa.server.cart.entity.CartAddition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CartAdditionRepository extends JpaRepository<CartAddition,Long>{
    Optional<CartAddition> findByMemberIdAndRequestKey(Long memberId,String requestKey);
}
