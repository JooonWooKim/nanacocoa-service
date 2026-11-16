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
import com.fasterxml.jackson.databind.ObjectMapper;

/** 같은 테스트를 임시 MySQL과 H2에서 실행합니다. 외부 PG는 호출하지 않습니다. */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
    "spring.flyway.enabled=true", "spring.sql.init.mode=never",
    "shipping.carriers.test.name=테스트 택배",
    "shipping.carriers.test.tracking-pattern=[0-9]{10}",
    "shipping.carriers.test.tracking-url=https://carrier.example.test/track/{tracking}"
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
    @Autowired ObjectMapper json;
    @Autowired ShippingService shipping;
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
    @Test
    void orderHistoryPaginatesInStableOrderAndReturnsOnlyTheOwnersSummaries() throws Exception {
        MockHttpSession buyerSession = session(buyer);
        mvc.perform(get("/api/orders").session(buyerSession)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders").isEmpty()).andExpect(jsonPath("$.data.hasNext").value(false));
        Products cocoa = product(25);
        Products second = product(1);
        for (int count = 0; count < 20; count++) {
            order(cocoa, 1);
        }
        var newest = orderService.createOrder(request(List.of(
            new OrderItemRequest(second.getId(), 1), new OrderItemRequest(cocoa.getId(), 1))), buyer);
        var owner = members.findByEmail(buyer.getUsername()).orElseThrow();
        jdbc.update("update orders set created_at = ? where member_id = ?",
            LocalDateTime.of(2026, 1, 1, 12, 0), owner.getId());
        orderService.cancelUnpaid(newest.getOrderId(), buyer);
        orderService.createOrder(request(List.of(new OrderItemRequest(cocoa.getId(), 1))), admin);

        mvc.perform(get("/api/orders").session(buyerSession)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.orders.length()").value(20))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(newest.getOrderId()))
            .andExpect(jsonPath("$.data.orders[0].productName").value(second.getName()))
            .andExpect(jsonPath("$.data.orders[0].status").value("CANCELED"))
            .andExpect(jsonPath("$.data.orders[0].totalAmount").value(82000))
            .andExpect(jsonPath("$.data.orders[0].phoneNumber").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.data.orders[0].shippingAddress").doesNotHaveJsonPath());
        mvc.perform(get("/api/orders").param("page", "1").session(buyerSession)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.orders.length()").value(1))
            .andExpect(jsonPath("$.data.orders[0].status").value("CREATED"));
        for (String page : List.of("2", "2147483647")) {
            mvc.perform(get("/api/orders").param("page", page).session(buyerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").isEmpty());
        }
        for (String page : List.of("-1", "bad", "", "2147483648")) {
            mvc.perform(get("/api/orders").param("page", page).session(buyerSession)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void orderHistoryProtectsDetailsAndReadsStoredPurchaseInformation() throws Exception {
        Products cocoa = product(2);
        var purchased = order(cocoa, 2);
        String path = "/api/orders/" + purchased.getOrderId();
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        String missing = mvc.perform(get("/api/orders/nonexistent").session(session(admin)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(NOT_FOUND_ORDER.getCode()))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(get(path).session(session(admin))).andExpect(status().isNotFound())
            .andExpect(content().string(missing));

        jdbc.update("update products set name = ?, price = ? where id = ?", "바뀐 이름", 555L, cocoa.getId());
        jdbc.update("update members set address = ? where email = ?", "바뀐 주소", buyer.getUsername());
        mvc.perform(get(path).session(session(buyer))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.shippingAddress").value("테스트 배송지"))
            .andExpect(jsonPath("$.data.items[0].productName").value(cocoa.getName()))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value(41000))
            .andExpect(jsonPath("$.data.items[0].quantity").value(2))
            .andExpect(jsonPath("$.data.items[0].lineAmount").value(82000))
            .andExpect(jsonPath("$.data.paymentResult").value("UNPAID"));

        jdbc.update("update orders set phone_number = '', shipping_address = '', stock_state = 'LEGACY' where order_id = ?",
            purchased.getOrderId());
        mvc.perform(get(path).session(session(buyer))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phoneNumber").value(""))
            .andExpect(jsonPath("$.data.shippingAddress").value(""));
    }

    @Test
    void orderHistoryDistinguishesApprovalFailureCompensationAndCustomerCancellation() throws Exception {
        Products cocoa = product(3);
        var purchased = order(cocoa, 1);
        MockHttpSession buyerSession = session(buyer);

        var first = approval(purchased);
        expectOrderPaymentResult(purchased, buyerSession, "PENDING");
        payments.failApproval(first.paymentId(), "DECLINED", "테스트 제공자 오류");
        expectOrderPaymentResult(purchased, buyerSession, "FAILED");

        var second = approval(purchased);
        expectOrderPaymentResult(purchased, buyerSession, "PENDING");
        payments.completeApproval(second.paymentId(), response(second, "DONE"));
        expectOrderPaymentResult(purchased, buyerSession, "PAID");
        var cancellation = payments.prepareCancellation(
            second.paymentId(), "테스트", UUID.randomUUID().toString(), "cancel-fingerprint", buyer.getUsername());
        expectOrderPaymentResult(purchased, buyerSession, "CANCELLATION_PENDING");
        payments.completeCancellation(cancellation.cancellationId(), response(second, "CANCELED"));
        expectOrderPaymentResult(purchased, buyerSession, "PAYMENT_CANCELED");

        var compensated = order(cocoa, 1);
        var payment = approval(compensated);
        payments.requestCompensation(payment.paymentId(), "ERROR", "테스트 보상");
        expectOrderPaymentResult(compensated, buyerSession, "PENDING");
        payments.completeCompensation(payment.paymentId(), response(payment, "CANCELED"));
        expectOrderPaymentResult(compensated, buyerSession, "FAILED");
        orderService.cancelUnpaid(compensated.getOrderId(), buyer);
        expectOrderPaymentResult(compensated, buyerSession, "ORDER_CANCELED");

        org.mockito.Mockito.verifyNoInteractions(tossPaymentsClient);
    }

    @Test
    void shippingHttpLifecyclePreservesHistoryAndProtectsOrderData() throws Exception {
        var order = order(product(1), 1);
        var work = approval(order);
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        String collection = "/api/admin/shipping/orders";
        String endpoint = collection + "/" + order.getOrderId();
        var operatorSession = session(admin);

        mvc.perform(get(collection).param("orderNumber", order.getOrderId()).session(operatorSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.orders[0].shippingStatus").value("PREPARING"))
            .andExpect(jsonPath("$.data.orders[0].shippingAddress").doesNotHaveJsonPath());
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).session(session(buyer))).andExpect(status().isForbidden());
        String csrf = mvc.perform(get(endpoint).session(operatorSession)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.actions[0]").value("SHIP"))
            .andReturn().getResponse().getHeader("X-CSRF-TOKEN");
        assertThat(csrf).isNotBlank();

        var ship = shippingChange(Action.SHIP, 0L, "0000000123");
        String body = json.writeValueAsString(ship);
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).session(operatorSession).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).session(operatorSession).header("X-CSRF-TOKEN", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).session(operatorSession).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(shippingChange(Action.SHIP, 0L, "invalid"))))
            .andExpect(status().isBadRequest());
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post(endpoint).session(operatorSession).header("X-CSRF-TOKEN", csrf)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tracking.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.history.length()").value(1));
        }

        var original = shipping.get(order.getOrderId(), admin).tracking().shippedAt();
        var reusedKey = new ShippingChangeRequest(Action.SHIP, ship.requestKey(), 0L, "test",
            "0000000987", "다른 입력", null, null);
        assertThatThrownBy(() -> shipping.change(order.getOrderId(), reusedKey, admin))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_REUSED));
        var auditInsertFailure = new ShippingChangeRequest(Action.CORRECT_TRACKING, UUID.randomUUID(), 1L, "test",
            "0000000987", "x".repeat(501), null, null);
        assertThatThrownBy(() -> shipping.change(order.getOrderId(), auditInsertFailure, admin))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var afterRollback = shipping.get(order.getOrderId(), admin);
        assertThat(afterRollback.tracking().trackingNumber()).isEqualTo("0000000123");
        assertThat(afterRollback.history()).hasSize(1);
        assertThat(afterRollback.revision()).isEqualTo(1);
        shipping.change(order.getOrderId(), shippingChange(Action.CORRECT_TRACKING, 1L, "0000000456"), admin);
        assertThatThrownBy(() -> shipping.change(order.getOrderId(), shippingChange(Action.DELIVER, 1L, null), admin))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(SHIPPING_CONFLICT));
        var delivered = shipping.change(order.getOrderId(), shippingChange(Action.DELIVER, 2L, null), admin);
        assertThat(delivered.tracking().shippedAt()).isEqualTo(original);
        assertThat(delivered.history().get(1).previousTracking()).isEqualTo("0000000123");

        mvc.perform(get("/api/orders/" + order.getOrderId()).session(session(buyer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shipping.status").value("DELIVERED"))
            .andExpect(jsonPath("$.data.shipping.trackingNumber").value("0000000456"))
            .andExpect(jsonPath("$.data.shipping.trackingUrl").value("https://carrier.example.test/track/0000000456"))
            .andExpect(jsonPath("$.data.shipping.history").doesNotHaveJsonPath());
        mvc.perform(get("/api/orders/" + order.getOrderId()).session(operatorSession)).andExpect(status().isNotFound());
        jdbc.update("update members set is_admin = false where email = ?", admin.getUsername());
        mvc.perform(get(endpoint).session(operatorSession)).andExpect(status().isForbidden());
    }

    @Test
    void shippingAndCancellationRaceAndPendingRecoveryHaveOneWinner() throws Exception {
        var order = order(product(1), 1);
        var work = approval(order);
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        var succeeded = new AtomicInteger();
        parallel(() -> {
            try {
                shipping.change(order.getOrderId(), shippingChange(Action.SHIP, 0L, "0000000123"), admin);
                succeeded.incrementAndGet();
            } catch (NanacocoaException expected) {
                assertThat(expected.getErrorCode()).isEqualTo(SHIPPING_CONFLICT);
            }
        }, () -> {
            try {
                payments.prepareCancellation(work.paymentId(), "취소", "race-" + UUID.randomUUID(), "fp", buyer.getUsername());
                succeeded.incrementAndGet();
            } catch (NanacocoaException expected) {
                assertThat(expected.getErrorCode()).isEqualTo(SHIPPING_CANCEL_BLOCKED);
            }
        });
        assertThat(succeeded.get()).isEqualTo(1);

        var second = order(product(1), 1);
        var secondPayment = approval(second);
        assertThatThrownBy(() -> shipping.change(second.getOrderId(), shippingChange(Action.SHIP, 0L, "0000000123"), admin))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(SHIPPING_CONFLICT));
        payments.completeApproval(secondPayment.paymentId(), response(secondPayment, "DONE"));
        String key = "pending-" + UUID.randomUUID();
        var cancellation = payments.prepareCancellation(secondPayment.paymentId(), "취소", key, "fp", buyer.getUsername());
        payments.recordUncertainCancellation(cancellation.cancellationId(), "TIMEOUT", "결과 확인 중");
        assertThat(shipping.get(second.getOrderId(), admin).tracking().status()).isEqualTo(ShippingStatus.ON_HOLD);
        assertThatThrownBy(() -> shipping.change(second.getOrderId(), shippingChange(Action.SHIP, 0L, "0000000123"), admin))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(SHIPPING_CONFLICT));
        assertThat(payments.prepareCancellation(secondPayment.paymentId(), "취소", key, "fp", buyer.getUsername()).cancellationId())
            .isEqualTo(cancellation.cancellationId());
        payments.failCancellation(cancellation.cancellationId(), "DECLINED", "확정 실패");
        shipping.change(second.getOrderId(), shippingChange(Action.SHIP, 0L, "0000000123"), admin);
        assertThatThrownBy(() -> payments.prepareCancellation(secondPayment.paymentId(), "취소", key + "-new", "fp", buyer.getUsername()))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(SHIPPING_CANCEL_BLOCKED));
    }

    @Test
    void oldOrdersRequireVerifiedShippingEvidence() {
        var order = order(product(1), 1);
        var work = approval(order);
        payments.completeApproval(work.paymentId(), response(work, "DONE"));
        jdbc.update("update orders set legacy_shipping_review = true where order_id = ?", order.getOrderId());
        var held = shipping.get(order.getOrderId(), admin);
        assertThat(held.tracking().status()).isEqualTo(ShippingStatus.ON_HOLD);
        assertThat(held.history()).isEmpty();
        String key = "legacy-" + UUID.randomUUID();
        assertThatThrownBy(() -> payments.prepareCancellation(work.paymentId(), "취소", key, "fp", buyer.getUsername()))
            .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(SHIPPING_CANCEL_BLOCKED));
        var verified = shipping.change(order.getOrderId(), shippingChange(Action.VERIFY_UNSHIPPED, 0L, null), admin);
        assertThat(verified.tracking().status()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(verified.tracking().shippedAt()).isNull();
        assertThat(payments.prepareCancellation(work.paymentId(), "취소", key, "fp", buyer.getUsername()).cancellationId())
            .isNotNull();

        var imported = order(product(1), 1);
        var importedPayment = approval(imported);
        payments.completeApproval(importedPayment.paymentId(), response(importedPayment, "DONE"));
        jdbc.update("update orders set legacy_shipping_review = true where order_id = ?", imported.getOrderId());
        LocalDateTime shippedAt = LocalDateTime.now().minusDays(2).withNano(0);
        LocalDateTime deliveredAt = shippedAt.plusDays(1);
        var confirmed = shipping.change(imported.getOrderId(), new ShippingChangeRequest(Action.IMPORT_DELIVERED,
            UUID.randomUUID(), 0L, "test", "0000000321", "기존 송장 대조", shippedAt, deliveredAt), admin);
        assertThat(confirmed.tracking().shippedAt()).isEqualTo(shippedAt);
        assertThat(confirmed.tracking().deliveredAt()).isEqualTo(deliveredAt);
        assertThat(confirmed.tracking().status()).isEqualTo(ShippingStatus.DELIVERED);
    }

    private ShippingChangeRequest shippingChange(Action action, long revision, String tracking) {
        return new ShippingChangeRequest(action, UUID.randomUUID(), revision, tracking == null ? null : "test",
            tracking, "운영 기록 확인", null, null);
    }

    private void expectOrderPaymentResult(OrderResponse order, MockHttpSession session, String result) throws Exception {
        mvc.perform(get("/api/orders/" + order.getOrderId()).session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentResult").value(result))
            .andExpect(jsonPath("$.data.paymentKey").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.data.approvalIdempotencyKey").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.data.failureMessage").doesNotHaveJsonPath());
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
