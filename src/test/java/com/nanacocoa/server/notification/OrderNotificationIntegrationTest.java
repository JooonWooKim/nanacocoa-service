package com.nanacocoa.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.notification.entity.OrderNotification;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import com.nanacocoa.server.notification.service.NotificationPublisher;
import com.nanacocoa.server.notification.service.NotificationWorker;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
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
    "notifications.starts-at=2026-01-01T00:00:00Z", "notifications.initial-delay=3600000",
    "notifications.support.enabled=true", "notifications.support.starts-at=2026-02-01T00:00:00Z",
    "product-image.storage-mode=mock", "toss-payments.enabled=false"})
class OrderNotificationIntegrationTest {
    static final String PASSWORD = "SyntheticNotify123!";
    static final String API = "/api/notifications";
    static final String ADMIN = "/api/admin/notifications";
    static final LocalDateTime CUTOFF = LocalDateTime.parse("2026-01-01T00:00:00");

    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String mysql = System.getenv("NOTIFY_TEST_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) throw new IllegalArgumentException("Loopback DB required");
        r.add("spring.datasource.url", () -> mysql == null
                ? "jdbc:h2:mem:notify;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
        r.add("spring.datasource.driver-class-name", () -> mysql == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username", () -> mysql == null ? "sa" : "root");
        r.add("spring.datasource.password", () -> "");
    }

    @Autowired JdbcTemplate sql;
    @Autowired TransactionTemplate transactions;
    @Autowired NotificationWorker worker;
    @Autowired NotificationPublisher publisher;
    @MockitoSpyBean OrderNotificationRepository rows;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    Member member(boolean admin) {
        var m = Member.builder().email(UUID.randomUUID()+"@example.test").name("합성 회원")
                .password(passwords.encode(PASSWORD)).build();
        m.setAdmin(admin);
        return members.saveAndFlush(m);
    }

    long order(Member member) {
        String order = "NC_" + UUID.randomUUID();
        sql.update("insert into orders (order_id,member_id,total_amount,status,version,created_at,updated_at) values (?,?,42000,'CREATED',0,current_timestamp,current_timestamp)",
                order, member.getId());
        return sql.queryForObject("select id from orders where order_id=?", Long.class, order);
    }

    OrderNotification pending(Member owner) {
        long order = order(owner);
        String number = sql.queryForObject("select order_id from orders where id=?", String.class, order);
        return rows.saveAndFlush(new OrderNotification(order, owner.getId(), number,
                OrderNotification.Type.PAYMENT_COMPLETED, CUTOFF));
    }

    MockHttpSession login(Member member) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email",member.getEmail(),"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    void shipment(long order, String action, LocalDateTime time, long operator) {
        sql.update("insert into shipments (order_id,status,revision) values (?,'SHIPPED',1)",order);
        long shipment = sql.queryForObject("select id from shipments where order_id=?", Long.class, order);
        sql.update("""
            insert into shipment_events (shipment_id,request_key,fingerprint,action,previous_status,next_status,operator_id,recorded_at,reason,notification_confirmed_at)
                values (?,?,? ,?,'NOT_SHIPPED','SHIPPED',?,current_timestamp,'synthetic',?)
            """,shipment,UUID.randomUUID().toString(),"a".repeat(64),action,operator,time);
    }

    @Test void boundaryRollbackImportsAndConcurrentRediscoveryProduceOneNotification() throws Exception {
        var owner = member(false);
        long eligible = order(owner);
        shipment(eligible,"SHIP",CUTOFF,owner.getId());
        shipment(order(owner),"SHIP",CUTOFF.minusSeconds(1),owner.getId());
        shipment(order(owner),"IMPORT_SHIPPED",CUTOFF,owner.getId());
        long rollback = order(owner);
        transactions.executeWithoutResult(tx -> {
            shipment(rollback,"SHIP",CUTOFF,owner.getId());
            tx.setRollbackOnly();
        });
        assertThat(rows.existsByOrderIdAndEventType(eligible, OrderNotification.Type.SHIPPED)).isFalse();
        var restartedWorker = new NotificationWorker(rows, publisher, CUTOFF.toInstant(ZoneOffset.UTC).toString(), false, "");
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a=workers.submit(worker::run);
            var b=workers.submit(restartedWorker::run);
            a.get(15,TimeUnit.SECONDS);
            b.get(15,TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        worker.run();
        var session = login(owner);
        mvc.perform(get(API).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("SHIPPED"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test void readIsIdempotentAndScopedToOwnerAndCsrf() throws Exception {
        var owner = member(false);
        var item = pending(owner);
        for (int i = 0; i < 20; i++) pending(owner);
        worker.run();
        var session = login(owner);
        String read = API+"/"+item.getId()+"/read";
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).session(session)).andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(21));
        mvc.perform(get(API).session(session).param("page", "1"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(item.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(put(read).session(session)).andExpect(status().isForbidden());
        mvc.perform(put(read).session(login(member(false))).with(csrf(mvc))).andExpect(status().isNotFound());
        var otherSession = login(owner);
        var readers = Executors.newFixedThreadPool(2);
        try {
            var firstRequest = readers.submit(() -> mvc.perform(put(read).session(session).with(csrf(mvc)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var secondRequest = readers.submit(() -> mvc.perform(put(read).session(otherSession).with(csrf(mvc)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(json.readTree(firstRequest.get(15, TimeUnit.SECONDS)).at("/data/readAt"))
                    .isEqualTo(json.readTree(secondRequest.get(15, TimeUnit.SECONDS)).at("/data/readAt"));
        } finally { readers.shutdownNow(); }
        var first = rows.findById(item.getId()).orElseThrow().getReadAt();
        assertThat(first).isNotNull();
        mvc.perform(put(read).session(session).with(csrf(mvc))).andExpect(status().isOk());
        assertThat(rows.findById(item.getId()).orElseThrow().getReadAt()).isEqualTo(first);
        mvc.perform(get(API).session(session)).andExpect(jsonPath("$.data.unreadCount").value(20));
    }

    @Test void failedPublishIsBoundedAndOperatorCanRetryOnlyNotification() throws Exception {
        var owner=member(false);
        var job=pending(owner);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic")).when(rows).flush();
        for (int i=0;i<4;i++) worker.run();
        // Clear the spy's stubbing before the retry so the publisher can flush normally.
        org.mockito.Mockito.reset(rows);
        var failed=rows.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OrderNotification.Status.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        mvc.perform(get(API).session(login(owner))).andExpect(jsonPath("$.data.items").isEmpty());
        String retry=ADMIN+"/"+job.getId()+"/retry";
        mvc.perform(post(retry).session(login(owner)).with(csrf(mvc))).andExpect(status().isForbidden());
        var admin=login(member(true));
        mvc.perform(get(ADMIN).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + job.getId() + ")].failureCode").value("PUBLISH_FAILED"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + job.getId() + ")].attemptCount").value(3));
        mvc.perform(post(retry).session(admin).with(csrf(mvc))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        worker.run();
        var published=rows.findById(job.getId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OrderNotification.Status.PUBLISHED);
        assertThat(published.getAttemptCount()).isEqualTo(4);
        assertThat(sql.queryForObject("select status from orders where id=?", String.class, job.getOrderId())).isEqualTo("CREATED");
    }

    @Test void confirmedReplyWithoutOrderJoinsInboxAndRemainsPrivateAcrossRetries() throws Exception {
        var owner = member(false);
        var ownerSession = login(owner);
        var other = login(member(false));
        var admin = login(member(true));
        String inquiryApi = "/api/support/inquiries";
        String adminApi = "/api/admin/support/inquiries";
        String id = json.readTree(mvc.perform(post(inquiryApi).session(ownerSession).with(csrf(mvc))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content("{\"title\":\"synthetic title\",\"body\":\"synthetic inquiry\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/data/id").asText();
        String reply = adminApi + "/" + id + "/reply";
        var body = "{\"body\":\"synthetic private reply\"}";
        var orderJob = pending(owner);
        worker.run();
        assertThat(rows.existsByInquiryId(Long.valueOf(id))).isFalse();
        mvc.perform(post(reply).session(admin).with(csrf(mvc)).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post(reply).session(admin).with(csrf(mvc)).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        assertThat(sql.queryForObject("select notification_confirmed_at from support_inquiries where id=?",
                LocalDateTime.class, Long.valueOf(id))).isNotNull();
        var paused = new NotificationWorker(rows, publisher, "2026-01-01T00:00:00Z", false, "");
        paused.run();
        assertThat(rows.existsByInquiryId(Long.valueOf(id))).isFalse();
        var supportCutoff = LocalDateTime.parse("2026-02-01T00:00:00");
        sql.update("update support_inquiries set notification_confirmed_at=? where id=?",supportCutoff,Long.valueOf(id));
        var event = rows.undiscoveredReplies(supportCutoff).stream()
                .filter(r -> r.getInquiryId().equals(Long.valueOf(id))).findFirst().orElseThrow();
        publisher.discoverReply(event);
        paused.run();
        var notificationId = sql.queryForObject("select id from order_notifications where inquiry_id=?", Long.class, Long.valueOf(id));
        assertThat(rows.findById(notificationId).orElseThrow().getStatus()).isEqualTo(OrderNotification.Status.PENDING);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic")).when(rows).flush();
        for (int i = 0; i < 4; i++) worker.run();
        reset(rows);
        mvc.perform(get(inquiryApi + "/" + id).session(ownerSession))
                .andExpect(jsonPath("$.data.replyBody").value("synthetic private reply"));
        mvc.perform(get(inquiryApi + "/" + id).session(other)).andExpect(status().isNotFound());
        String retry = ADMIN + "/" + notificationId + "/retry";
        mvc.perform(post(retry).session(other).with(csrf(mvc))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).session(admin))
                .andExpect(jsonPath("$.data.items[?(@.id == " + notificationId + ")].attemptCount").value(3));
        mvc.perform(post(retry).session(admin).with(csrf(mvc)))
                .andExpect(jsonPath("$.data.inquiryId").value(id));
        var resumed = new NotificationWorker(rows, publisher, "2026-01-01T00:00:00Z", true, "2026-02-01T00:00:00Z");
        var threads = Executors.newFixedThreadPool(2);
        try {
            var a = threads.submit(worker::run);
            var b = threads.submit(resumed::run);
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally { threads.shutdownNow(); }
        assertThat(rows.findById(notificationId).orElseThrow().getAttemptCount()).isEqualTo(4);
        mvc.perform(get(API).session(ownerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(id))
                .andExpect(jsonPath("$.data.items[0].type").value("SUPPORT_ANSWERED"))
                .andExpect(jsonPath("$.data.items[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].replyBody").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].title").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].id").value(orderJob.getId()))
                .andExpect(jsonPath("$.data.items[1].inquiryId").doesNotExist())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
        String read = API + "/" + notificationId + "/read";
        mvc.perform(put(read).session(ownerSession)).andExpect(status().isForbidden());
        mvc.perform(put(read).session(other).with(csrf(mvc))).andExpect(status().isNotFound());
        mvc.perform(put(read).session(ownerSession).with(csrf(mvc))).andExpect(status().isOk());
        mvc.perform(get(API).session(ownerSession)).andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test void replyDiscoveryIgnoresLegacyEarlyAndRolledBackConfirmationsUnderConcurrency() throws Exception {
        var owner = member(false);
        var boundary = LocalDateTime.parse("2026-02-01T00:00:00");
        long eligible = insertAnsweredInquiry(owner, boundary);
        insertAnsweredInquiry(owner, boundary.minusSeconds(1));
        insertAnsweredInquiry(owner, null);
        transactions.executeWithoutResult(tx -> {
            insertAnsweredInquiry(owner, boundary);
            tx.setRollbackOnly();
        });
        var second = new NotificationWorker(rows, publisher, "2026-01-01T00:00:00Z", true, "2026-02-01T00:00:00Z");
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(worker::run);
            var b = workers.submit(second::run);
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        mvc.perform(get(API).session(login(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(Long.toString(eligible)));
    }

    private long insertAnsweredInquiry(Member owner, LocalDateTime confirmedAt) {
        String key = UUID.randomUUID().toString();
        sql.update("""
            insert into support_inquiries (member_id,request_key,title,body,status,created_at,notification_confirmed_at)
            values (?,?,'synthetic','synthetic','ANSWERED',current_timestamp,?)
            """, owner.getId(), key, confirmedAt);
        return sql.queryForObject("select id from support_inquiries where member_id=? and request_key=?",
                Long.class, owner.getId(), key);
    }
}
