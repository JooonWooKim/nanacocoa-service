package com.nanacocoa.server.member;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.session.ProfileVerificationSession;
import com.nanacocoa.server.member.dto.reqeust.ChangeMemberPasswordRequest;
import com.nanacocoa.server.member.dto.reqeust.UpdateMemberProfileRequest;
import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.*;

import static com.nanacocoa.server.common.exception.ErrorCode.PROFILE_VERIFICATION_REQUIRED;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "payment.reconciliation.enabled=false"
})
class MemberConcurrencyIntegrationTest {
    private static final String EMAIL = "concurrent@example.com";
    @Autowired MemberRepository repository;
    @Autowired MemberService service;
    @Autowired PasswordEncoder encoder;
    @Autowired ProfileVerificationSession verification;
    @Autowired PlatformTransactionManager transactionManager;
    private String originalHash;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        originalHash = encoder.encode("current123!");
        repository.saveAndFlush(Member.builder().email(EMAIL).name("original")
                .password(originalHash).address("original address").build());
    }

    @Test
    void passwordFirstRejectsWaitingProfileWithStaleVerification() throws Exception {
        Throwable failure = runConcurrent(this::changePassword, this::updateProfile, false);
        assertThat(failure).isInstanceOfSatisfying(NanacocoaException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PROFILE_VERIFICATION_REQUIRED));
        Member result = repository.findByEmail(EMAIL).orElseThrow();
        assertThat(encoder.matches("changed123!", result.getPassword())).isTrue();
        assertThat(result.getName()).isEqualTo("original");
    }

    @Test
    void profileFirstPreservesBothChanges() throws Exception {
        assertThat(runConcurrent(this::updateProfile, this::changePassword, false)).isNull();
        Member result = repository.findByEmail(EMAIL).orElseThrow();
        assertThat(result.getName()).isEqualTo("updated");
        assertThat(result.getAddress()).isEqualTo("updated address");
        assertThat(encoder.matches("changed123!", result.getPassword())).isTrue();
    }

    @Test
    void rollbackReleasesLockAndWaitingPasswordChangeSucceeds() throws Exception {
        assertThat(runConcurrent(this::updateProfile, this::changePassword, true)).isNull();
        Member result = repository.findByEmail(EMAIL).orElseThrow();
        assertThat(result.getName()).isEqualTo("original");
        assertThat(encoder.matches("changed123!", result.getPassword())).isTrue();
    }

    private Throwable runConcurrent(Runnable first, Runnable second, boolean rollback) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        MockHttpSession firstSession = verifiedSession();
        MockHttpSession secondSession = verifiedSession();
        try {
            Future<?> owner = executor.submit(() -> withSession(firstSession, () ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        first.run();
                        repository.flush();
                        locked.countDown();
                        await(release);
                        if (rollback) status.setRollbackOnly();
                    })));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> waiter = executor.submit(() -> {
                try {
                    withSession(secondSession, () -> {
                        secondStarted.countDown();
                        second.run();
                    });
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // 첫 트랜잭션이 행을 소유하는 동안 두 번째 서비스는 완료될 수 없습니다.
            assertThatThrownBy(() -> waiter.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            owner.get(10, TimeUnit.SECONDS);
            return waiter.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MockHttpSession verifiedSession() {
        MockHttpSession session = new MockHttpSession();
        withSession(session, () -> verification.verify(EMAIL, originalHash, () -> {}));
        return session;
    }

    private void withSession(MockHttpSession session, Runnable action) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            action.run();
        } finally {
            attributes.requestCompleted();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private void updateProfile() {
        service.updateProfile(EMAIL, new UpdateMemberProfileRequest(
                EMAIL, "updated", "updated address", "010-3333-4444", EmailReceiveConsent.Y));
    }

    private void changePassword() {
        service.changePassword(EMAIL, new ChangeMemberPasswordRequest(
                "current123!", "changed123!", "changed123!"));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
