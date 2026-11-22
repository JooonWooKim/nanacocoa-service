package com.nanacocoa.server.recovery.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "password_recovery_limits")
@Getter @NoArgsConstructor
public class RecoveryLimit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 80, unique = true) private String limitKey;
    @Column(nullable = false) private LocalDateTime windowStart;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private LocalDateTime nextAllowedAt;

    public boolean acquire(LocalDateTime now, int maximum, int intervalSeconds) {
        if (!now.isBefore(windowStart.plusHours(1))) { windowStart = now; attempts = 0; }
        if (attempts >= maximum || now.isBefore(nextAllowedAt)) return false;
        attempts++; nextAllowedAt = now.plusSeconds(intervalSeconds);
        return true;
    }
}
