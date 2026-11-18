package com.nanacocoa.server.order.inventory;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.dto.reqeust.*;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.order.entity.*;
import com.nanacocoa.server.order.entity.ShipmentEvent.Action;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.order.service.*;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.entity.PaymentRecoveryAction;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.pg.TossPaymentResponse;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.nanacocoa.server.payment.service.PaymentRecoveryService;
import com.nanacocoa.server.payment.service.PaymentTransactionalService;
import com.nanacocoa.server.products.dto.reqeust.UpdateInventoryRequest;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.entity.SaleStatus;
import com.nanacocoa.server.products.repository.ProductsRepository;
import com.nanacocoa.server.products.service.ProductsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.nanacocoa.server.CsrfRequests.csrf;
import static com.nanacocoa.server.common.exception.ErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 같은 테스트를 임시 MySQL과 H2에서 실행합니다. 외부 PG는 호출하지 않습니다. */
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
    "spring.flyway.enabled=true", "spring.sql.init.mode=never",
    "shipping.carriers.test.name=테스트 택배",
    "shipping.carriers.test.tracking-pattern=[0-9]{10}",
    "shipping.carriers.test.tracking-url=https://carrier.example.test/track/{tracking}"
})
@org.springframework.context.annotation.Import(PaymentOperationsIntegrationTest.TestLocks.class)
class PaymentOperationsIntegrationTest {
    // 기본 test 프로필의 무잠금 어댑터 대신 운영 주문 락 계약을 재현한다.
    @org.springframework.boot.test.context.TestConfiguration
    static class TestLocks {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        com.nanacocoa.server.payment.lock.OrderPaymentLock operationTestLock() {
            return new com.nanacocoa.server.payment.lock.OrderPaymentLock() {
                final ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock> locks = new ConcurrentHashMap<>();
                public <T> T withOrderLock(String id, java.util.function.Supplier<T> action) {
                    var lock = locks.computeIfAbsent(id, key -> new java.util.concurrent.locks.ReentrantLock());
                    lock.lock(); try { return action.get(); } finally { lock.unlock(); }
                }
            };
        }
    }
    @org.junit.jupiter.api.AfterEach void parkOtherPendingWork() {
        jdbc.update("update payments set next_retry_at = ? where status = 'PENDING'", LocalDateTime.now().plusDays(1));
        jdbc.update("update payment_cancellations set next_retry_at = ? where status = 'PENDING'", LocalDateTime.now().plusDays(1));
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String mysql = System.getenv("PAYMENT_OPERATIONS_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> mysql == null
            ? "jdbc:h2:mem:paymentoperations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
        registry.add("spring.datasource.driver-class-name", () -> mysql == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> mysql == null ? "sa" : "root");
        registry.add("spring.datasource.password", () -> "");
    }
    @Autowired ProductsRepository products;
    @Autowired OrderRepository orders;
    @Autowired MemberRepository members;
    @Autowired OrderService orderService;
    @Autowired ProductsService productsService;
    @Autowired OrderExpirationService expiration;
    @Autowired PaymentTransactionalService payments;
    @Autowired PaymentRecoveryService paymentRecovery;
    @MockitoBean TossPaymentsClient tossPaymentsClient;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.nanacocoa.server.payment.repository.PaymentRepository paymentRepository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ShippingService shipping;
    @Autowired PasswordEncoder passwordEncoder;
    UserDetailsImpl buyer;
    UserDetailsImpl admin;

    @BeforeEach void setup() { buyer = identity(false); admin = identity(true); }
    UserDetailsImpl identity(boolean administrator) {
        Member member = Member.builder().email(UUID.randomUUID()+"@example.test").name("테스트").password("encoded").build();
        member.setAdmin(administrator);
        member = members.saveAndFlush(member);
        return new UserDetailsImpl(member, member.getEmail());
    }
    Products product(long quantity) {
        Products product = Products.builder().name("stock-"+UUID.randomUUID()).price(41000L)
            .summary("요약").detailTitle("상세").description("설명").build();
        product.updateInventory(quantity, SaleStatus.ON_SALE, 0);
        return products.saveAndFlush(product);
    }
    OrderResponse order(Products product, int quantity) {
        return submitNewOrder(request(List.of(new OrderItemRequest(product.getId(), quantity))), buyer);
    }
    OrderResponse submitNewOrder(CreateOrderRequest request, UserDetailsImpl owner) {
        return orderService.createOrder(request, UUID.randomUUID().toString(), null, owner).order();
    }
    CreateOrderRequest request(List<OrderItemRequest> items) {
        return new CreateOrderRequest("테스트", "010-0000-0000", "테스트 배송지", null, items);
    }
    void stock(Products product, long available, long reserved) {
        Products actual = products.findById(product.getId()).orElseThrow();
        assertThat(actual.getStockQuantity()).isEqualTo(available);
        assertThat(actual.getReservedQuantity()).isEqualTo(reserved);
    }
    void makeExpired(OrderResponse order) {
        jdbc.update("update orders set reservation_expires_at = ? where order_id = ?", LocalDateTime.now().minusMinutes(1), order.getOrderId());
    }
    PaymentTransactionalService.ApprovalWork approval(OrderResponse order) {
        return payments.prepareApproval(new ConfirmPaymentRequest(UUID.randomUUID().toString(), order.getOrderId(), order.getTotalAmount()),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), buyer.getUsername());
    }
    TossPaymentResponse response(PaymentTransactionalService.ApprovalWork work, String status) {
        return new TossPaymentResponse(work.paymentKey(), work.orderId(), work.amount(), status, "카드", OffsetDateTime.now(), List.of());
    }


    @Autowired com.nanacocoa.server.payment.service.PaymentOperationsService operations;
    static final String API = "/api/admin/payment-operations";
    void due(long id) { jdbc.update("update payments set next_retry_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), id); }
    MockHttpSession session(UserDetailsImpl identity) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, identity.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context); return session;
    }
    String path(long id) { return API + "/APPROVAL/" + id; }
    @Test void protectsEveryBoundaryAndRechecksCurrentAdmin() throws Exception {
        var work = approval(order(product(1), 1));
        var operator = session(admin);
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).session(session(buyer))).andExpect(status().isForbidden());
        mvc.perform(get(path(work.paymentId())).session(session(buyer))).andExpect(status().isForbidden());
        mvc.perform(post(path(work.paymentId()) + "/reconcile").session(operator)).andExpect(status().isForbidden());
        mvc.perform(post(path(work.paymentId()) + "/reconcile").session(operator).header("X-CSRF-TOKEN", "bad"))
            .andExpect(status().isForbidden());
        mvc.perform(post(path(work.paymentId()) + "/reconcile").session(session(buyer)).with(csrf(mvc)))
            .andExpect(status().isForbidden());
        jdbc.update("update members set is_admin = false where email = ?", admin.getUsername());
        mvc.perform(get(path(work.paymentId())).session(operator)).andExpect(status().isForbidden());
        mvc.perform(post(path(work.paymentId()) + "/reconcile").session(operator).with(csrf(mvc)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(tossPaymentsClient);
    }
    @Test void rejectsInvalidQueriesAndIdentifiers() throws Exception {
        var operator = session(admin);
        for (String query : List.of("kind=OTHER", "size=0", "size=101", "page=-1", "page=abc")) {
            mvc.perform(get(API + "?" + query).session(operator)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(API).param("orderId", " ").session(operator)).andExpect(status().isBadRequest());
        for (String suffix : List.of("OTHER/1", "APPROVAL/0", "APPROVAL/abc", "APPROVAL/9999999999999999999999")) {
            mvc.perform(get(API + "/" + suffix).session(operator)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path(Long.MAX_VALUE)).session(operator)).andExpect(status().isNotFound());
    }
    @Test void listsFutureWorkAndSanitizesProviderErrors() throws Exception {
        var work = approval(order(product(1), 1));
        jdbc.update("update payments set failure_code = ?, failure_message = ? where id = ?", "secret-marker", "private-error", work.paymentId());
        var result = mvc.perform(get(API).param("orderId", work.orderId()).session(session(admin)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].canReconcile").value(false))
            .andExpect(jsonPath("$.data.items[0].errorCategory").value("RECHECK_REQUIRED")).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(work.paymentKey(), work.approvalIdempotencyKey(), "private-error", "secret-marker", buyer.getUsername(), "fingerprint", "failureCode");
        mvc.perform(get(path(work.paymentId())).session(session(admin)))
            .andExpect(jsonPath("$.data.nextRetryAt").isNotEmpty());
    }
    @Test void pagesApprovalAndCancellationWithSameIdWithoutCollision() {
        var approval = approval(order(product(1), 1));
        var cancelWork = approval(order(product(1), 1));
        payments.completeApproval(cancelWork.paymentId(), response(cancelWork, "DONE"));
        var cancel = payments.prepareCancellation(cancelWork.paymentId(), "private-reason", UUID.randomUUID().toString(), "fp", buyer.getUsername());
        // 별도 테이블의 동일 ID도 type으로 구분한다.
        jdbc.update("update payment_cancellations set id = ? where id = ?", approval.paymentId(), cancel.cancellationId());
        LocalDateTime sameTime = LocalDateTime.now().minusHours(1);
        jdbc.update("update payments set created_at = ? where id = ?", sameTime, approval.paymentId());
        jdbc.update("update payment_cancellations set created_at = ? where id = ?", sameTime, approval.paymentId());
        var first = operations.list(null, null, "0", "1", admin);
        assertThat(first.items().get(0).type()).isEqualTo("APPROVAL");
        var second = operations.list(null, null, "1", "1", admin);
        assertThat(second.items().get(0).type()).isEqualTo("CANCELLATION");
        assertThat(second.totalElements()).isEqualTo(first.totalElements());
        assertThat(operations.get("CANCELLATION", ""+approval.paymentId(), admin).orderId()).isEqualTo(cancelWork.orderId());
        assertThat(operations.list("CANCELLATION", cancelWork.orderId(), "0", "20", admin).items()).hasSize(1);
    }
    @Test void recoversApprovalViaHttpAndReplaysCompletedWithoutPg() throws Exception {
        var work = approval(order(product(1), 1)); due(work.paymentId());
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenReturn(response(work, "DONE"));
        var operator = session(admin);
        for (int i = 0; i < 2; i++) mvc.perform(post(path(work.paymentId()) + "/reconcile").session(operator).with(csrf(mvc)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.pending").value(false))
            .andExpect(jsonPath("$.data.stockState").value("SOLD"));
        verify(tossPaymentsClient, times(1)).findByOrderId(work.orderId());
        assertThat(operations.list(null, work.orderId(), "0", "20", admin).items()).isEmpty();
    }
    @Test void defersFutureAndMissingRetryTimeAndBlocksDisabledProvider() throws Exception {
        var work = approval(order(product(1), 1)); var operator = session(admin);
        mvc.perform(post(path(work.paymentId())+"/reconcile").session(operator).with(csrf(mvc))).andExpect(status().isConflict());
        jdbc.update("update payments set next_retry_at = null where id = ?", work.paymentId());
        mvc.perform(post(path(work.paymentId())+"/reconcile").session(operator).with(csrf(mvc))).andExpect(status().isConflict());
        due(work.paymentId()); when(tossPaymentsClient.isDisabled()).thenReturn(true);
        mvc.perform(post(path(work.paymentId())+"/reconcile").session(operator).with(csrf(mvc))).andExpect(status().isServiceUnavailable());
        mvc.perform(get(path(work.paymentId())).session(operator)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.canReconcile").value(false));
        verify(tossPaymentsClient, times(0)).findByOrderId(anyString());
    }
    @Test void timeoutKeepsReservationAndReturnsAccepted() throws Exception {
        var product = product(1); var work = approval(order(product, 1)); due(work.paymentId());
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenThrow(new com.nanacocoa.server.payment.pg.TossPaymentsException(
            com.nanacocoa.server.payment.pg.TossPaymentsException.Kind.UNCERTAIN, "TIMEOUT", "private-response"));
        mvc.perform(post(path(work.paymentId())+"/reconcile").session(session(admin)).with(csrf(mvc)))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.retryCount").value(1));
        stock(product, 0, 1);
    }
    @Test void validatesCancellationLookupBeforeIssuingRefund() {
        var product = product(1); var work = approval(order(product, 1));
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        var cancel = payments.prepareCancellation(work.paymentId(), "reason", UUID.randomUUID().toString(), "fp", buyer.getUsername());
        jdbc.update("update payment_cancellations set next_retry_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), cancel.cancellationId());
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenReturn(new TossPaymentResponse(work.paymentKey(), "wrong-order", work.amount(), "CANCELED", null, null, List.of()));
        var result = operations.reconcile("CANCELLATION", ""+cancel.cancellationId(), admin);
        assertThat(result.pending()).isTrue(); assertThat(result.errorCategory()).isEqualTo("MISMATCH");
        verify(tossPaymentsClient, times(0)).cancel(anyString(), anyString(), anyString());
        assertThat(paymentRepository.findById(work.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCEL_PENDING);
        stock(product, 0, 0);
    }
    @Test void cancellationAndCompensationCompleteWithOriginalKeys() {
        var work = approval(order(product(1), 1));
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        var cancel = payments.prepareCancellation(work.paymentId(), "reason", UUID.randomUUID().toString(), "fp", buyer.getUsername());
        jdbc.update("update payment_cancellations set next_retry_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), cancel.cancellationId());
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenReturn(response(work, "DONE"));
        when(tossPaymentsClient.cancel(work.paymentKey(), "reason", cancel.idempotencyKey())).thenReturn(response(work, "CANCELED"));
        assertThat(operations.reconcile("CANCELLATION", ""+cancel.cancellationId(), admin).pending()).isFalse();
        assertThat(operations.reconcile("CANCELLATION", ""+cancel.cancellationId(), admin).pending()).isFalse();
        verify(tossPaymentsClient, times(1)).cancel(work.paymentKey(), "reason", cancel.idempotencyKey());
        var compensation = approval(order(product(1), 1));
        payments.requestCompensation(compensation.paymentId(), "TEST", "private"); due(compensation.paymentId());
        when(tossPaymentsClient.findByOrderId(compensation.orderId())).thenReturn(response(compensation, "DONE"));
        when(tossPaymentsClient.cancel(eq(compensation.paymentKey()), anyString(), eq(compensation.compensationIdempotencyKey())))
            .thenReturn(response(compensation, "CANCELED"));
        assertThat(operations.reconcile("APPROVAL", ""+compensation.paymentId(), admin).pending()).isFalse();
        assertThat(orders.findByOrderId(compensation.orderId()).orElseThrow().getStockState()).isEqualTo(StockState.RELEASED);
    }
    @Test void concurrentManualAndScheduledRecoveryOnlyConfirmsOnce() throws Exception {
        var product = product(1); var work = approval(order(product, 1)); due(work.paymentId());
        var calls = new AtomicInteger();
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenAnswer(invocation -> { calls.incrementAndGet(); Thread.sleep(100); return response(work, "DONE"); });
        var executor = Executors.newFixedThreadPool(3);
        var start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i=0; i<2; i++) futures.add(executor.submit(() -> { start.await(); operations.reconcile("APPROVAL", ""+work.paymentId(), admin); return null; }));
            futures.add(executor.submit(() -> { start.await(); paymentRecovery.reconcileDueApprovals(100); return null; }));
            start.countDown(); for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(calls.get()).isEqualTo(1); stock(product, 0, 0);
        assertThat(orders.findByOrderId(work.orderId()).orElseThrow().getStockState()).isEqualTo(StockState.SOLD);
    }

    @Test void cancellationTimeoutAndDefinitiveFailureDoNotRestock() {
        var product = product(1); var work = approval(order(product, 1));
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        var cancel = payments.prepareCancellation(work.paymentId(), "reason", UUID.randomUUID().toString(), "fp", buyer.getUsername());
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenReturn(response(work, "DONE"));
        when(tossPaymentsClient.cancel(work.paymentKey(), "reason", cancel.idempotencyKey()))
            .thenThrow(new com.nanacocoa.server.payment.pg.TossPaymentsException(com.nanacocoa.server.payment.pg.TossPaymentsException.Kind.UNCERTAIN, "TIMEOUT", "private"))
            .thenThrow(new com.nanacocoa.server.payment.pg.TossPaymentsException(com.nanacocoa.server.payment.pg.TossPaymentsException.Kind.DEFINITIVE, "DECLINED", "private"));
        for (int i=0; i<2; i++) {
            jdbc.update("update payment_cancellations set next_retry_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), cancel.cancellationId());
            var result = operations.reconcile("CANCELLATION", ""+cancel.cancellationId(), admin);
            assertThat(result.pending()).isEqualTo(i == 0);
        }
        assertThat(paymentRepository.findById(work.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        stock(product, 0, 0);
    }
    @Test void cancellationCompletionRejectsMismatchedProviderIdentity() {
        for (int variant=0; variant<3; variant++) {
            var work = approval(order(product(1), 1));
            payments.completeApproval(work.paymentId(), response(work, "DONE"));
            var cancel = payments.prepareCancellation(work.paymentId(), "reason", UUID.randomUUID().toString(), "fp", buyer.getUsername());
            var mismatch = new TossPaymentResponse(variant == 0 ? "wrong-key" : work.paymentKey(),
                variant == 1 ? "wrong-order" : work.orderId(), variant == 2 ? work.amount()+1 : work.amount(), "CANCELED", null, null, List.of());
            assertThatThrownBy(() -> payments.completeCancellation(cancel.cancellationId(), mismatch)).isInstanceOf(NanacocoaException.class);
            assertThat(paymentRepository.findById(work.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCEL_PENDING);
        }
    }
    @Test void repeatedConcurrentTimeoutDoesNotBypassNewRetryTime() throws Exception {
        var product = product(1); var work = approval(order(product, 1)); due(work.paymentId());
        var calls = new AtomicInteger();
        when(tossPaymentsClient.findByOrderId(work.orderId())).thenAnswer(invocation -> {
            calls.incrementAndGet(); Thread.sleep(50);
            throw new com.nanacocoa.server.payment.pg.TossPaymentsException(com.nanacocoa.server.payment.pg.TossPaymentsException.Kind.UNCERTAIN, "TIMEOUT", "private");
        });
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var manual = executor.submit(() -> { start.await();
                try { operations.reconcile("APPROVAL", ""+work.paymentId(), admin); }
                catch (NanacocoaException e) { assertThat(e.getErrorCode()).isEqualTo(PAYMENT_RECOVERY_DEFERRED); }
                return null;
            });
            var batch = executor.submit(() -> { start.await(); paymentRecovery.reconcileDueApprovals(100); return null; });
            start.countDown(); manual.get(20, TimeUnit.SECONDS); batch.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(calls.get()).isEqualTo(1); stock(product, 0, 1);
        assertThat(paymentRepository.findById(work.paymentId()).orElseThrow().getRetryCount()).isEqualTo(1);
    }
}
