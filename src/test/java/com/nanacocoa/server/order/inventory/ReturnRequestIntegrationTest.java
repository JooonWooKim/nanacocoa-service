package com.nanacocoa.server.order.inventory;

import com.nanacocoa.server.common.exception.*;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.config.ReturnPolicy;
import com.nanacocoa.server.order.dto.reqeust.*;
import com.nanacocoa.server.order.dto.response.*;
import com.nanacocoa.server.order.entity.*;
import com.nanacocoa.server.order.repository.*;
import com.nanacocoa.server.order.service.*;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.entity.*;
import com.nanacocoa.server.payment.pg.*;
import com.nanacocoa.server.payment.repository.*;
import com.nanacocoa.server.payment.service.*;
import com.nanacocoa.server.products.entity.*;
import com.nanacocoa.server.products.repository.ProductsRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.nanacocoa.server.CsrfRequests.csrf;
import static com.nanacocoa.server.common.exception.ErrorCode.*;
import static com.nanacocoa.server.order.entity.ReturnRequest.Action.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
    "spring.sql.init.mode=never", "payment.reconciliation.enabled=false",
    "shipping.carriers.test.name=테스트 택배", "shipping.carriers.test.tracking-pattern=[0-9]{10}"
})
class ReturnRequestIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String mysql = System.getenv("RETURNS_TEST_DB_URL");
        r.add("spring.datasource.url", () -> mysql == null
            ? "jdbc:h2:mem:returns;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
        r.add("spring.datasource.driver-class-name", () -> mysql == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username", () -> mysql == null ? "sa" : "root");
        r.add("spring.datasource.password", () -> "");
    }
    @Autowired OrderService orders;
    @Autowired ReturnRequestFacade returns;
    @Autowired ReturnRequestService requests;
    @Autowired ReturnPolicy policy;
    @Autowired OrderRepository orderRepo;
    @Autowired ReturnRequestRepository returnRepo;
    @Autowired ReturnRequestEventRepository events;
    @Autowired MemberRepository members;
    @Autowired ProductsRepository products;
    @Autowired ShipmentRepository shipments;
    @Autowired ShippingService shipping;
    @Autowired PaymentTransactionalService transactions;
    @Autowired PaymentRepository payments;
    @Autowired PaymentCancellationRepository cancellations;
    @Autowired PaymentOperationsService operations;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean TossPaymentsClient provider;
    UserDetailsImpl buyer, admin;
    record Paid(OrderResponse order, Products product, PaymentTransactionalService.ApprovalWork payment) {}
    @BeforeEach void setup() {
        buyer = identity(false); admin = identity(true);
        policy.setEnabled(true); policy.setVersion("test-only-v1"); policy.setApprovedBy("test-operator");
        policy.setApprovedAt("2026-01-01T00:00:00Z"); policy.setGuidance("가상 검증 정책. 실제 운영 승인이 아닙니다.");
        policy.setWindowDays(14); policy.setAllowInTransit(true); policy.setAllowReapply(true);
        policy.setRefundAfterFailedInspection(true);
    }
    UserDetailsImpl identity(boolean isAdmin) {
        var member = Member.builder().email(UUID.randomUUID()+"@example.test").name("가상 회원").password("encoded").build();
        member.setAdmin(isAdmin); member = members.saveAndFlush(member);
        return new UserDetailsImpl(member, member.getEmail());
    }
    Paid paid() {
        var product = Products.builder().name("return-"+UUID.randomUUID()).price(41000L)
            .summary("summary").detailTitle("detail").description("description").build();
        product.updateInventory(1, SaleStatus.ON_SALE, 0); product = products.saveAndFlush(product);
        var order = orders.createOrder(new CreateOrderRequest("가상 수령인", "010-0000-0000", "가상 배송지", null,
            List.of(new OrderItemRequest(product.getId(), 1))), UUID.randomUUID().toString(), null, buyer).order();
        var payment = transactions.prepareApproval(new ConfirmPaymentRequest(UUID.randomUUID().toString(), order.getOrderId(), order.getTotalAmount()),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), buyer.getUsername());
        transactions.completeApproval(payment.paymentId(), response(payment, "DONE"));
        return new Paid(order, product, payment);
    }
    TossPaymentResponse response(PaymentTransactionalService.ApprovalWork p, String status) {
        return new TossPaymentResponse(p.paymentKey(), p.orderId(), p.amount(), status, "카드", OffsetDateTime.now(), List.of());
    }
    long submit(Paid p, ReturnRequest.Type type) {
        return returns.create(p.order().getOrderId(), new CreateReturnRequest(UUID.randomUUID(), type, "가상 신청 사유"), buyer).requests().get(0).id();
    }
    ReturnResponse.Detail action(long id, ReturnRequest.Action action) {
        var row = returns.get(id, admin);
        return returns.action(id, new ReturnActionRequest(UUID.randomUUID(), row.revision(), action, "운영 내부 메모",
            action == INSPECT ? true : null, action == INSPECT ? true : null), admin);
    }
    void inspect(long id) { action(id, APPROVE); action(id, INSPECT); }
    void ship(Paid p) {
        shipping.change(p.order().getOrderId(), new ShippingChangeRequest(ShipmentEvent.Action.SHIP, UUID.randomUUID(),
            0L, "test", "0012345678", "가상 출고", null, null), admin);
    }
    MockHttpSession session(UserDetailsImpl user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context); return session;
    }
    void conflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(NanacocoaException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(RETURN_CONFLICT));
    }
    @Test void policyDisabledBlocksNewRequestsAndActionsButAllowsReadsAndOwnedReplay() {
        var p = paid(); policy.setEnabled(false);
        assertThat(returns.ownedView(p.order().getOrderId(), buyer).allowedTypes()).isEmpty();
        assertThatThrownBy(() -> submit(p, ReturnRequest.Type.CANCELLATION)).isInstanceOfSatisfying(NanacocoaException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(RETURN_POLICY_UNAVAILABLE));
        assertThat(returnRepo.findByOrderIdOrderByIdDesc(orderRepo.findByOrderId(p.order().getOrderId()).orElseThrow().getId())).isEmpty();
        policy.setEnabled(true);
        var input = new CreateReturnRequest(UUID.randomUUID(), ReturnRequest.Type.CANCELLATION, "reason");
        long id = returns.create(p.order().getOrderId(), input, buyer).requests().get(0).id();
        policy.setEnabled(false);
        assertThat(returns.create(p.order().getOrderId(), input, buyer).requests()).hasSize(1);
        assertThatThrownBy(() -> action(id, APPROVE)).isInstanceOf(NanacocoaException.class);
        verifyNoInteractions(provider);
    }
    @Test void idempotentSubmissionRejectsChangedPayloadAndSecondActiveRequest() {
        var p = paid(); var key = UUID.randomUUID();
        var input = new CreateReturnRequest(key, ReturnRequest.Type.CANCELLATION, "reason");
        var first = returns.create(p.order().getOrderId(), input, buyer);
        assertThat(returns.create(p.order().getOrderId(), input, buyer).requests().get(0).id()).isEqualTo(first.requests().get(0).id());
        conflict(() -> returns.create(p.order().getOrderId(), new CreateReturnRequest(key, ReturnRequest.Type.CANCELLATION, "changed"), buyer));
        conflict(() -> submit(p, ReturnRequest.Type.CANCELLATION));
    }
    @Test void cancellationHoldsShippingAndRejectionReleasesItWithHistory() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION);
        assertThat(orders.getOrder(p.order().getOrderId(), buyer).getClass()).isNotNull();
        assertThat(orders.getOrder(p.order().getOrderId(), buyer).shipping().status()).isEqualTo(ShippingStatus.ON_HOLD);
        assertThatThrownBy(() -> ship(p)).isInstanceOf(NanacocoaException.class);
        action(id, REJECT); ship(p);
        assertThat(orders.getOrder(p.order().getOrderId(), buyer).shipping().status()).isEqualTo(ShippingStatus.SHIPPED);
    }
    @Test void shippedOrderOnlyPermitsReturnAndOldOrUnknownOrdersAreHeld() {
        var p = paid(); ship(p);
        conflict(() -> submit(p, ReturnRequest.Type.CANCELLATION));
        long id = submit(p, ReturnRequest.Type.RETURN);
        assertThat(returns.get(id, admin).type()).isEqualTo("RETURN");
        var old = paid();
        jdbc.update("update orders set legacy_shipping_review=true where order_id=?", old.order().getOrderId());
        conflict(() -> submit(old, ReturnRequest.Type.CANCELLATION));
        var aged = paid(); ship(aged);
        jdbc.update("update shipments set shipped_at=? where order_id=?", LocalDateTime.now().minusDays(30),
            orderRepo.findByOrderId(aged.order().getOrderId()).orElseThrow().getId());
        conflict(() -> submit(aged, ReturnRequest.Type.RETURN));
    }
    @Test void fullCancellationRefundAndRestockReplayExactlyOnce() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); inspect(id);
        var before = returns.get(id, admin);
        var input = new ReturnActionRequest(UUID.randomUUID(), before.revision(), REFUND, "환불", null, null);
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenReturn(response(p.payment(), "CANCELED"));
        assertThat(returns.action(id, input, admin).refundStatus()).isEqualTo("SUCCESS");
        assertThat(returns.action(id, input, admin).refundStatus()).isEqualTo("SUCCESS");
        assertThat(cancellations.count()).isPositive();
        var restock = returns.get(id, admin);
        var restockInput = new ReturnActionRequest(UUID.randomUUID(), restock.revision(), RESTOCK, "검수 적합", null, null);
        returns.action(id, restockInput, admin); returns.action(id, restockInput, admin);
        assertThat(products.findById(p.product().getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(orders.getOrder(p.order().getOrderId(), buyer).returns().requests().get(0).restockStatus()).isEqualTo("COMPLETED");
        verify(provider, times(1)).cancel(eq(p.payment().paymentKey()), anyString(), anyString());
    }
    @Test void returnCollectionInspectionCanRefundWithoutRestockingDamagedItem() {
        var p = paid(); ship(p); long id = submit(p, ReturnRequest.Type.RETURN);
        action(id, APPROVE); action(id, COLLECT); action(id, RECEIVE);
        var row = returns.get(id, admin);
        returns.action(id, new ReturnActionRequest(UUID.randomUUID(), row.revision(), INSPECT, "파손 검수", false, true), admin);
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenReturn(response(p.payment(), "CANCELED"));
        assertThat(action(id, REFUND).refundStatus()).isEqualTo("SUCCESS");
        conflict(() -> action(id, RESTOCK));
        assertThatThrownBy(() -> orders.restock(p.order().getOrderId(), "우회", admin)).isInstanceOf(NanacocoaException.class);
        assertThat(products.findById(p.product().getId()).orElseThrow().getStockQuantity()).isZero();
    }
    @Test void timeoutPersistsLinkedTransactionAndRecoveryUpdatesCustomerWithoutNewRefund() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); inspect(id);
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenThrow(
            new TossPaymentsException(TossPaymentsException.Kind.UNCERTAIN, "TIMEOUT", "private-provider-error"));
        var row = returns.get(id, admin);
        var input = new ReturnActionRequest(UUID.randomUUID(), row.revision(), REFUND, "환불", null, null);
        var pending = returns.action(id, input, admin);
        assertThat(pending.refundStatus()).isEqualTo("PENDING");
        returns.action(id, input, admin);
        conflict(() -> action(id, REFUND));
        jdbc.update("update payment_cancellations set next_retry_at=? where id=?", LocalDateTime.now().minusMinutes(1), pending.cancellationId());
        when(provider.findByOrderId(p.order().getOrderId())).thenReturn(response(p.payment(), "CANCELED"));
        assertThat(operations.reconcile("CANCELLATION", ""+pending.cancellationId(), admin).pending()).isFalse();
        assertThat(returns.ownedView(p.order().getOrderId(), buyer).requests().get(0).refundStatus()).isEqualTo("SUCCESS");
        verify(provider, times(1)).cancel(eq(p.payment().paymentKey()), anyString(), anyString());
    }
    @Test void failedRefundRetryUsesNewTransactionAndOldActionReplayDoesNotRefundAgain() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); inspect(id);
        var row = returns.get(id, admin);
        var input = new ReturnActionRequest(UUID.randomUUID(), row.revision(), REFUND, "환불", null, null);
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenThrow(
            new TossPaymentsException(TossPaymentsException.Kind.DEFINITIVE, "DECLINED", "private"));
        assertThatThrownBy(() -> returns.action(id, input, admin)).isInstanceOf(NanacocoaException.class);
        assertThat(returns.get(id, admin).refundStatus()).isEqualTo("FAILED");
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenReturn(response(p.payment(), "CANCELED"));
        action(id, REFUND); returns.action(id, input, admin);
        verify(provider, times(2)).cancel(eq(p.payment().paymentKey()), anyString(), anyString());
    }
    @Test void disabledProviderDoesNotRecordRefundIntent() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); inspect(id);
        var before = returns.get(id, admin); when(provider.isDisabled()).thenReturn(true);
        assertThatThrownBy(() -> action(id, REFUND)).isInstanceOfSatisfying(NanacocoaException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PAYMENT_PROVIDER_UNAVAILABLE));
        var after = returns.get(id, admin);
        assertThat(after.revision()).isEqualTo(before.revision());
        assertThat(after.refundStatus()).isEqualTo("NOT_STARTED");
        verify(provider, never()).cancel(anyString(), anyString(), anyString());
    }
    @Test void staleVersionAndChangedActionPayloadCannotMutateHistory() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION);
        var input = new ReturnActionRequest(UUID.randomUUID(), 0L, APPROVE, "승인", null, null);
        returns.action(id, input, admin); returns.action(id, input, admin);
        conflict(() -> returns.action(id, new ReturnActionRequest(input.requestKey(), 0L, APPROVE, "다른 내용", null, null), admin));
        conflict(() -> returns.action(id, new ReturnActionRequest(UUID.randomUUID(), 0L, REJECT, "반려", null, null), admin));
        assertThat(returns.get(id, admin).history()).hasSize(1);
    }
    @Test void guardsOwnershipAdminCsrfPrivacyAndDirectCancellation() throws Exception {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); action(id, APPROVE);
        String buyerApi = "/api/orders/"+p.order().getOrderId()+"/return-requests";
        mvc.perform(get(buyerApi)).andExpect(status().isUnauthorized());
        mvc.perform(get(buyerApi).session(session(identity(false)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/return-requests").session(session(buyer))).andExpect(status().isForbidden());
        var input = new ReturnActionRequest(UUID.randomUUID(), 1L, INSPECT, "secret", true, true);
        String adminApi = "/api/admin/return-requests/"+id+"/actions";
        mvc.perform(post(adminApi).session(session(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isForbidden());
        mvc.perform(post(adminApi).session(session(admin)).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isOk());
        var result = mvc.perform(get(buyerApi).session(session(buyer))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("운영 내부 메모", "secret", p.payment().paymentKey(), buyer.getUsername(), "fingerprint", "operatorId");
        mvc.perform(post("/api/payments/"+p.payment().paymentId()+"/cancel").session(session(buyer)).with(csrf(mvc))
            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"cancelReason\":\"우회\"}")).andExpect(status().isConflict());
        jdbc.update("update members set is_admin=false where email=?", admin.getUsername());
        mvc.perform(get("/api/admin/return-requests/"+id).session(session(admin))).andExpect(status().isForbidden());
        verify(provider, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(provider, never()).cancel(anyString(), anyString(), anyString());
        verify(provider, never()).findByOrderId(anyString());
    }
    @Test void concurrentSubmissionsCreateOneActiveRequest() throws Exception {
        var p = paid(); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i=0; i<2; i++) futures.add(pool.submit(() -> {
                start.await();
                try { submit(p, ReturnRequest.Type.CANCELLATION); successes.incrementAndGet(); }
                catch (NanacocoaException e) { assertThat(e.getErrorCode()).isEqualTo(RETURN_CONFLICT); }
                return null;
            }));
            start.countDown(); for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(returns.ownedView(p.order().getOrderId(), buyer).requests()).hasSize(1);
    }
    @Test void shipmentAndCancellationRaceCannotBothCommit() throws Exception {
        var p = paid(); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Future<?> shippingJob = pool.submit(() -> { start.await(); try { ship(p); } catch (NanacocoaException expected) {} return null; });
            Future<?> cancelJob = pool.submit(() -> { start.await(); try { submit(p, ReturnRequest.Type.CANCELLATION); } catch (NanacocoaException expected) {} return null; });
            start.countDown(); shippingJob.get(20, TimeUnit.SECONDS); cancelJob.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        boolean shipped = shipments.findByOrderId(orderRepo.findByOrderId(p.order().getOrderId()).orElseThrow().getId()).isPresent();
        boolean requested = !returns.ownedView(p.order().getOrderId(), buyer).requests().isEmpty();
        assertThat(shipped ^ requested).isTrue();
    }
    @Test void concurrentRefundAndRestockOnlyExecuteOnce() throws Exception {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); inspect(id);
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenAnswer(i -> { Thread.sleep(50); return response(p.payment(), "CANCELED"); });
        var row = returns.get(id, admin);
        var input = new ReturnActionRequest(UUID.randomUUID(), row.revision(), REFUND, "환불", null, null);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> returns.action(id, input, admin)); var b = pool.submit(() -> returns.action(id, input, admin));
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        verify(provider, times(1)).cancel(eq(p.payment().paymentKey()), anyString(), anyString());
        row = returns.get(id, admin);
        var restock = new ReturnActionRequest(UUID.randomUUID(), row.revision(), RESTOCK, "검수", null, null);
        pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> returns.action(id, restock, admin)); var b = pool.submit(() -> returns.action(id, restock, admin));
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(products.findById(p.product().getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test void rejectsMalformedFiltersAndRequestBodies() throws Exception {
        var p = paid(); var s = session(admin);
        for (String query : List.of("page=-1", "page=abc", "page=99999999999999999", "type=OTHER", "status=OTHER")) {
            mvc.perform(get("/api/admin/return-requests?"+query).session(s)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/return-requests").param("orderId", " ").session(s)).andExpect(status().isBadRequest());
        for (String id : List.of("0", "-1", "abc", "99999999999999999999"))
            mvc.perform(get("/api/admin/return-requests/"+id).session(s)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/return-requests/9223372036854775807").session(s)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/"+p.order().getOrderId()+"/return-requests").session(session(buyer)).with(csrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OTHER\",\"reason\":\"x\"}")).andExpect(status().isBadRequest());
    }
    @Test void inspectionFailurePolicyAndMissingApprovalMetadataFailClosed() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); action(id, APPROVE);
        policy.setRefundAfterFailedInspection(false);
        var row = returns.get(id, admin);
        conflict(() -> returns.action(id, new ReturnActionRequest(UUID.randomUUID(), row.revision(), INSPECT, "파손", false, true), admin));
        assertThat(returns.get(id, admin).revision()).isEqualTo(row.revision());
        policy.setApprovedAt("");
        assertThat(policy.available()).isFalse();
        policy.setApprovedAt("2099-01-01T00:00:00Z");
        assertThat(policy.available()).isFalse();
        policy.setApprovedAt("2026-01-01T00:00:00Z"); policy.setWindowDays(0);
        assertThat(policy.available()).isFalse();
        policy.setWindowDays(14); policy.setAllowReapply(null);
        assertThat(policy.available()).isFalse();
    }
    @Test void mismatchedProviderResultRemainsPendingUntilValidRecovery() {
        var p = paid(); long id = submit(p, ReturnRequest.Type.CANCELLATION); inspect(id);
        when(provider.cancel(eq(p.payment().paymentKey()), anyString(), anyString())).thenReturn(
            new TossPaymentResponse("wrong-key", p.order().getOrderId(), p.payment().amount(), "CANCELED", null, null, List.of()));
        assertThat(action(id, REFUND).refundStatus()).isEqualTo("PENDING");
        assertThat(orderRepo.findByOrderId(p.order().getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(returns.get(id, admin).actions()).doesNotContain("RESTOCK", "REFUND");
        assertThat(products.findById(p.product().getId()).orElseThrow().getStockQuantity()).isZero();
    }
    @Test void concurrentIdenticalSubmissionsReplayOneRequest() throws Exception {
        var p = paid(); var input = new CreateReturnRequest(UUID.randomUUID(), ReturnRequest.Type.CANCELLATION, "동일 제출");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> returns.create(p.order().getOrderId(), input, buyer));
            var b = pool.submit(() -> returns.create(p.order().getOrderId(), input, buyer));
            assertThat(a.get(20, TimeUnit.SECONDS).requests().get(0).id())
                .isEqualTo(b.get(20, TimeUnit.SECONDS).requests().get(0).id());
        } finally { pool.shutdownNow(); }
        assertThat(returns.ownedView(p.order().getOrderId(), buyer).requests()).hasSize(1);
    }
}
