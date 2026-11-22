package com.nanacocoa.server.recovery.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "password_recovery_tokens")
@Getter @NoArgsConstructor
public class RecoveryToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false, length = 64, unique = true) private String tokenHash;
    @Column(nullable = false, length = 100) private String credentialHash;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean consumed;

    public RecoveryToken(Long memberId, String tokenHash, String credentialHash, LocalDateTime expiresAt) {
        this.memberId = memberId; this.tokenHash = tokenHash;
        this.credentialHash = credentialHash; this.expiresAt = expiresAt;
    }
    public void consume() { consumed = true; }
}
