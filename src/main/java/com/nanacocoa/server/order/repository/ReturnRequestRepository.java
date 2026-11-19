package com.nanacocoa.server.order.repository;
import com.nanacocoa.server.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    @Query("select r.order.orderId from ReturnRequest r where r.id = :id")
    Optional<String> findOrderNumber(@Param("id") Long id);
    Optional<ReturnRequest> findByOrderIdAndRequestKey(Long orderId, String requestKey);
    Optional<ReturnRequest> findByActiveOrderId(Long orderId);
    List<ReturnRequest> findByOrderIdOrderByIdDesc(Long orderId);
    @Query("select r from ReturnRequest r where (:orderId is null or r.order.orderId = :orderId) and (:type is null or r.type = :type) and (:status is null or r.status = :status) order by r.createdAt desc, r.id desc")
    Slice<ReturnRequest> search(@Param("orderId") String orderId, @Param("type") ReturnRequest.Type type,
        @Param("status") ReturnRequest.Status status, Pageable pageable);
}
