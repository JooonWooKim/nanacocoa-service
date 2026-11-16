package com.nanacocoa.server.order.repository;

import com.nanacocoa.server.order.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    Optional<ShipmentEvent> findByShipmentIdAndRequestKey(Long shipmentId, String requestKey);
    List<ShipmentEvent> findByShipmentIdOrderByIdDesc(Long shipmentId);
}
