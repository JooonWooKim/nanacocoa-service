package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.payment.dto.response.PaymentResponse;
import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.lock.OrderPaymentLock;
import com.nanacocoa.server.payment.pg.TossPaymentResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsException;
import com.nanacocoa.server.payment.repository.PaymentCancellationRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.ApprovalWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentCancellationRepository cancellationRepository;
	@Mock
	private PaymentTransactionalService transactionalService;
	@Mock
	private TossPaymentsClient tossPaymentsClient;

	private PaymentRecoveryService recoveryService;

	@BeforeEach
	void setUp() {
		OrderPaymentLock directLock = new OrderPaymentLock() {
			@Override
			public <T> T withOrderLock(String orderId, Supplier<T> action) {
				return action.get();
			}
		};
		recoveryService = new PaymentRecoveryService(
				paymentRepository,
				cancellationRepository,
				transactionalService,
				directLock,
				tossPaymentsClient,
				Duration.ofMinutes(11)
		);
	}

	@Test
	void completesPendingPaymentWhenProviderLookupIsDone() {
		Payment payment = recoverablePayment(10L, "NC_order-123");
		ApprovalWork work = work(LocalDateTime.now().minusMinutes(1));
		TossPaymentResponse done = doneResponse();
		when(paymentRepository.findRecoverable(eq(PaymentRecoveryAction.NONE), any(), any(Pageable.class)))
				.thenReturn(List.of(payment));
		when(transactionalService.getRecoveryApproval(10L)).thenReturn(work);
		when(tossPaymentsClient.findByOrderId("NC_order-123")).thenReturn(done);

		recoveryService.reconcileDueApprovals(100);

		verify(transactionalService).completeApproval(10L, done);
	}

	@Test
	void failsExpiredApprovalOnlyAfterAuthoritativeNotFound() {
		Payment payment = recoverablePayment(10L, "NC_order-123");
		ApprovalWork work = work(LocalDateTime.now().minusMinutes(12));
		when(paymentRepository.findRecoverable(eq(PaymentRecoveryAction.NONE), any(), any(Pageable.class)))
				.thenReturn(List.of(payment));
		when(transactionalService.getRecoveryApproval(10L)).thenReturn(work);
		when(tossPaymentsClient.findByOrderId("NC_order-123"))
				.thenThrow(new TossPaymentsException(TossPaymentsException.Kind.NOT_FOUND, "NOT_FOUND", "없음"));

		recoveryService.reconcileDueApprovals(100);

		verify(transactionalService).failApproval(10L, "NOT_FOUND", "없음");
	}

	@Test
	void retriesUnexpiredApprovalWithOriginalIdempotencyKeyAfterNotFound() {
		Payment payment = recoverablePayment(10L, "NC_order-123");
		ApprovalWork work = work(LocalDateTime.now().minusMinutes(1));
		TossPaymentResponse done = doneResponse();
		when(paymentRepository.findRecoverable(eq(PaymentRecoveryAction.NONE), any(), any(Pageable.class)))
				.thenReturn(List.of(payment));
		when(transactionalService.getRecoveryApproval(10L)).thenReturn(work);
		when(tossPaymentsClient.findByOrderId("NC_order-123"))
				.thenThrow(new TossPaymentsException(TossPaymentsException.Kind.NOT_FOUND, "NOT_FOUND", "없음"));
		when(tossPaymentsClient.confirm("payment-key", "NC_order-123", 38000L, "idem-key"))
				.thenReturn(done);

		recoveryService.reconcileDueApprovals(100);

		verify(transactionalService).completeApproval(10L, done);
	}

	@Test
	void releasesTerminalFailuresBeforeVerificationOrCompensation() {
		Payment payment = recoverablePayment(10L, "NC_order-123");
		when(paymentRepository.findRecoverable(eq(PaymentRecoveryAction.NONE), any(), any(Pageable.class)))
				.thenReturn(List.of(payment));
		LocalDateTime createdAt = LocalDateTime.now();
		when(transactionalService.getRecoveryApproval(10L))
				.thenReturn(work(createdAt), work(createdAt, PaymentRecoveryAction.COMPENSATE_APPROVAL));
		when(tossPaymentsClient.findByOrderId("NC_order-123")).thenReturn(
				new TossPaymentResponse("payment-key", "NC_order-123", 38000L, "ABORTED", null, null, List.of()),
				new TossPaymentResponse("payment-key", "NC_order-123", 38000L, "EXPIRED", null, null, List.of()));

		recoveryService.reconcileDueApprovals(100);
		recoveryService.reconcileDueApprovals(100);

		verify(transactionalService).failApproval(eq(10L), eq("ABORTED"), any());
		verify(transactionalService).failApproval(eq(10L), eq("EXPIRED"), any());
		verify(transactionalService, never()).requestCompensation(any(), any(), any());
		verify(tossPaymentsClient, times(2)).findByOrderId("NC_order-123");
		verifyNoMoreInteractions(tossPaymentsClient);
	}

	@Test
	void preservesRecoveryActionWhenLookupIdentifiersDoNotMatch() {
		Payment payment = recoverablePayment(10L, "NC_order-123");
		when(paymentRepository.findRecoverable(eq(PaymentRecoveryAction.NONE), any(), any(Pageable.class)))
				.thenReturn(List.of(payment));
		LocalDateTime createdAt = LocalDateTime.now();
		when(transactionalService.getRecoveryApproval(10L))
				.thenReturn(work(createdAt), work(createdAt, PaymentRecoveryAction.COMPENSATE_APPROVAL));
		when(tossPaymentsClient.findByOrderId("NC_order-123")).thenReturn(
				new TossPaymentResponse("other-key", "NC_order-123", 38000L, "ABORTED", null, null, List.of()),
				new TossPaymentResponse("payment-key", "other-order", 38000L, "EXPIRED", null, null, List.of()),
				new TossPaymentResponse("payment-key", "NC_order-123", 39000L, "EXPIRED", null, null, List.of()));

		for (int attempt = 0; attempt < 3; attempt++) {
			recoveryService.reconcileDueApprovals(100);
		}

		verify(transactionalService).recordUncertainApproval(eq(10L), eq("PG_PAYMENT_MISMATCH"), any());
		verify(transactionalService, times(2))
				.requestCompensation(eq(10L), eq("PG_PAYMENT_MISMATCH"), any());
		verify(transactionalService, never()).failApproval(any(), any(), any());
	}

	private Payment recoverablePayment(Long paymentId, String orderId) {
		Payment payment = mock(Payment.class);
		Order order = mock(Order.class);
		when(payment.getId()).thenReturn(paymentId);
		when(payment.getOrder()).thenReturn(order);
		when(order.getOrderId()).thenReturn(orderId);
		return payment;
	}

	private ApprovalWork work(LocalDateTime createdAt) {
		return work(createdAt, PaymentRecoveryAction.VERIFY_APPROVAL);
	}

	private ApprovalWork work(LocalDateTime createdAt, PaymentRecoveryAction recoveryAction) {
		return new ApprovalWork(
				10L,
				"payment-key",
				"NC_order-123",
				38000L,
				"idem-key",
				"compensation-key",
				PaymentStatus.PENDING,
				recoveryAction,
				createdAt,
				LocalDateTime.now().minusSeconds(1),
				true,
				new PaymentResponse(10L, "NC_order-123", 38000L, PaymentStatus.PENDING, null, null, null, null)
		);
	}

	private TossPaymentResponse doneResponse() {
		return new TossPaymentResponse(
				"payment-key", "NC_order-123", 38000L, "DONE", "카드", OffsetDateTime.now(), List.of()
		);
	}
}
