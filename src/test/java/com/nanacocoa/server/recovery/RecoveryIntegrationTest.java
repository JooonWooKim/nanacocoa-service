package com.nanacocoa.server.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.recovery.config.RecoveryProperties;
import com.nanacocoa.server.recovery.dto.reqeust.RecoveryRequests;
import com.nanacocoa.server.recovery.mail.*;
import com.nanacocoa.server.recovery.repository.*;
import com.nanacocoa.server.recovery.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import static com.nanacocoa.server.CsrfRequests.csrf;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test") @AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.sql.init.mode=never", "spring.jpa.open-in-view=false", "recovery.enabled=true", "recovery.mode=mock",
    "recovery.base-url=https://recovery.example.test", "recovery.queue-capacity=2", "cart.cleanup.initial-delay=1h"})
class RecoveryIntegrationTest {
    static final String ROOT = "/api/auth/password-recovery";
    static final String BEFORE = "BeforeRecovery123!", AFTER = "AfterRecovery123!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("RECOVERY_TEST_DB_URL");
        r.add("spring.datasource.url", () -> url == null ? "jdbc:h2:mem:recovery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : url);
        r.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username", () -> url == null ? "sa" : "root");
        r.add("spring.datasource.password", () -> "");
    }
    @Autowired RecoveryService service;
    @Autowired RecoveryTokenService tokens;
    @Autowired RecoveryAdmissionService admission;
    @Autowired RecoveryProperties properties;
    @Autowired MemberRepository members;
    @MockitoSpyBean RecoveryTokenRepository tokenRows;
    @Autowired RecoveryLimitRepository limitRows;
    @MockitoSpyBean RecoveryMailSender mail;
    @MockitoBean Clock clock;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @MockitoSpyBean com.nanacocoa.server.member.service.AuthenticationGenerationService generations;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("recoveryExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor;
    String email, ip;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-22T12:00:00Z")); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        email = UUID.randomUUID()+"@example.test"; ip = UUID.randomUUID().toString();
        members.saveAndFlush(Member.builder().email(email).name("가상 복구 회원").password(passwords.encode(BEFORE)).build());
        properties.setEnabled(true);
        ((MockRecoveryMailSender)mail).clear();
    }
    @AfterEach void drain() throws Exception {
        for(int i=0;i<200 && (executor.getActiveCount()>0 || executor.getQueueSize()>0);i++) Thread.sleep(10);
        assertThat(executor.getActiveCount()).isZero();
    }
    RecoveryRequests.Reset reset(String token) { return new RecoveryRequests.Reset(token, AFTER, AFTER); }
    void at(String instant) { when(clock.instant()).thenReturn(Instant.parse(instant)); }
    MockHttpSession login(String password) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email",email,"password",password))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    void awaitDelivery() throws Exception {
        for (int i=0;i<100 && ((MockRecoveryMailSender)mail).deliveries().isEmpty();i++) Thread.sleep(20);
        assertThat(((MockRecoveryMailSender)mail).deliveries()).hasSize(1);
    }

    @Test void anonymousRequestsHideAccountExistenceAndUseTrustedFragmentLink() throws Exception {
        String known = mvc.perform(post(ROOT+"/requests").with(csrf(mvc)).with(r->{r.setRemoteAddr(ip);return r;})
                .header("Host","attacker.invalid").contentType("application/json").content(json.writeValueAsString(Map.of("email",email))))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post(ROOT+"/requests").with(csrf(mvc)).with(r->{r.setRemoteAddr(ip);return r;})
                .contentType("application/json").content("{\"email\":\"absent-"+email+"\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown).doesNotContain(email);
        awaitDelivery();
        var delivery = ((MockRecoveryMailSender)mail).deliveries().get(0);
        assertThat(delivery.link()).startsWith("https://recovery.example.test/password-reset.html#token=");
        String raw = delivery.link().split("#token=")[1];
        assertThat(raw).hasSize(43);
        assertThat(tokenRows.findByTokenHash(RecoveryAdmissionService.hash(raw))).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from password_recovery_tokens where token_hash=?",Long.class,raw)).isZero();
    }
    @Test void resetConsumesOnceChangesPasswordAndInvalidatesAllSessions() throws Exception {
        var a=login(BEFORE);var b=login(BEFORE);String token=tokens.issue(email).token();
        mvc.perform(post(ROOT+"/reset").with(csrf(mvc)).contentType("application/json").content(json.writeValueAsString(reset(token))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/session").session(a)).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/cart").session(b)).andExpect(status().isUnauthorized());
        assertThat(members.findByEmail(email).orElseThrow().getAuthenticationGeneration()).isEqualTo(1);
        assertThatThrownBy(()->tokens.reset(reset(token))).isInstanceOf(NanacocoaException.class);
        login(AFTER);
        assertThat(passwords.matches(BEFORE,members.findByEmail(email).orElseThrow().getPassword())).isFalse();
    }
    @Test void expiresAtBoundaryAndReissueRevokesEarlierLink() {
        String first=tokens.issue(email).token(), second=tokens.issue(email).token();
        assertThatThrownBy(()->tokens.reset(reset(first))).isInstanceOf(NanacocoaException.class);
        at("2026-11-22T12:30:00Z");
        assertThatThrownBy(()->tokens.reset(reset(second))).isInstanceOf(NanacocoaException.class);
        assertThat(members.findByEmail(email).orElseThrow().getAuthenticationGeneration()).isZero();
    }
    @Test void concurrentSameTokenSucceedsExactlyOnce() throws Exception {
        String raw=tokens.issue(email).token();var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task=()->{try{tokens.reset(reset(raw));return true;}catch(NanacocoaException e){return false;}};
            var results=pool.invokeAll(List.of(task,task));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(true,false);
            assertThat(members.findByEmail(email).orElseThrow().getAuthenticationGeneration()).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void dbFailureAfterPasswordMutationRollsBackPasswordGenerationAndToken() {
        String raw=tokens.issue(email).token();var id=members.findByEmail(email).orElseThrow().getId();
        var row=tokenRows.findByTokenHash(RecoveryAdmissionService.hash(raw)).orElseThrow();
        doReturn(List.of(row)).doThrow(new DataAccessResourceFailureException("synthetic database failure"))
                .when(tokenRows).findByMemberIdAndConsumedFalse(id);
        assertThatThrownBy(()->tokens.reset(reset(raw))).isInstanceOf(DataAccessResourceFailureException.class);
        var member=members.findByEmail(email).orElseThrow();
        assertThat(member.getAuthenticationGeneration()).isZero();assertThat(passwords.matches(BEFORE,member.getPassword())).isTrue();
        assertThat(tokenRows.findByTokenHash(RecoveryAdmissionService.hash(raw)).orElseThrow().isConsumed()).isFalse();
    }
    @Test void credentialChangeRejectsPreviouslyIssuedTokenWithoutChangingSessionGeneration() {
        String raw=tokens.issue(email).token();var m=members.findByEmail(email).orElseThrow();
        m.changePassword(passwords.encode("OrdinaryChange123!"));members.saveAndFlush(m);
        assertThatThrownBy(()->tokens.reset(reset(raw))).isInstanceOf(NanacocoaException.class);
        assertThat(members.findByEmail(email).orElseThrow().getAuthenticationGeneration()).isZero();
    }
    @Test void validationCsrfAndInvalidTokensCannotChangeCredentials() throws Exception {
        mvc.perform(post(ROOT+"/requests").contentType("application/json").content("{\"email\":\"x@example.test\"}"))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post(ROOT+"/requests").with(csrf(mvc)).contentType("application/json").content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        String raw=tokens.issue(email).token();
        assertThatThrownBy(()->tokens.reset(new RecoveryRequests.Reset(raw,"        ","        "))).isInstanceOf(NanacocoaException.class);
        assertThatThrownBy(()->tokens.reset(new RecoveryRequests.Reset(raw,null,null))).isInstanceOf(NanacocoaException.class);
        assertThatThrownBy(()->tokens.reset(new RecoveryRequests.Reset(raw,"a".repeat(73),"a".repeat(73)))).isInstanceOf(NanacocoaException.class);
        assertThatThrownBy(()->tokens.reset(new RecoveryRequests.Reset(raw,"가".repeat(25),"가".repeat(25)))).isInstanceOf(NanacocoaException.class);
        assertThatThrownBy(()->tokens.reset(new RecoveryRequests.Reset(raw,AFTER,BEFORE))).isInstanceOf(NanacocoaException.class);
        for(String bad:List.of("bad","z".repeat(43))) {
            mvc.perform(post(ROOT+"/reset").with(csrf(mvc)).contentType("application/json").content(json.writeValueAsString(reset(bad))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RECOVERY_01"));
        }
        mvc.perform(get("/password-reset.html")).andExpect(status().isOk()).andExpect(header().string("Referrer-Policy","no-referrer"));
        assertThat(tokenRows.findByTokenHash(RecoveryAdmissionService.hash(raw)).orElseThrow().isConsumed()).isFalse();
    }
    @Test void emailAndIpLimitsArePersistentAndTimeBound() {
        assertThat(admission.acquire(email,ip).emailAllowed()).isTrue();
        assertThat(admission.acquire(email,ip).emailAllowed()).isFalse();
        for(int i=1;i<5;i++){at("2026-11-22T12:0"+i+":00Z");assertThat(admission.acquire(email,ip).emailAllowed()).isTrue();}
        at("2026-11-22T12:06:00Z");assertThat(admission.acquire(email,ip).emailAllowed()).isFalse();
        for(int i=0;i<14;i++)admission.acquire(UUID.randomUUID()+"@example.test",ip);
        assertThat(admission.acquire(email,ip).ipAllowed()).isFalse();
        at("2026-11-22T13:00:00Z");assertThat(admission.acquire(email,ip).emailAllowed()).isTrue();
        assertThat(limitRows.count()).isPositive();
    }
    @Test void concurrentEmailAdmissionAllowsOnlyOneAndStoresNoRawIdentifiers() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try {
            var result=pool.invokeAll(List.of(()->admission.acquire(email,ip).emailAllowed(),()->admission.acquire(email,ip+"other").emailAllowed()));
            assertThat(List.of(result.get(0).get(),result.get(1).get())).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForList("select limit_key from password_recovery_limits",String.class)).noneMatch(x->x.contains(email)||x.contains(ip));
        } finally {pool.shutdownNow();}
    }
    @Test void delayedSmtpDoesNotBlockAcceptanceAndQueueSaturationIsUniform() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();release.await(5,TimeUnit.SECONDS);return null;}).when(mail).send(anyString(),anyString());
        String other=UUID.randomUUID()+"@example.test";members.saveAndFlush(Member.builder().email(other).name("가상").password("encoded").build());
        try {
            service.request(new RecoveryRequests.Request(email),ip);
            service.request(new RecoveryRequests.Request(other),ip+"2");
            assertThat(entered.await(5,TimeUnit.SECONDS)).withFailMessage("Mail worker failures: %s",metrics.find("recovery.mail.failed").counters().stream().map(c->c.getId().getTags()).toList()).isTrue();
            assertThatThrownBy(()->service.request(new RecoveryRequests.Request("absent@example.test"),ip+"3"))
                    .isInstanceOfSatisfying(NanacocoaException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("RECOVERY_UNAVAILABLE"));
        } finally {release.countDown();Thread.sleep(100);}
    }
    @Test void mailFailureDoesNotRollBackIssuedTokenOrExposeFailure() throws Exception {
        var invoked=new CountDownLatch(1);
        doAnswer(call->{invoked.countDown();throw new IllegalStateException("synthetic SMTP failure");}).when(mail).send(anyString(),anyString());
        service.request(new RecoveryRequests.Request(email),ip);
        assertThat(invoked.await(5,TimeUnit.SECONDS)).withFailMessage("Worker counters: %s",metrics.find("recovery.mail.failed").counters().stream().map(c->c.getId().getTags()+"="+c.count()).toList()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from password_recovery_tokens where member_id=? and consumed=false",Long.class,
                members.findByEmail(email).orElseThrow().getId())).isEqualTo(1);
    }
    @Test void disabledRecoveryIsUniformForKnownAndUnknownAccounts() throws Exception {
        properties.setEnabled(false);
        try {
            for(String candidate:List.of(email,"absent@example.test"))
                mvc.perform(post(ROOT+"/requests").with(csrf(mvc)).contentType("application/json").content(json.writeValueAsString(Map.of("email",candidate))))
                        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RECOVERY_03"));
        } finally {properties.setEnabled(true);}
    }
    @Test void adminSessionsAndSessionsWithoutGenerationCannotSurviveRecovery() throws Exception {
        var m=members.findByEmail(email).orElseThrow();m.setAdmin(true);members.saveAndFlush(m);
        var admin=login(BEFORE);var legacy=login(BEFORE);
        var context=(org.springframework.security.core.context.SecurityContext)legacy.getAttribute("SPRING_SECURITY_CONTEXT");
        org.springframework.test.util.ReflectionTestUtils.setField(context.getAuthentication().getPrincipal(),"authenticationGeneration",null);
        mvc.perform(get("/api/cart").session(legacy)).andExpect(status().isUnauthorized());
        tokens.reset(reset(tokens.issue(email).token()));
        mvc.perform(get("/api/cart").session(admin)).andExpect(status().isUnauthorized());
    }
    @Test void generationLookupFailureDoesNotTrustExistingSession() throws Exception {
        var session=login(BEFORE);
        doThrow(new DataAccessResourceFailureException("synthetic unavailable")).when(generations).isCurrent(anyLong(),anyLong());
        mvc.perform(get("/api/cart").session(session)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_05"));
    }
    @Test void concurrentReissueLeavesExactlyOneUsableLink() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try {
            var work=pool.invokeAll(List.<Callable<String>>of(()->tokens.issue(email).token(),()->tokens.issue(email).token()));
            int successes=0;
            for(var result:work)try{tokens.reset(reset(result.get()));successes++;}catch(NanacocoaException expected){}
            assertThat(successes).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
}
