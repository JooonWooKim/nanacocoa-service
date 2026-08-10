package com.nanacocoa.server.payment.repository;

import com.nanacocoa.server.payment.entity.CancellationStatus;
import com.nanacocoa.server.payment.entity.PaymentCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
	Optional<PaymentCancellation> findByIdempotencyKey(String idempotencyKey);

	Optional<PaymentCancellation> findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
			Long paymentId,
			CancellationStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select c from PaymentCancellation c
			join fetch c.payment p
			join fetch p.order o
			join fetch o.member
			where c.id = :cancellationId
			""")
	Optional<PaymentCancellation> findByIdForUpdate(@Param("cancellationId") Long cancellationId);

	@Query("""
			select c from PaymentCancellation c
			join fetch c.payment p
			join fetch p.order o
			join fetch o.member
			where c.status = :status
			and c.nextRetryAt <= :now
			order by c.nextRetryAt asc
			""")
	List<PaymentCancellation> findRecoverable(
			@Param("status") CancellationStatus status,
			@Param("now") LocalDateTime now,
			Pageable pageable);
}
