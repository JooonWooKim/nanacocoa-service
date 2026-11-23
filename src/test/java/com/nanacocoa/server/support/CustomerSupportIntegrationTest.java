package com.nanacocoa.server.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.support.dto.reqeust.SupportRequests;
import com.nanacocoa.server.support.repository.InquiryRepository;
import com.nanacocoa.server.support.service.FaqCatalog;
import com.nanacocoa.server.support.service.SupportService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static com.nanacocoa.server.CsrfRequests.csrf;
import static com.nanacocoa.server.common.exception.ErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never", "spring.jpa.open-in-view=false", "cart.cleanup.initial-delay=1h",
        "product-image.storage-mode=mock", "toss-payments.enabled=false"})
class CustomerSupportIntegrationTest {
    static final String API = "/api/support/inquiries";
    static final String ADMIN = "/api/admin/support/inquiries";
    static final String PASSWORD = "SyntheticSupport123!";

    @DynamicPropertySource static void database(DynamicPropertyRegistry values) {
        String mysql = System.getenv("SUPPORT_TEST_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("Loopback test database required");
        }
        values.add("spring.datasource.url", () -> mysql == null
                ? "jdbc:h2:mem:support;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
        values.add("spring.datasource.driver-class-name", () -> mysql == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        values.add("spring.datasource.username", () -> mysql == null ? "sa" : "root");
        values.add("spring.datasource.password", () -> "");
    }

    @Autowired SupportService service;
    @Autowired InquiryRepository inquiries;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate sql;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    Member owner, stranger, admin;

    @BeforeEach void fixture() {
        owner = member(false);
        stranger = member(false);
        admin = member(true);
    }

    Member member(boolean admin) {
        var member = Member.builder().email(UUID.randomUUID()+"@example.test").name("합성 회원")
                .password(passwords.encode(PASSWORD)).build();
        member.setAdmin(admin);
        return members.saveAndFlush(member);
    }
    UserDetailsImpl user(Member m) { return new UserDetailsImpl(m, m.getEmail()); }
    String key() { return UUID.randomUUID().toString(); }
    MockHttpSession login(Member m) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf(mvc))
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", m.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    String order(Member member) {
        String number = "NC_" + key().replace("-", "");
        sql.update("insert into orders (order_id,member_id,total_amount,status,version,created_at,updated_at) values (?,?,42000,'CREATED',0,current_timestamp,current_timestamp)",
                number, member.getId());
        return number;
    }

    @Test void publicFaqUsesPolicyLinkAndCatalogDistinguishesEmptyFromInvalid() throws Exception {
        mvc.perform(get("/api/support/faq")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[2].policyHref").value("policies.html?document=SHIPPING_RETURN"));
        var empty = new FaqCatalog(json, new ByteArrayResource("[]".getBytes(StandardCharsets.UTF_8)));
        assertThat(empty.entries()).isEmpty();
        var invalid = new FaqCatalog(json, new ByteArrayResource("{".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(invalid::entries).isInstanceOfSatisfying(NanacocoaException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FAQ_UNAVAILABLE));
    }

    @Test void memberContactValidatesOrderOwnerAndInputWithoutChangingOrder() throws Exception {
        String own = order(owner), foreign = order(stranger);
        var session = login(owner);
        long count = inquiries.count();
        for (String rejected : List.of(foreign, "missing", own.toLowerCase(Locale.ROOT))) {
            mvc.perform(post(API).session(session).with(csrf(mvc)).header("Idempotency-Key", key())
                    .contentType("application/json").content(json.writeValueAsString(new SupportRequests.Create("제목","본문",rejected))))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_01"));
        }
        mvc.perform(post(API).session(session).with(csrf(mvc)).header("Idempotency-Key", key())
                .contentType("application/json").content(json.writeValueAsString(new SupportRequests.Create(" ", "본문", null))))
                .andExpect(status().isBadRequest());
        assertThat(inquiries.count()).isEqualTo(count);
        mvc.perform(post(API).session(session).with(csrf(mvc)).header("Idempotency-Key", key())
                .contentType("application/json").content(json.writeValueAsString(new SupportRequests.Create(" 질문 ", "첫 줄\n둘째 줄", own))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.orderId").value(own))
                .andExpect(jsonPath("$.data.body").value("첫 줄\n둘째 줄"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sql.queryForObject("select status from orders where order_id=?", String.class, own)).isEqualTo("CREATED");
    }

    @Test void concurrentSameKeyReturnsOneInquiryAndChangedPayloadConflicts() throws Exception {
        String id = key();
        var payload = new SupportRequests.Create(" 회신 ", "내용", null);
        var workers = Executors.newFixedThreadPool(2);
        try {
            Callable<com.nanacocoa.server.support.dto.response.SupportResponses.Receipt> job =
                    () -> service.create(payload, id, null, user(owner));
            var results = workers.invokeAll(List.of(job, job));
            var first = results.get(0).get();
            var second = results.get(1).get();
            assertThat(first.inquiry().id()).isEqualTo(second.inquiry().id());
            assertThat(List.of(first.replayed(), second.replayed())).containsExactlyInAnyOrder(true, false);
            assertThatThrownBy(() -> service.create(new SupportRequests.Create("다른 내용", "본문", null), id, null, user(owner)))
                    .isInstanceOfSatisfying(NanacocoaException.class,e -> assertThat(e.getErrorCode()).isEqualTo(SUPPORT_REQUEST_CONFLICT));
        } finally { workers.shutdownNow(); }
    }

    @Test void ownListPaginatesAndCrossMemberAccessMatchesMissing() throws Exception {
        for (int n = 0; n < 21; n++) service.create(new SupportRequests.Create("문의 " + n, "본문", null), key(), null, user(owner));
        var detail = service.own("0", user(owner));
        assertThat(detail.items()).hasSize(20);
        assertThat(detail.hasNext()).isTrue();
        assertThat(service.own("1", user(owner)).items()).hasSize(1);
        var other = login(stranger);
        for (String id : List.of(detail.items().get(0).id().toString(), "9223372036854775807")) {
            mvc.perform(get(API + "/" + id).session(other)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SUPPORT_02"));
        }
        mvc.perform(get(API).session(other)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test void writesRequireSessionCsrfAndMatchingAccount() throws Exception {
        var session = login(owner);
        String payload = json.writeValueAsString(new SupportRequests.Create("제목", "본문", null));
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(post(API).session(session).header("Idempotency-Key",key()).contentType("application/json").content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post(API).session(session).with(csrf(mvc)).header("Idempotency-Key",key())
                .header("X-Checkout-Account",stranger.checkoutAccount()).contentType("application/json").content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPORT_05"));
        session.invalidate();
        mvc.perform(post(API).with(csrf(mvc)).header("Idempotency-Key",key()).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        assertThat(service.own("0", user(owner)).items()).isEmpty();
    }

    @Test void operatorsCanAnswerOnlyOnceAndOwnerSeesConfirmedReply() throws Exception {
        var open = service.create(new SupportRequests.Create("질문", "본문", null), key(), null, user(owner)).inquiry();
        String id = open.id().toString();
        transactions.executeWithoutResult(status -> {
            service.reply(id, new SupportRequests.Reply("롤백될 답변"), null, user(admin));
            inquiries.flush();
            status.setRollbackOnly();
        });
        assertThat(service.ownDetail(id,user(owner)).replyBody()).isNull();
        assertThat(service.ownDetail(id,user(owner)).status().name()).isEqualTo("PENDING");
        var session = login(owner);
        mvc.perform(get(ADMIN).session(session)).andExpect(status().isForbidden());
        mvc.perform(post(ADMIN+"/"+id+"/reply").session(session).with(csrf(mvc)).contentType("application/json")
                .content("{\"body\":\"권한 없는 답변\"}")).andExpect(status().isForbidden());
        assertThat(service.adminList("PENDING","0",user(admin)).items()).anyMatch(row -> row.id().equals(open.id()));
        var workers=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> reply=()->{
                try { service.reply(id,new SupportRequests.Reply("확정 답변"),null,user(admin));return true; }
                catch(NanacocoaException e){assertThat(e.getErrorCode()).isEqualTo(SUPPORT_REPLY_CONFLICT);return false;}
            };
            var results=workers.invokeAll(List.of(reply,reply));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(true,false);
        } finally { workers.shutdownNow(); }
        mvc.perform(get(API + "/" + id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED")).andExpect(jsonPath("$.data.replyBody").value("확정 답변"))
                .andExpect(jsonPath("$.data.repliedBy").doesNotExist());
        assertThat(service.adminList("ANSWERED","0",user(admin)).items()).anyMatch(row -> row.id().equals(open.id()));
    }
}
