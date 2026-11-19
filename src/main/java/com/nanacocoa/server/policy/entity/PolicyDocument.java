package com.nanacocoa.server.policy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_documents", uniqueConstraints = @UniqueConstraint(name = "uk_policy_document_version", columnNames = {"document_code", "document_version"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PolicyDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "document_code", nullable = false, length = 64, updatable = false) private String documentCode;
    @Column(name = "document_version", nullable = false, length = 64, updatable = false) private String documentVersion;
    @Column(nullable = false, length = 200, updatable = false) private String title;
    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String body;
    @Enumerated(EnumType.STRING) @Column(name = "consent_mode", nullable = false, length = 32, updatable = false) private ConsentMode consentMode;
    @Column(name = "effective_at", nullable = false, updatable = false) private LocalDateTime effectiveAt;
    @Column(name = "approved_by", nullable = false, length = 100, updatable = false) private String approvedBy;
    @Column(name = "approved_at", nullable = false, updatable = false) private LocalDateTime approvedAt;
    @Column(name = "content_hash", nullable = false, length = 64, updatable = false) private String contentHash;
    @Column(nullable = false) private boolean active;

    public PolicyDocument(String code, String version, String title, String body, ConsentMode mode,
                          LocalDateTime effectiveAt, String approvedBy, LocalDateTime approvedAt, String hash) {
        this.documentCode = code; this.documentVersion = version; this.title = title; this.body = body;
        this.consentMode = mode; this.effectiveAt = effectiveAt; this.approvedBy = approvedBy;
        this.approvedAt = approvedAt; this.contentHash = hash;
    }
    public void activate(boolean active) { this.active = active; }
}
