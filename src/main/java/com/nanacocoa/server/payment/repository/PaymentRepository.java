package com.nanacocoa.server.payment.repository;

import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
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
	Optional<Payment> findByApprovalIdempotencyKey(String approvalIdempotencyKey);

	Optional<Payment> findByPaymentKey(String paymentKey);

	@Query("select p from Payment p join fetch p.order o join fetch o.member where p.id = :paymentId")
	Optional<Payment> findDetailsById(@Param("paymentId") Long paymentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p join fetch p.order o join fetch o.member where p.id = :paymentId")
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
}
