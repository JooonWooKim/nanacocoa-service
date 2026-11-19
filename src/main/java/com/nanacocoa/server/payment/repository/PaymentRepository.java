package com.nanacocoa.server.payment.repository;

import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, PaymentStatus status);
	Optional<Payment> findByApprovalIdempotencyKey(String approvalIdempotencyKey);

	Optional<Payment> findByPaymentKey(String paymentKey);

	boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

	boolean existsByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);

	@Query("select p from Payment p join fetch p.order o join fetch o.member where p.id = :paymentId")
	Optional<Payment> findDetailsById(@Param("paymentId") Long paymentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :paymentId")
	Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

	@Query("""
			select p from Payment p
			join fetch p.order o
			join fetch o.member
			where p.recoveryAction <> :none
			and p.nextRetryAt <= :now
			order by p.nextRetryAt asc
			""")
	List<Payment> findRecoverable(
			@Param("none") PaymentRecoveryAction none,
			@Param("now") LocalDateTime now,
			Pageable pageable);

    // 양쪽 거래를 하나의 정렬·페이지 경계에서 조회한다. 외부 키·고객 정보는 선택하지 않는다.
    String OPERATIONS = """
        select 'APPROVAL' as type, p.id as id, p.id as paymentId, o.order_id as orderId,
          p.recovery_action as kind, p.amount as amount, p.status as status, p.status as paymentStatus,
          o.status as orderStatus, o.stock_state as stockState, p.created_at as createdAt,
          p.updated_at as updatedAt, p.retry_count as retryCount, p.next_retry_at as nextRetryAt,
          p.failure_code as failureCode,
          case when p.status = 'PENDING' and p.recovery_action <> 'NONE' then 1 else 0 end as pending
        from payments p join orders o on o.id = p.order_id
        union all
        select 'CANCELLATION', c.id, p.id, o.order_id, 'CANCELLATION', p.amount, c.status, p.status,
          o.status, o.stock_state, c.created_at, c.updated_at, c.retry_count, c.next_retry_at, c.failure_code,
          case when c.status = 'PENDING' then 1 else 0 end
        from payment_cancellations c join payments p on p.id = c.payment_id join orders o on o.id = p.order_id
        """;
    String OP_FILTER = " where x.pending = 1 and (:kind is null or x.kind = :kind) and (:orderId is null or x.orderId = :orderId) ";

    @Query(value = "select x.* from (" + OPERATIONS + ") x" + OP_FILTER
            + "order by x.createdAt asc, x.id asc, x.type asc limit :limit offset :offset", nativeQuery = true)
    List<OperationRow> findOperations(@Param("kind") String kind, @Param("orderId") String orderId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "select count(*) from (" + OPERATIONS + ") x" + OP_FILTER, nativeQuery = true)
    long countOperations(@Param("kind") String kind, @Param("orderId") String orderId);

    @Query(value = "select x.* from (" + OPERATIONS + ") x where x.type = :type and x.id = :id", nativeQuery = true)
    Optional<OperationRow> findOperation(@Param("type") String type, @Param("id") long id);

    interface OperationRow {
        String getType(); Long getId(); Long getPaymentId(); String getOrderId(); String getKind();
        Long getAmount(); String getStatus(); String getPaymentStatus(); String getOrderStatus(); String getStockState();
        LocalDateTime getCreatedAt(); LocalDateTime getUpdatedAt(); Integer getRetryCount();
        LocalDateTime getNextRetryAt(); String getFailureCode(); Integer getPending();
    }
}
