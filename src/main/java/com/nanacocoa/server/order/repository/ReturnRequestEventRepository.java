package com.nanacocoa.server.order.repository;
import com.nanacocoa.server.order.entity.ReturnRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ReturnRequestEventRepository extends JpaRepository<ReturnRequestEvent, Long> {
    Optional<ReturnRequestEvent> findByRequestIdAndRequestKey(Long requestId, String requestKey);
    List<ReturnRequestEvent> findByRequestIdOrderByIdAsc(Long requestId);
}
