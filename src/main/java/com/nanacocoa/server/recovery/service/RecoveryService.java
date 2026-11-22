package com.nanacocoa.server.recovery.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.recovery.config.RecoveryProperties;
import com.nanacocoa.server.recovery.dto.reqeust.RecoveryRequests;
import com.nanacocoa.server.recovery.mail.RecoveryMailSender;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service
public class RecoveryService {
    public static final String ACCEPTED = "입력한 이메일로 복구 안내를 받을 수 있는 경우 메일이 발송됩니다.";
    private final RecoveryProperties properties;
    private final RecoveryAdmissionService admissions;
    private final RecoveryTokenService tokens;
    private final RecoveryMailSender mail;
    private final ThreadPoolTaskExecutor executor;
    private final MeterRegistry metrics;
    private final Semaphore capacity;

    public RecoveryService(RecoveryProperties properties, RecoveryAdmissionService admissions, RecoveryTokenService tokens,
            RecoveryMailSender mail, @Qualifier("recoveryExecutor") ThreadPoolTaskExecutor executor, MeterRegistry metrics) {
        this.properties = properties; this.admissions = admissions; this.tokens = tokens;
        this.mail = mail; this.executor = executor; this.metrics = metrics;
        capacity = new Semaphore(Math.max(1, properties.getQueueCapacity()));
    }

    public void request(RecoveryRequests.Request request, String remoteAddress) {
        requireEnabled();
        if (!capacity.tryAcquire()) throw new NanacocoaException(RECOVERY_UNAVAILABLE);
        try {
            var admission = admissions.acquire(request.email(), remoteAddress);
            if (!admission.ipAllowed()) throw new NanacocoaException(RECOVERY_LIMITED);
            // 이메일 제한 여부와 무관하게 같은 큐 경로로 접수한다.
            executor.execute(() -> {
                try {
                    if (!admission.emailAllowed()) return;
                    var issued = tokens.issue(request.email());
                    if (issued == null) return;
                    var base = properties.getBaseUrl().replaceAll("/+$", "");
                    mail.send(issued.recipient(), base + "/password-reset.html#token=" + issued.token());
                    metrics.counter("recovery.mail.sent").increment();
                } catch (RuntimeException failure) {
                    // SMTP/SQL 예외에는 이메일·토큰이 들어갈 수 있으므로 예외 원문을 기록하지 않는다.
                    metrics.counter("recovery.mail.failed", "type", failure.getClass().getSimpleName()).increment();
                } finally { capacity.release(); }
            });
        } catch (NanacocoaException e) { capacity.release(); throw e; }
        catch (RuntimeException e) { capacity.release(); throw new NanacocoaException(RECOVERY_UNAVAILABLE); }
    }

    public void reset(RecoveryRequests.Reset request) {
        requireEnabled();
        try { tokens.reset(request); }
        catch (NanacocoaException e) { throw e; }
        catch (RuntimeException e) { throw new NanacocoaException(RECOVERY_UNAVAILABLE); }
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !("mock".equals(properties.getMode()) || properties.isSmtpEnabled()))
            throw new NanacocoaException(RECOVERY_UNAVAILABLE);
    }
}
