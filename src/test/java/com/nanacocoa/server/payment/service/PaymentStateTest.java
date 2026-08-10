package com.nanacocoa.server.payment.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderStatus;
import com.nanacocoa.server.payment.entity.Payment;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_PAYMENT_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateTest {
	@Test
	void approvalAndFullCancellationFollowAllowedTransitions() {
		Order order = pendingOrder();
		Payment payment = pendingPayment(order);

		payment.complete("DONE", "카드", LocalDateTime.now());
		payment.startCancellation();
		payment.canceledByUser("CANCELED", LocalDateTime.now());

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
	}

	@Test
	void definitiveApprovalFailureRestoresCreatedOrder() {
		Order order = pendingOrder();
		Payment payment = pendingPayment(order);

		payment.fail("REJECTED", "승인 거절");

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	void compensationCancellationRestoresOrderForRetry() {
		Order order = pendingOrder();
		Payment payment = pendingPayment(order);

		payment.requestCompensation("DB_FAILED", "DB 반영 실패", LocalDateTime.now());
		payment.canceledByCompensation("CANCELED", LocalDateTime.now());

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	void rejectsCancellationBeforeApproval() {
		Payment payment = pendingPayment(pendingOrder());

		assertThatThrownBy(payment::startCancellation)
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(INVALID_PAYMENT_STATUS));
	}

	private Order pendingOrder() {
		Member member = Member.builder()
				.email("buyer@example.com")
				.name("구매자")
				.password("encoded")
				.build();
		Order order = Order.create(
				"NC_order-123",
				member,
				"구매자",
				"010-1234-5678",
				"서울시 성동구 성수동",
				null
		);
		order.startPayment();
		return order;
	}

	private Payment pendingPayment(Order order) {
		return Payment.pending(
				order,
				"payment-key",
				"idem-key",
				"fingerprint",
				"compensation-key",
				38000L,
				LocalDateTime.now().plusSeconds(30)
		);
	}
}
