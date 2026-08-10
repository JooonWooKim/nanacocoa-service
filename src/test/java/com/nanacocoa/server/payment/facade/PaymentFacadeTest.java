package com.nanacocoa.server.payment.facade;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.payment.dto.reqeust.CancelPaymentRequest;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.dto.response.PaymentResponse;
import com.nanacocoa.server.payment.entity.CancellationStatus;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.lock.OrderPaymentLock;
import com.nanacocoa.server.payment.pg.TossCancelResponse;
import com.nanacocoa.server.payment.pg.TossPaymentResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.pg.TossPaymentsException;
import com.nanacocoa.server.payment.service.PaymentFingerprintService;
import com.nanacocoa.server.payment.service.PaymentTransactionalService;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.ApprovalWork;
import com.nanacocoa.server.payment.service.PaymentTransactionalService.CancellationWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

import static com.nanacocoa.server.common.exception.ErrorCode.PAYMENT_APPROVAL_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {
	@Mock
	private PaymentTransactionalService transactionalService;
	@Mock
	private PaymentFingerprintService fingerprintService;
	@Mock
	private TossPaymentsClient tossPaymentsClient;

	private PaymentFacade paymentFacade;
	private UserDetailsImpl userDetails;
	private ConfirmPaymentRequest request;

	@BeforeEach
	void setUp() {
		OrderPaymentLock directLock = new OrderPaymentLock() {
			@Override
			public <T> T withOrderLock(String orderId, Supplier<T> action) {
				return action.get();
			}
		};
		paymentFacade = new PaymentFacade(directLock, transactionalService, fingerprintService, tossPaymentsClient);
		Member member = Member.builder()
				.email("buyer@example.com")
				.name("구매자")
				.password("encoded")
				.build();
		userDetails = new UserDetailsImpl(member, member.getEmail());
		request = new ConfirmPaymentRequest("payment-key", "NC_order-123", 38000L);
		lenient().when(fingerprintService.approval(userDetails.getUsername(), request)).thenReturn("fingerprint");
	}

	@Test
	void confirmsPaymentAndCompletesDatabaseState() {
		ApprovalWork work = approvalWork(PaymentStatus.PENDING, false, pendingResponse());
		TossPaymentResponse pgResponse = doneResponse();
		PaymentResponse completed = successResponse();
		when(transactionalService.prepareApproval(request, "idem-key", "fingerprint", userDetails.getUsername()))
				.thenReturn(work);
		when(tossPaymentsClient.confirm("payment-key", "NC_order-123", 38000L, "idem-key"))
				.thenReturn(pgResponse);
		when(transactionalService.completeApproval(10L, pgResponse)).thenReturn(completed);

		PaymentResponse response = paymentFacade.confirm(request, "idem-key", userDetails);

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		verify(tossPaymentsClient).confirm("payment-key", "NC_order-123", 38000L, "idem-key");
	}

	@Test
	void idempotentSuccessReplayDoesNotCallProvider() {
		PaymentResponse completed = successResponse();
		when(transactionalService.prepareApproval(request, "idem-key", "fingerprint", userDetails.getUsername()))
				.thenReturn(approvalWork(PaymentStatus.SUCCESS, true, completed));

		PaymentResponse response = paymentFacade.confirm(request, "idem-key", userDetails);

		assertThat(response).isSameAs(completed);
		verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString());
	}

	@Test
	void timeoutKeepsPaymentPending() {
		ApprovalWork work = approvalWork(PaymentStatus.PENDING, false, pendingResponse());
		PaymentResponse pending = pendingResponse();
		when(transactionalService.prepareApproval(request, "idem-key", "fingerprint", userDetails.getUsername()))
				.thenReturn(work);
		when(tossPaymentsClient.confirm("payment-key", "NC_order-123", 38000L, "idem-key"))
				.thenThrow(new TossPaymentsException(TossPaymentsException.Kind.UNCERTAIN, "PG_TIMEOUT", "timeout"));
		when(transactionalService.recordUncertainApproval(10L, "PG_TIMEOUT", "timeout")).thenReturn(pending);

		PaymentResponse response = paymentFacade.confirm(request, "idem-key", userDetails);

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
		verify(transactionalService, never()).failApproval(10L, "PG_TIMEOUT", "timeout");
	}

	@Test
	void definitiveProviderFailureRollsOrderBack() {
		ApprovalWork work = approvalWork(PaymentStatus.PENDING, false, pendingResponse());
		when(transactionalService.prepareApproval(request, "idem-key", "fingerprint", userDetails.getUsername()))
				.thenReturn(work);
		when(tossPaymentsClient.confirm("payment-key", "NC_order-123", 38000L, "idem-key"))
				.thenThrow(new TossPaymentsException(TossPaymentsException.Kind.DEFINITIVE, "REJECTED", "declined"));

		assertThatThrownBy(() -> paymentFacade.confirm(request, "idem-key", userDetails))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(PAYMENT_APPROVAL_FAILED));

		verify(transactionalService).failApproval(10L, "REJECTED", "declined");
	}

	@Test
	void databaseCompletionFailureTriggersIdempotentCompensation() {
		ApprovalWork work = approvalWork(PaymentStatus.PENDING, false, pendingResponse());
		TossPaymentResponse approved = doneResponse();
		TossPaymentResponse canceled = canceledResponse();
		when(transactionalService.prepareApproval(request, "idem-key", "fingerprint", userDetails.getUsername()))
				.thenReturn(work);
		when(tossPaymentsClient.confirm("payment-key", "NC_order-123", 38000L, "idem-key"))
				.thenReturn(approved);
		when(transactionalService.completeApproval(10L, approved))
				.thenThrow(new DataAccessResourceFailureException("db unavailable"));
		when(tossPaymentsClient.cancel("payment-key", "내부 결제 확정 실패에 따른 자동 보상 취소", "compensation-key"))
				.thenReturn(canceled);

		assertThatThrownBy(() -> paymentFacade.confirm(request, "idem-key", userDetails))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(PAYMENT_APPROVAL_FAILED));

		verify(transactionalService).requestCompensation(10L, "INTERNAL_COMPLETION_FAILED", "db unavailable");
		verify(transactionalService).completeCompensation(10L, canceled);
	}

	@Test
	void cancelsSuccessfulPayment() {
		CancelPaymentRequest cancelRequest = new CancelPaymentRequest("고객 요청");
		when(transactionalService.getOwnedOrderId(10L, userDetails.getUsername())).thenReturn("NC_order-123");
		when(fingerprintService.cancellation(userDetails.getUsername(), 10L, "고객 요청"))
				.thenReturn("cancel-fingerprint");
		CancellationWork work = new CancellationWork(
				20L, 10L, "payment-key", "NC_order-123", "cancel-idem", "고객 요청",
				CancellationStatus.PENDING, false,
				new PaymentResponse(10L, "NC_order-123", 38000L, PaymentStatus.CANCEL_PENDING, "카드", null, LocalDateTime.now(), null)
		);
		TossPaymentResponse canceled = canceledResponse();
		PaymentResponse completed = new PaymentResponse(
				10L, "NC_order-123", 38000L, PaymentStatus.CANCELED, "카드", null, LocalDateTime.now(), LocalDateTime.now()
		);
		when(transactionalService.prepareCancellation(10L, "고객 요청", "cancel-idem", "cancel-fingerprint", userDetails.getUsername()))
				.thenReturn(work);
		when(tossPaymentsClient.cancel("payment-key", "고객 요청", "cancel-idem")).thenReturn(canceled);
		when(transactionalService.completeCancellation(20L, canceled)).thenReturn(completed);

		PaymentResponse response = paymentFacade.cancel(10L, cancelRequest, "cancel-idem", userDetails);

		assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELED);
	}

	private ApprovalWork approvalWork(PaymentStatus status, boolean replay, PaymentResponse response) {
		return new ApprovalWork(
				10L,
				"payment-key",
				"NC_order-123",
				38000L,
				"idem-key",
				"compensation-key",
				status,
				status == PaymentStatus.PENDING ? PaymentRecoveryAction.VERIFY_APPROVAL : PaymentRecoveryAction.NONE,
				LocalDateTime.now(),
				replay,
				response
		);
	}

	private PaymentResponse pendingResponse() {
		return new PaymentResponse(10L, "NC_order-123", 38000L, PaymentStatus.PENDING, null, null, null, null);
	}

	private PaymentResponse successResponse() {
		return new PaymentResponse(10L, "NC_order-123", 38000L, PaymentStatus.SUCCESS, "카드", null, LocalDateTime.now(), null);
	}

	private TossPaymentResponse doneResponse() {
		return new TossPaymentResponse(
				"payment-key", "NC_order-123", 38000L, "DONE", "카드", OffsetDateTime.now(), List.of()
		);
	}

	private TossPaymentResponse canceledResponse() {
		return new TossPaymentResponse(
				"payment-key",
				"NC_order-123",
				38000L,
				"CANCELED",
				"카드",
				OffsetDateTime.now(),
				List.of(new TossCancelResponse("cancel-tx", "DONE", OffsetDateTime.now()))
		);
	}
}
