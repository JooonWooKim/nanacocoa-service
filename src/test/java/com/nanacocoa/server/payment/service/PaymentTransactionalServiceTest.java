package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.repository.PaymentCancellationRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import com.nanacocoa.server.products.entity.Products;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static com.nanacocoa.server.common.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_AMOUNT_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionalServiceTest {
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentCancellationRepository cancellationRepository;
	@Mock
	private OrderRepository orderRepository;

	private PaymentTransactionalService service;

	@BeforeEach
	void setUp() {
		service = new PaymentTransactionalService(
				paymentRepository,
				cancellationRepository,
				orderRepository,
				Duration.ofSeconds(30)
		);
	}

	@Test
	void persistsPendingAttemptAndMovesOrderToPaymentPending() {
		Order order = order(38000L);
		ConfirmPaymentRequest request = new ConfirmPaymentRequest("payment-key", order.getOrderId(), 38000L);
		when(orderRepository.findByOrderIdForUpdate(order.getOrderId())).thenReturn(Optional.of(order));
		when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentTransactionalService.ApprovalWork work = service.prepareApproval(
				request,
				"idem-key",
				"fingerprint",
				"buyer@example.com"
		);

		assertThat(work.amount()).isEqualTo(38000L);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
		verify(paymentRepository).saveAndFlush(any(Payment.class));
	}

	@Test
	void rejectsSameIdempotencyKeyWithDifferentFingerprint() {
		Payment payment = org.mockito.Mockito.mock(Payment.class);
		when(payment.getRequestFingerprint()).thenReturn("original-fingerprint");
		when(paymentRepository.findByApprovalIdempotencyKey("idem-key")).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> service.replayApproval("idem-key", "different-fingerprint"))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_REUSED));
	}

	@Test
	void rejectsClientAmountThatDiffersFromOrderTotal() {
		Order order = order(38000L);
		ConfirmPaymentRequest request = new ConfirmPaymentRequest("payment-key", order.getOrderId(), 39000L);
		when(orderRepository.findByOrderIdForUpdate(order.getOrderId())).thenReturn(Optional.of(order));

		assertThatThrownBy(() -> service.prepareApproval(
				request,
				"idem-key",
				"fingerprint",
				"buyer@example.com"
		))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(PAYMENT_AMOUNT_MISMATCH));

		verify(paymentRepository, never()).saveAndFlush(any());
	}

	private Order order(long amount) {
		Member member = Member.builder()
				.email("buyer@example.com")
				.name("구매자")
				.password("encoded")
				.build();
		Products product = Products.builder()
				.name("상품")
				.price(amount)
				.summary("요약")
				.detailTitle("상세")
				.description("설명")
				.build();
		Order order = Order.create(
				"NC_order-123",
				member,
				"구매자",
				"010-1234-5678",
				"서울시 성동구 성수동",
				null
		);
		order.addItem(product, 1);
		return order;
	}
}
