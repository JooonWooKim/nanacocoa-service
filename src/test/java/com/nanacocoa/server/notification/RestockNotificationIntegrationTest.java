package com.nanacocoa.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import com.nanacocoa.server.notification.repository.RestockSubscriptionRepository;
import com.nanacocoa.server.notification.service.NotificationWorker;
import com.nanacocoa.server.notification.service.RestockNotificationPublisher;
import com.nanacocoa.server.notification.service.RestockNotificationWorker;
import com.nanacocoa.server.products.config.RestockNotificationSettings;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.entity.SaleStatus;
import com.nanacocoa.server.products.repository.ProductsRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static com.nanacocoa.server.CsrfRequests.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never", "spring.jpa.open-in-view=false", "notifications.enabled=true",
        "notifications.starts-at=2000-01-01T00:00:00Z", "notifications.restock.enabled=true",
        "notifications.restock.starts-at=2026-01-01T00:00:00Z", "notifications.initial-delay=3600000",
        "product-image.storage-mode=mock", "toss-payments.enabled=false"})
class RestockNotificationIntegrationTest {
    private static final String PASSWORD = "SyntheticRestock123!";
    private static final String INBOX = "/api/notifications";

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties) {
        String mysql = System.getenv("RESTOCK_NOTIFY_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) throw new IllegalArgumentException("Loopback DB required");
        properties.add("spring.datasource.url", () -> mysql == null
                ? "jdbc:h2:mem:restock-notify;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
        properties.add("spring.datasource.driver-class-name", () -> mysql == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", () -> mysql == null ? "sa" : "root");
        properties.add("spring.datasource.password", () -> "");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwords;
    @Autowired ProductsRepository products;
    @Autowired RestockSubscriptionRepository subscriptions;
    @Autowired RestockNotificationSettings settings;
    @Autowired RestockNotificationPublisher publisher;
    @Autowired RestockNotificationWorker worker;
    @Autowired NotificationWorker otherWorker;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate sql;
    @MockitoSpyBean OrderNotificationRepository notifications;

    @Test void scopedSubscriptionAndCancellationStayConsistentUnderConcurrency() throws Exception {
        long product = product(0);
        var buyer = member(false);
        var first = login(buyer);
        var second = login(buyer);
        var other = login(member(false));
        String endpoint = endpoint(product);
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(put(endpoint).session(first)).andExpect(status().isForbidden());
        mvc.perform(put(endpoint).session(first).with(csrf(mvc)).header("X-Checkout-Account","different"))
                .andExpect(status().isConflict());
        mvc.perform(get(endpoint).session(first)).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.canSubscribe").value(true));
        concurrently(() -> { subscribe(product, first); return null; }, () -> { subscribe(product, second); return null; });
        assertThat(sql.queryForObject("select count(*) from product_restock_subscriptions where product_id=?", Integer.class, product)).isOne();
        mvc.perform(get(endpoint).session(other)).andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(delete(endpoint).session(other).with(csrf(mvc))).andExpect(status().isOk());
        mvc.perform(get(endpoint).session(first)).andExpect(jsonPath("$.data.active").value(true));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(endpoint).session(first).with(csrf(mvc))).andExpect(jsonPath("$.data.active").value(false));
        }
        inventory(product, 3, SaleStatus.ON_SALE, login(member(true)));
        worker.run();
        mvc.perform(get(INBOX).session(first)).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(put(endpoint).session(first).with(csrf(mvc))).andExpect(status().isConflict());
    }

    @Test void staleEventCannotPublishAfterSoldOutAndReservationRelease() throws Exception {
        long product = product(0);
        var buyer = login(member(false));
        var operator = login(member(true));
        subscribe(product, buyer);
        inventory(product, 1, SaleStatus.ON_SALE, operator);
        var candidate = subscriptions.ready(settings.cutoff()).stream()
                .filter(row -> row.getProductId().equals(product)).findFirst().orElseThrow();
        Long notice = publisher.prepare(candidate).orElseThrow();
        transaction.executeWithoutResult(tx -> products.findByIdForUpdate(product).orElseThrow().reserve(1));
        publisher.publish(candidate, notice);
        assertThat(notifications.findById(notice).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
        otherWorker.run();
        transaction.executeWithoutResult(tx -> products.findByIdForUpdate(product).orElseThrow().release(1));
        publisher.publish(candidate, notice);
        worker.run();
        assertThat(notifications.findById(notice).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
        inventory(product, 1, SaleStatus.STOPPED, operator);
        inventory(product, 1, SaleStatus.ON_SALE, operator);
        worker.run();
        mvc.perform(get(INBOX).session(buyer)).andExpect(jsonPath("$.data.unreadCount").value(0));

        inventory(product, 0, SaleStatus.ON_SALE, operator);
        transaction.executeWithoutResult(tx -> {
            try { inventory(product, 1, SaleStatus.ON_SALE, operator); }
            catch (Exception e) { throw new IllegalStateException(e); }
            tx.setRollbackOnly();
        });
        worker.run();
        mvc.perform(get(INBOX).session(buyer)).andExpect(jsonPath("$.data.unreadCount").value(0));
        inventory(product, 1, SaleStatus.ON_SALE, operator);
        concurrently(() -> { worker.run(); return null; }, () -> { worker.run(); return null; });
        mvc.perform(get(INBOX).session(buyer)).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("PRODUCT_RESTOCKED"))
                .andExpect(jsonPath("$.data.items[0].productId").value(Long.toString(product)))
                .andExpect(jsonPath("$.data.items[0].orderId").doesNotExist());
        mvc.perform(put(INBOX + "/" + notice + "/read").session(buyer).with(csrf(mvc))).andExpect(status().isOk());
        inventory(product, 0, SaleStatus.ON_SALE, operator);
        subscribe(product, buyer);
        worker.run();
        inventory(product, 1, SaleStatus.ON_SALE, operator);
        worker.run();
        mvc.perform(get(INBOX).session(buyer)).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test void failedRestockPublishKeepsInventoryAndSupportsManualRetry() throws Exception {
        long product = product(0);
        var buyer = login(member(false));
        var operator = login(member(true));
        subscribe(product, buyer);
        inventory(product, 4, SaleStatus.ON_SALE, operator);
        var candidate = subscriptions.ready(settings.cutoff()).stream()
                .filter(row -> row.getProductId().equals(product)).findFirst().orElseThrow();
        Long id = publisher.prepare(candidate).orElseThrow();
        doThrow(new DataAccessResourceFailureException("synthetic")).when(notifications).flush();
        try { for (int attempt = 0; attempt < 4; attempt++) worker.run(); }
        finally { reset(notifications); }
        var failed = notifications.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(4);
        String retry = "/api/admin/notifications/" + id + "/retry";
        mvc.perform(post(retry).session(buyer).with(csrf(mvc))).andExpect(status().isForbidden());
        mvc.perform(post(retry).session(operator).with(csrf(mvc))).andExpect(jsonPath("$.data.status").value("PENDING"));
        worker.run();
        assertThat(notifications.findById(id).orElseThrow().getAttemptCount()).isEqualTo(4);
        mvc.perform(get(INBOX).session(buyer)).andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test void cancellationAndPublicationProduceOnlyOneTerminalOutcome() throws Exception {
        long product = product(0);
        var buyer = login(member(false));
        subscribe(product, buyer);
        inventory(product, 1, SaleStatus.ON_SALE, login(member(true)));
        var candidate = subscriptions.ready(settings.cutoff()).stream()
                .filter(row -> row.getProductId().equals(product)).findFirst().orElseThrow();
        Long notice = publisher.prepare(candidate).orElseThrow();
        concurrently(() -> { publisher.publish(candidate, notice); return null; }, () -> {
            mvc.perform(delete(endpoint(product)).session(buyer).with(csrf(mvc)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
            return null;
        });
        assertThat(subscriptions.findById(candidate.getSubscriptionId()).orElseThrow().isActive()).isFalse();
        notifications.findById(notice).ifPresent(row -> assertThat(row.getStatus()).isEqualTo(Status.PUBLISHED));
        worker.run();
        assertThat(sql.queryForObject("select count(*) from order_notifications where product_id=?", Integer.class, product))
                .isBetween(0, 1);
    }

    private long product(long available) {
        var product = Products.builder().name("합성 " + UUID.randomUUID()).price(41_000L)
                .summary("합성 상품").detailTitle("합성 상품").description("검증").build();
        product.updateInventory(available, SaleStatus.ON_SALE, 0);
        return products.saveAndFlush(product).getId();
    }

    private Member member(boolean admin) {
        var member = Member.builder().email(UUID.randomUUID() + "@example.test").name("합성 회원")
                .password(passwords.encode(PASSWORD)).build();
        member.setAdmin(admin);
        return members.saveAndFlush(member);
    }

    private MockHttpSession login(Member member) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", member.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private void inventory(long product, long stock, SaleStatus status, MockHttpSession operator) throws Exception {
        long version = products.findById(product).orElseThrow().getStockVersion();
        mvc.perform(put("/api/products/" + product + "/inventory").session(operator)
                .with(csrf(mvc)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("stockQuantity", stock, "saleStatus", status, "expectedVersion", version))))
                .andExpect(status().isOk());
    }

    private void subscribe(long product, MockHttpSession session) throws Exception {
        mvc.perform(put(endpoint(product)).session(session).with(csrf(mvc)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
    }

    private static String endpoint(long product) { return "/api/restock-subscriptions/" + product; }

    private static void concurrently(Callable<Void> first, Callable<Void> second) throws Exception {
        var threads = Executors.newFixedThreadPool(2);
        try {
            var a = threads.submit(first);
            var b = threads.submit(second);
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally { threads.shutdownNow(); }
    }
}
