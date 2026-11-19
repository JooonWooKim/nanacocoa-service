package com.nanacocoa.server.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.dto.reqeust.SignupRequest;
import com.nanacocoa.server.member.entity.*;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.member.service.AuthService;
import com.nanacocoa.server.policy.dto.reqeust.PolicyConsentRequest;
import com.nanacocoa.server.policy.repository.*;
import com.nanacocoa.server.policy.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static com.nanacocoa.server.CsrfRequests.csrf;
import static com.nanacocoa.server.common.exception.ErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test") @AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {"policy.signup-enabled=true", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.datasource.url=jdbc:h2:mem:policy-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
class PolicyIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("POLICY_TEST_DB_URL");
        if (url != null) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.datasource.username", () -> "root");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PolicyPublishingService publishing;
    @Autowired PolicyService service;
    @Autowired PolicyReadiness readiness;
    @Autowired com.nanacocoa.server.policy.config.PolicyCatalogLoader loader;
    @Autowired PolicyPublicationRepository publications;
    @Autowired PolicyDocumentRepository documents;
    @Autowired MemberRepository members;
    @Autowired AuthService auth;
    @Autowired TransactionTemplate tx;
    @MockitoSpyBean MemberPolicyConsentRepository consents;
    private PolicyCatalog fixture;

    @BeforeEach void setup() throws Exception {
        consents.deleteAll(); members.deleteAll(); documents.deleteAll(); publications.deleteAll();
        publishing.initialize();
        try (var input = new ClassPathResource("policies/approved-test-catalog.json").getInputStream()) { fixture = json.readValue(input,PolicyCatalog.class); }
        publishing.publish(fixture); readiness.complete(true);
    }
    SignupRequest signup(String email, boolean receive) {
        return new SignupRequest(email,"fixture-password","테스트 회원","합성 주소","010-1111-2222",
                receive ? EmailReceiveConsent.Y : EmailReceiveConsent.N, PolicyTestFixtures.consents(receive));
    }
    String body(SignupRequest request) throws Exception { return json.writeValueAsString(request); }
    PolicyCatalog revision(long revision, String version, String changedBody) {
        return new PolicyCatalog(revision,fixture.documents().stream().map(d -> new PolicyCatalog.Document(d.documentCode(),
                version == null ? d.version() : version,d.title(),changedBody == null ? d.body() : changedBody,d.consentMode(),d.effectiveAt(),d.approvedBy(),d.approvedAt())).toList());
    }
    @Test void publicCatalogAndHistoricalDocumentExcludeInternalApprovalAndMemberData() throws Exception {
        mvc.perform(get("/api/policies")).andExpect(status().isOk()).andExpect(jsonPath("$.data.signupReady").value(true))
                .andExpect(jsonPath("$.data.documents.length()").value(5)).andExpect(jsonPath("$.data.documents[0].approvedBy").doesNotExist());
        publishing.publish(revision(2,"test-v2",null));
        mvc.perform(get("/api/policies/TERMS/test-v1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.body").value(fixture.documents().get(0).body()))
                .andExpect(jsonPath("$.data.approvedBy").doesNotExist());
        mvc.perform(get("/api/policies/TERMS/unpublished")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_03"));
        mvc.perform(get("/api/policies/UNKNOWN/test-v1")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_03"));
    }
    @Test void optionalRefusalCreatesMemberAndImmutableSignupHistory() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body(signup("refusal@example.test",false))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.message").value("회원가입성공"));
        var member = members.findByEmail("refusal@example.test").orElseThrow();
        assertThat(member.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.N);
        var records = consents.findByMemberId(member.getId());
        assertThat(records).hasSize(3); assertThat(records.stream().filter(c -> !c.isAgreed())).hasSize(1);
        member.setEmailReceiveConsent(EmailReceiveConsent.Y); members.saveAndFlush(member);
        assertThat(consents.findByMemberId(member.getId()).stream().filter(c -> !c.isAgreed())).hasSize(1);
    }
    @Test void missingRequiredRefusalDuplicateUnknownAndEmailMismatchAreRejected() throws Exception {
        var valid = PolicyTestFixtures.consents(false);
        var cases = List.of(List.<PolicyConsentRequest>of(),List.of(new PolicyConsentRequest("TERMS","test-v1",false),valid.get(1),valid.get(2)),
                List.of(valid.get(0),valid.get(0),valid.get(1),valid.get(2)),
                List.of(valid.get(0),valid.get(1),valid.get(2),new PolicyConsentRequest("UNKNOWN","v1",true)),PolicyTestFixtures.consents(true));
        for (var input : cases) {
            var request = signup("invalid@example.test",false);
            var object = json.valueToTree(request); ((com.fasterxml.jackson.databind.node.ObjectNode)object).set("consents",json.valueToTree(input));
            mvc.perform(post("/api/auth/signup").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(object.toString()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_01"));
        }
        assertThat(members.findByEmail("invalid@example.test")).isEmpty(); assertThat(consents.count()).isZero();
    }
    @Test void staleConsentVersionRequiresReconfirmationWithoutCreatingMember() throws Exception {
        publishing.publish(revision(2,"test-v2",null));
        mvc.perform(post("/api/auth/signup").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body(signup("stale@example.test",false))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_02"));
        assertThat(members.count()).isZero();
    }
    @Test void defaultEmptyCatalogDefersSignupAndPreservesExistingLogin() throws Exception {
        documents.deleteAll(); publications.deleteAll(); publishing.initialize(); publishing.publish(new PolicyCatalog(0,List.of()));
        mvc.perform(get("/api/policies")).andExpect(status().isOk()).andExpect(jsonPath("$.data.signupReady").value(false));
        mvc.perform(post("/api/auth/signup").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body(signup("deferred@example.test",false))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("POLICY_01"));
        assertThat(members.count()).isZero(); assertThat(consents.count()).isZero();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        members.saveAndFlush(Member.builder().email("legacy@example.test").name("기존 회원").password(encoder.encode("fixture-password")).build());
        mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"legacy@example.test\",\"password\":\"fixture-password\"}")).andExpect(status().isOk());
        assertThat(consents.count()).isZero();
    }
    @Test void malformedPublicationBlocksSignupButKeepsPublishedHistoryReadable() throws Exception {
        assertThatThrownBy(() -> publishing.publish(new PolicyCatalog(2,List.of()))).isInstanceOf(NanacocoaException.class);
        readiness.complete(false);
        mvc.perform(get("/api/policies")).andExpect(status().isOk()).andExpect(jsonPath("$.data.signupReady").value(false));
        mvc.perform(get("/api/policies/TERMS/test-v1")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/signup").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body(signup("blocked@example.test",false))))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void immutableVersionConflictRollsBackWholePublication() {
        var changed = new ArrayList<>(revision(2,null,"changed text").documents());
        var first = changed.get(0);
        changed.set(0,new PolicyCatalog.Document(first.documentCode(),"test-v2",first.title(),first.body(),first.consentMode(),first.effectiveAt(),first.approvedBy(),first.approvedAt()));
        assertThatThrownBy(() -> publishing.publish(new PolicyCatalog(2,changed))).isInstanceOf(NanacocoaException.class);
        assertThat(service.catalog().revision()).isEqualTo(1); assertThat(documents.count()).isEqualTo(5);
        assertThat(service.document("TERMS","test-v1").body()).isEqualTo(fixture.documents().get(0).body());
    }
    @Test void unapprovedCatalogIsNotImportedAndLoaderDefersOnlySignup() throws Exception {
        var invalid = new ArrayList<>(fixture.documents());
        var first = invalid.get(0);
        invalid.set(0,new PolicyCatalog.Document(first.documentCode(),"test-v2",first.title(),first.body(),first.consentMode(),first.effectiveAt(),"",first.approvedAt()));
        assertThatThrownBy(() -> publishing.publish(new PolicyCatalog(2,invalid))).isInstanceOf(NanacocoaException.class);
        org.springframework.test.util.ReflectionTestUtils.setField(loader,"location","classpath:policies/not-present.json");
        try { loader.run(new org.springframework.boot.DefaultApplicationArguments()); }
        finally { org.springframework.test.util.ReflectionTestUtils.setField(loader,"location","classpath:policies/catalog.json"); }
        assertThat(service.catalog().signupReady()).isFalse();
        assertThat(service.document("TERMS","test-v1").body()).isNotBlank();
        assertThat(documents.count()).isEqualTo(5);
    }
    @Test void explicitSignupPausePreservesPublishedDocumentsAndHistory() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(readiness,"signupEnabled",false);
        try {
            mvc.perform(get("/api/policies")).andExpect(status().isOk()).andExpect(jsonPath("$.data.signupReady").value(false));
            mvc.perform(post("/api/auth/signup").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body(signup("paused@example.test",false))))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/policies/TERMS/test-v1")).andExpect(status().isOk());
            assertThat(members.count()).isZero();
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(readiness,"signupEnabled",true); }
    }
    @Test void publicationRevisionCannotBeReusedOrMoveBackwards() {
        publishing.publish(revision(2,"test-v2",null)); publishing.publish(fixture); publishing.publish(new PolicyCatalog(0,List.of()));
        assertThat(service.catalog().revision()).isEqualTo(2);
        assertThatThrownBy(() -> publishing.publish(revision(2,"test-v3",null))).isInstanceOf(NanacocoaException.class);
        assertThat(documents.count()).isEqualTo(10);
    }
    @Test void consentStorageFailureRollsBackMemberCreation() {
        doThrow(new DataIntegrityViolationException("synthetic consent failure")).when(consents).saveAllAndFlush(any());
        assertThatThrownBy(() -> auth.signUp(signup("rollback@example.test",false))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(members.findByEmail("rollback@example.test")).isEmpty();
    }
    @Test void signupWithoutCsrfDoesNotCreateMemberOrHistory() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(signup("csrf@example.test",false))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(members.count()).isZero(); assertThat(consents.count()).isZero();
    }
    @Test void signupHoldsPublicationLockUntilConsentTransactionCompletes() throws Exception {
        var validated = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var signup = pool.submit(() -> tx.execute(status -> {
                service.validateSignup(PolicyTestFixtures.consents(false),EmailReceiveConsent.N); validated.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                auth.signUp(signup("concurrent@example.test",false)); return true;
            }));
            assertThat(validated.await(10,TimeUnit.SECONDS)).isTrue();
            var publication = pool.submit(() -> { started.countDown(); publishing.publish(revision(2,"test-v2",null)); });
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> publication.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); assertThat(signup.get(10,TimeUnit.SECONDS)).isTrue(); publication.get(10,TimeUnit.SECONDS);
            assertThat(service.catalog().revision()).isEqualTo(2);
            var member = members.findByEmail("concurrent@example.test").orElseThrow();
            assertThat(consents.findByMemberId(member.getId())).hasSize(3);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
