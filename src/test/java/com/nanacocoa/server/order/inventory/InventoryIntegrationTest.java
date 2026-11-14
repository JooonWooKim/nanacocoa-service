package com.nanacocoa.server.order.inventory;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.dto.reqeust.*;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.order.entity.*;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import static com.nanacocoa.server.common.exception.ErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

/** 같은 테스트를 임시 MySQL과 H2에서 실행합니다. 외부 PG는 호출하지 않습니다. */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
    "spring.flyway.enabled=true", "spring.sql.init.mode=never"
})
class InventoryIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String mysql = System.getenv("INVENTORY_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> mysql == null
            ? "jdbc:h2:mem:inventory;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
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
        return orderService.createOrder(request(List.of(new OrderItemRequest(product.getId(), quantity))), buyer);
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

    @Test void lastUnitCanOnlyBeReservedOnceAcrossConcurrentTransactions() throws Exception {
        Products product = product(1);
        AtomicInteger success = new AtomicInteger(), rejected = new AtomicInteger();
        Runnable buy = () -> {
            try { order(product, 1); success.incrementAndGet(); }
            catch (NanacocoaException e) { assertThat(e.getErrorCode()).isEqualTo(INSUFFICIENT_STOCK); rejected.incrementAndGet(); }
        };
        parallel(buy, buy);
        assertThat(success.get()).isEqualTo(1); assertThat(rejected.get()).isEqualTo(1); stock(product, 0, 1);
    }

    @Test void insufficientSecondItemRollsBackFirstReservationAndEntireOrder() {
        Products first = product(5), second = product(0); long count = orders.count();
        assertThatThrownBy(() -> orderService.createOrder(request(List.of(new OrderItemRequest(first.getId(), 2), new OrderItemRequest(second.getId(), 1))), buyer))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(INSUFFICIENT_STOCK));
        stock(first, 5, 0); stock(second, 0, 0); assertThat(orders.count()).isEqualTo(count);
    }

    @Test void stoppedAndOversizedOrdersAreRejected() {
        Products product = product(2);
        productsService.updateInventory(product.getId(), new UpdateInventoryRequest(2L, SaleStatus.STOPPED, product.getStockVersion()), admin);
        assertThatThrownBy(() -> order(product, 1)).isInstanceOf(NanacocoaException.class);
        Products current = products.findById(product.getId()).orElseThrow();
        productsService.updateInventory(product.getId(), new UpdateInventoryRequest(2L, SaleStatus.ON_SALE, current.getStockVersion()), admin);
        assertThatThrownBy(() -> order(product, 3)).isInstanceOf(NanacocoaException.class); stock(product, 2, 0);
    }

    @Test void definitiveFailureReleasesOnceAndRetryMustReacquireStock() {
        Products product = product(1); OrderResponse order = order(product, 1); var work = approval(order);
        payments.failApproval(work.paymentId(), "DECLINED", "거절"); payments.failApproval(work.paymentId(), "DECLINED", "거절"); stock(product, 1, 0);
        order(product, 1);
        assertThatThrownBy(() -> approval(order)).isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(INSUFFICIENT_STOCK));
        stock(product, 0, 1);
    }

    @Test void reservationRetryReacquiresOnceWithoutExtendingExpiry() {
        Products product = product(1); var order = order(product, 1); var work = approval(order);
        payments.failApproval(work.paymentId(), "DECLINED", "거절");
        var first = orderService.reserveForRetry(order.getOrderId(), buyer);
        var second = orderService.reserveForRetry(order.getOrderId(), buyer);
        assertThat(first.getReservationExpiresAt()).isEqualTo(second.getReservationExpiresAt());
        stock(product, 0, 1);
        orderService.cancelUnpaid(order.getOrderId(), buyer);
        assertThatThrownBy(() -> orderService.reserveForRetry(order.getOrderId(), buyer)).isInstanceOf(NanacocoaException.class);
        stock(product, 1, 0);
    }

    @Test void retryAfterDefinitiveFailureReacquiresAvailableStock() {
        Products product = product(1); OrderResponse order = order(product, 1); var first = approval(order);
        payments.failApproval(first.paymentId(), "DECLINED", "거절"); var second = approval(order); stock(product, 0, 1);
        payments.completeApproval(second.paymentId(), response(second, "DONE")); stock(product, 0, 0);
    }

    @Test void uncertainApprovalAndLateRecoveryKeepReservedStockAfterExpiry() {
        Products product = product(1); OrderResponse order = order(product, 1); var work = approval(order);
        payments.recordUncertainApproval(work.paymentId(), "TIMEOUT", "결과 불확실"); makeExpired(order); expiration.expire(order.getOrderId()); stock(product, 0, 1);
        payments.completeApproval(work.paymentId(), response(work, "DONE")); payments.completeApproval(work.paymentId(), response(work, "DONE")); stock(product, 0, 0);
        assertThat(orders.findByOrderId(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test void compensationReleasesOnceAndRejectsLateSuccess() {
        Products product = product(1); var work = approval(order(product, 1)); payments.requestCompensation(work.paymentId(), "INTERNAL", "실패");
        payments.completeCompensation(work.paymentId(), response(work, "CANCELED")); payments.completeCompensation(work.paymentId(), response(work, "CANCELED")); stock(product, 1, 0);
        assertThatThrownBy(() -> payments.completeApproval(work.paymentId(), response(work, "DONE"))).isInstanceOf(NanacocoaException.class); stock(product, 1, 0);
    }

    @Test
    void terminalApprovalRecoveryKeepsStockOnMismatchAndReleasesItOnce() {
        Products product = product(1);
        OrderResponse order = order(product, 1);
        var work = approval(order);
        payments.requestCompensation(work.paymentId(), "INTERNAL", "확정 실패");
        when(tossPaymentsClient.findByOrderId(order.getOrderId())).thenReturn(
            new TossPaymentResponse("other-key", work.orderId(), work.amount(), "EXPIRED", null, null, List.of()),
            response(work, "EXPIRED"));

        jdbc.update("update payments set next_retry_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), work.paymentId());
        paymentRecovery.reconcileDueApprovals(100);
        stock(product, 0, 1);
        var pending = paymentRepository.findById(work.paymentId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pending.getRecoveryAction()).isEqualTo(PaymentRecoveryAction.COMPENSATE_APPROVAL);
        assertThat(pending.getFailureCode()).isEqualTo("PG_PAYMENT_MISMATCH");
        assertThat(pending.getNextRetryAt()).isAfter(LocalDateTime.now());

        jdbc.update("update payments set next_retry_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), work.paymentId());
        paymentRecovery.reconcileDueApprovals(100);
        paymentRecovery.reconcileDueApprovals(100);
        stock(product, 1, 0);
        assertThat(paymentRepository.findById(work.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        Order restored = orders.findByOrderId(order.getOrderId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(restored.getStockState()).isEqualTo(StockState.RELEASED);
        verify(tossPaymentsClient, times(2)).findByOrderId(order.getOrderId());
    }

    @Test void unpaidExpiryAndCancellationReleaseExactlyOnce() {
        Products product = product(2); var order = order(product, 2); makeExpired(order);
        expiration.expire(order.getOrderId()); expiration.expire(order.getOrderId()); orderService.cancelUnpaid(order.getOrderId(), buyer); stock(product, 2, 0);
        assertThatThrownBy(() -> approval(order)).isInstanceOf(NanacocoaException.class);
    }

    @Test void ownerCancellationDoesNotAllowAnotherMembersOrder() {
        Products product = product(1); var order = order(product, 1);
        assertThatThrownBy(() -> orderService.cancelUnpaid(order.getOrderId(), identity(false))).isInstanceOfSatisfying(NanacocoaException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(NOT_FOUND_ORDER));
        stock(product, 0, 1); orderService.cancelUnpaid(order.getOrderId(), buyer); stock(product, 1, 0);
    }

    @Test void canceledSaleRequiresAdminInspectionAndConcurrentRestockIsIdempotent() throws Exception {
        Products product = product(1); var order = order(product, 1); var work = approval(order);
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        var cancel = payments.prepareCancellation(work.paymentId(), "반품", "cancel-"+UUID.randomUUID(), "fingerprint", buyer.getUsername());
        payments.completeCancellation(cancel.cancellationId(), response(work, "CANCELED")); payments.completeCancellation(cancel.cancellationId(), response(work, "CANCELED")); stock(product, 0, 0);
        assertThatThrownBy(() -> orderService.restock(order.getOrderId(), "검수", buyer)).isInstanceOfSatisfying(NanacocoaException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ADMIN_ACCESS_REQUIRED));
        parallel(() -> orderService.restock(order.getOrderId(), "미출고 검수", admin), () -> orderService.restock(order.getOrderId(), "미출고 검수", admin)); stock(product, 1, 0);
    }

    @Test void inventoryAdjustmentCannotOverwriteAConcurrentReservation() {
        Products product = product(2); order(product, 1);
        assertThatThrownBy(() -> productsService.updateInventory(product.getId(), new UpdateInventoryRequest(10L, SaleStatus.ON_SALE, product.getStockVersion()), admin))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(STOCK_CONFLICT)); stock(product, 1, 1);
        Products current = products.findById(product.getId()).orElseThrow();
        productsService.updateInventory(product.getId(), new UpdateInventoryRequest(10L, SaleStatus.ON_SALE, current.getStockVersion()), admin); stock(product, 10, 1);
    }

    @Test void approvalAndCancellationRaceCannotReleaseAnActivePaymentReservation() throws Exception {
        Products product = product(1); var order = order(product, 1);
        parallel(() -> { try { approval(order); } catch (NanacocoaException e) { assertThat(e.getErrorCode()).isEqualTo(PAYMENT_ALREADY_PROCESSED); } },
            () -> { try { orderService.cancelUnpaid(order.getOrderId(), buyer); } catch (NanacocoaException e) { assertThat(e.getErrorCode()).isEqualTo(INVALID_PAYMENT_STATUS); } });
        var persisted = orders.findByOrderId(order.getOrderId()).orElseThrow();
        if (persisted.getStatus() == OrderStatus.PAYMENT_PENDING) stock(product, 0, 1);
        else { assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CANCELED); stock(product, 1, 0); }
    }

    @Test void expiredOrderCannotEnterApprovalDuringConcurrentExpiry() throws Exception {
        Products product = product(1); var order = order(product, 1); makeExpired(order);
        parallel(() -> assertThatThrownBy(() -> approval(order)).isInstanceOf(NanacocoaException.class), () -> expiration.expire(order.getOrderId())); stock(product, 1, 0);
        assertThat(orders.findByOrderId(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    Order legacyOrder(Products product) {
        Order legacy = Order.create("legacy-"+UUID.randomUUID(), members.findByEmail(buyer.getUsername()).orElseThrow(), "테스트", "010-0000-0000", "테스트", null);
        legacy.addItem(product, 1);
        return legacy;
    }

    @Test void legacyOrdersCannotStartNewApprovalsOrFabricateInventory() {
        Products product = product(1);
        OrderResponse order = OrderResponse.from(orders.saveAndFlush(legacyOrder(product)));
        assertThatThrownBy(() -> approval(order)).isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ORDER_RESERVATION_EXPIRED));
        stock(product, 1, 0);
    }

    @Test void legacyPendingApprovalCanRecoverWithoutCreatingStock() {
        Products product = product(0);
        Order legacy = legacyOrder(product); legacy.startPayment(); legacy = orders.saveAndFlush(legacy);
        var payment = com.nanacocoa.server.payment.entity.Payment.pending(legacy, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "fingerprint", UUID.randomUUID().toString(), 41000, LocalDateTime.now());
        payment = paymentRepository.saveAndFlush(payment);
        payments.completeApproval(payment.getId(), new TossPaymentResponse(payment.getPaymentKey(), legacy.getOrderId(), 41000L, "DONE", "카드", OffsetDateTime.now(), List.of()));
        stock(product, 0, 0);
        assertThat(orders.findByOrderId(legacy.getOrderId()).orElseThrow().getStockState()).isEqualTo(StockState.LEGACY);
    }

    @Test void concurrentApprovalCompletionAndExpiryKeepSoldStock() throws Exception {
        Products product = product(1); var order = order(product, 1); var work = approval(order); makeExpired(order);
        parallel(() -> payments.completeApproval(work.paymentId(), response(work, "DONE")), () -> expiration.expire(order.getOrderId()));
        stock(product, 0, 0);
        assertThat(orders.findByOrderId(order.getOrderId()).orElseThrow().getStockState()).isEqualTo(StockState.SOLD);
    }

    @Test void oppositeMultiProductRequestOrderDoesNotDeadlock() throws Exception {
        Products first = product(2), second = product(2);
        parallel(() -> orderService.createOrder(request(List.of(new OrderItemRequest(first.getId(), 1), new OrderItemRequest(second.getId(), 1))), buyer),
            () -> orderService.createOrder(request(List.of(new OrderItemRequest(second.getId(), 1), new OrderItemRequest(first.getId(), 1))), buyer));
        stock(first, 0, 2); stock(second, 0, 2);
    }

    @Test void adminCannotRestockPaidOrderBeforeCancellation() {
        Products product = product(1); var order = order(product, 1); var work = approval(order);
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        assertThatThrownBy(() -> orderService.restock(order.getOrderId(), "검수", admin)).isInstanceOf(NanacocoaException.class); stock(product, 0, 0);
    }

    @Test void inventoryHttpApiEnforcesRoleValidationAndPersistsAvailability() throws Exception {
        Products product = product(0); String path = "/api/products/"+product.getId()+"/inventory";
        String payload = "{\"stockQuantity\":2,\"saleStatus\":\"ON_SALE\",\"expectedVersion\":0}";
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).session(session(buyer)).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        mvc.perform(put(path).session(session(admin)).contentType(MediaType.APPLICATION_JSON).content(payload.replace(":2,", ":-1,"))).andExpect(status().isBadRequest());
        mvc.perform(put(path).session(session(admin)).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stockQuantity").value(2)).andExpect(jsonPath("$.data.availability").value("ON_SALE"));
        for (MockHttpSession identity : List.of(session(buyer), session(admin))) {
            mvc.perform(get("/api/products/"+product.getId()).session(identity)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.reservedQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.stockVersion").doesNotExist());
            mvc.perform(get("/api/products").session(identity)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].stockQuantity").isEmpty())
                .andExpect(jsonPath("$.data[*].reservedQuantity").isEmpty())
                .andExpect(jsonPath("$.data[*].stockVersion").isEmpty());
        }
        mvc.perform(get("/api/products/"+product.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stockQuantity").doesNotExist())
            .andExpect(jsonPath("$.data.reservedQuantity").doesNotExist())
            .andExpect(jsonPath("$.data.stockVersion").doesNotExist());
        mvc.perform(get("/api/products")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].stockQuantity").isEmpty())
            .andExpect(jsonPath("$.data[*].reservedQuantity").isEmpty())
            .andExpect(jsonPath("$.data[*].stockVersion").isEmpty());
        mvc.perform(get("/api/products/inventory")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/inventory").session(session(buyer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/inventory").session(session(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == " + product.getId() + ")].stockQuantity").value(org.hamcrest.Matchers.contains(2)));
        jdbc.update("update members set is_admin = false where email = ?", admin.getUsername());
        mvc.perform(get("/api/products/inventory").session(session(admin))).andExpect(status().isForbidden());
        order(product, 2); mvc.perform(get("/api/products/"+product.getId())).andExpect(jsonPath("$.data.availability").value("SOLD_OUT"));
    }

    @Test void newProductsAreStoppedWithoutInventedStock() {
        Products product = products.saveAndFlush(Products.builder().name("new-"+UUID.randomUUID()).price(1L).summary("요약").detailTitle("상세").description("설명").build());
        stock(product, 0, 0); assertThat(product.getAvailability()).isEqualTo("STOPPED");
    }
    MockHttpSession session(UserDetailsImpl identity) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, identity.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context); return session;
    }
    void parallel(Runnable first, Runnable second) throws Exception {
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : List.of(first, second)) futures.add(executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } task.run();
            }));
            start.countDown(); for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
}
