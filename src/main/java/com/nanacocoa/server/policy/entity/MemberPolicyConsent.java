package com.nanacocoa.server.policy.entity;

import com.nanacocoa.server.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "member_policy_consents", uniqueConstraints = @UniqueConstraint(name = "uk_member_policy_version", columnNames = {"member_id", "document_code", "document_version"}))
@Getter @NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MemberPolicyConsent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "member_id", updatable = false) private Member member;
    // 기존 FK 값은 보존하되 신규 이력은 파일의 식별자와 해시로 기록한다.
    @Column(name = "document_id", updatable = false) private Long legacyDocumentId;
    @Column(name = "document_code", nullable = false, length = 64, updatable = false) private String documentCode;
    @Column(name = "document_version", nullable = false, length = 64, updatable = false) private String documentVersion;
    @Column(name = "content_hash", nullable = false, length = 64, updatable = false) private String contentHash;
    @Column(nullable = false, updatable = false) private boolean agreed;
    @Column(name = "recorded_at", nullable = false, updatable = false) private LocalDateTime recordedAt;
    public MemberPolicyConsent(Member member, String code, String version, String hash, boolean agreed) {
        this.member = member; this.documentCode = code; this.documentVersion = version; this.contentHash = hash; this.agreed = agreed;
        this.recordedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
