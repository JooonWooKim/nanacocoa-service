package com.nanacocoa.server.products.repository;

import com.nanacocoa.server.products.entity.ProductRestockEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRestockEventRepository extends JpaRepository<ProductRestockEvent, Long> {
    Optional<ProductRestockEvent> findByProductIdAndAvailabilityEpoch(Long productId, long availabilityEpoch);
}
