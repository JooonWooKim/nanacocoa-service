package com.nanacocoa.server.common.session;

import com.nanacocoa.server.common.exception.NanacocoaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;

import static com.nanacocoa.server.common.exception.ErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileVerificationSessionTest {
    private ProfileVerificationSession verification;
    private Clock clock;
    private final Instant start = Instant.parse("2026-09-26T00:00:00Z");

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start);
        verification = new ProfileVerificationSession(new MockHttpServletRequest(), clock);
    }

    @Test
    void expiresAtTenMinutesWithoutSlidingOnRead() {
        verification.verify("member", "encoded-password", () -> {});
        when(clock.instant()).thenReturn(start.plusSeconds(599));
        verification.requireVerified("member", "encoded-password");
        when(clock.instant()).thenReturn(start.plusSeconds(600));
        assertRequired("member", "encoded-password");
    }

    @Test
    void rejectsDifferentIdentityOrChangedCredentialAndExplicitClear() {
        verification.verify("member", "encoded-password", () -> {});
        assertRequired("other-member", "encoded-password");
        verification.verify("member", "encoded-password", () -> {});
        assertRequired("member", "changed-hash");
        verification.verify("member", "encoded-password", () -> {});
        verification.clear();
        assertRequired("member", "encoded-password");
    }

    @Test
    void retryWindowBlocksPasswordChecksAndAllowsRetryAfterFiveMinutes() {
        Runnable wrong = () -> { throw new NanacocoaException(NOT_VALID_PASSWORD); };
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> verification.verify("member", "hash", wrong))
                    .isInstanceOf(NanacocoaException.class);
        }
        Runnable passwordCheck = mock(Runnable.class);
        assertThatThrownBy(() -> verification.verify("member", "hash", passwordCheck))
                .isInstanceOfSatisfying(NanacocoaException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(PROFILE_VERIFICATION_LOCKED));
        verifyNoInteractions(passwordCheck);
        when(clock.instant()).thenReturn(start.plusSeconds(300));
        verification.verify("member", "hash", passwordCheck);
        verify(passwordCheck).run();
        verification.requireVerified("member", "hash");
    }

    private void assertRequired(String email, String credential) {
        assertThatThrownBy(() -> verification.requireVerified(email, credential))
                .isInstanceOfSatisfying(NanacocoaException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(PROFILE_VERIFICATION_REQUIRED));
    }
}
