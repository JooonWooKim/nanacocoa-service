package com.nanacocoa.server.common.session;

import com.nanacocoa.server.common.exception.NanacocoaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static com.nanacocoa.server.common.exception.ErrorCode.PROFILE_VERIFICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.PROFILE_VERIFICATION_LOCKED;

@Component
public class ProfileVerificationSession {
    private static final String GRANT = ProfileVerificationSession.class.getName() + ".grant";
    private static final String ATTEMPTS = ProfileVerificationSession.class.getName() + ".attempts";
    private static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(5);
    private final HttpServletRequest request;
    private final Clock clock;

    @Autowired
    public ProfileVerificationSession(HttpServletRequest request) {
        this(request, Clock.systemUTC());
    }

    ProfileVerificationSession(HttpServletRequest request, Clock clock) {
        this.request = request;
        this.clock = clock;
    }

    public void verify(String email, String credential, Runnable passwordCheck) {
        HttpSession session = request.getSession();
        // 실제 세션을 잠가 병렬 요청으로 실패 횟수 제한을 우회하지 못하게 합니다.
        synchronized (session) {
            session.removeAttribute(GRANT);
            Instant now = clock.instant();
            Attempts attempts = (Attempts) session.getAttribute(ATTEMPTS);
            if (attempts == null || !now.isBefore(attempts.until())) {
                attempts = new Attempts(0, now.plus(RETRY_WINDOW));
            }
            if (attempts.count() >= 5) {
                throw new NanacocoaException(PROFILE_VERIFICATION_LOCKED);
            }
            session.setAttribute(ATTEMPTS, new Attempts(attempts.count() + 1, attempts.until()));
            passwordCheck.run();
            session.removeAttribute(ATTEMPTS);
            session.setAttribute(GRANT, new Grant(email, credential, now.plus(VALIDITY)));
        }
    }

    public void requireVerified(String email, String credential) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            synchronized (session) {
                Grant grant = (Grant) session.getAttribute(GRANT);
                if (grant != null && grant.email().equals(email)
                        && grant.credential().equals(credential) && clock.instant().isBefore(grant.until())) {
                    return;
                }
                session.removeAttribute(GRANT);
            }
        }
        throw new NanacocoaException(PROFILE_VERIFICATION_REQUIRED);
    }

    public void clear() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            synchronized (session) {
                session.removeAttribute(GRANT);
            }
        }
    }

    private record Grant(String email, String credential, Instant until) implements Serializable {}
    private record Attempts(int count, Instant until) implements Serializable {}
}
