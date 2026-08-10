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
import static org.mockito.Mockito.verify;
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

	private Payment recoverablePayment(Long paymentId, String orderId) {
		Payment payment = mock(Payment.class);
		Order order = mock(Order.class);
		when(payment.getId()).thenReturn(paymentId);
		when(payment.getOrder()).thenReturn(order);
		when(order.getOrderId()).thenReturn(orderId);
		return payment;
	}

	private ApprovalWork work(LocalDateTime createdAt) {
		return new ApprovalWork(
				10L,
				"payment-key",
				"NC_order-123",
				38000L,
				"idem-key",
				"compensation-key",
				PaymentStatus.PENDING,
				PaymentRecoveryAction.VERIFY_APPROVAL,
				createdAt,
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
