package com.nanacocoa.server.recovery.service;

import com.nanacocoa.server.recovery.repository.RecoveryLimitRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RecoveryAdmissionService {
    private final RecoveryLimitRepository limits;
    private final Clock clock;
    public record Admission(boolean ipAllowed, boolean emailAllowed) {}

    @Transactional
    public Admission acquire(String email, String ip) {
        var now = LocalDateTime.now(clock);
        // 항상 IP -> 이메일 순서로 잠근다. 계정 조회는 수행하지 않는다.
        String ipKey = "ip:" + hash(ip == null ? "unknown" : ip);
        limits.ensureRow(ipKey, now);
        if (!limits.lockByKey(ipKey).acquire(now, 20, 0)) return new Admission(false, false);
        String emailKey = "email:" + hash(email.trim().toLowerCase(Locale.ROOT));
        limits.ensureRow(emailKey, now);
        return new Admission(true, limits.lockByKey(emailKey).acquire(now, 5, 60));
    }

    @Transactional @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void cleanExpired() { limits.deleteExpired(LocalDateTime.now(clock).minusHours(2)); }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
