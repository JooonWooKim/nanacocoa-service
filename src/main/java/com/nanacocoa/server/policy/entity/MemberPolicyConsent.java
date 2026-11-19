package com.nanacocoa.server.policy.entity;

import com.nanacocoa.server.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "member_policy_consents", uniqueConstraints = @UniqueConstraint(name = "uk_member_policy_consent", columnNames = {"member_id", "document_id"}))
@Getter @NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MemberPolicyConsent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "member_id", updatable = false) private Member member;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id", updatable = false) private PolicyDocument document;
    @Column(nullable = false, updatable = false) private boolean agreed;
    @Column(name = "recorded_at", nullable = false, updatable = false) private LocalDateTime recordedAt;
    public MemberPolicyConsent(Member member, PolicyDocument document, boolean agreed) {
        this.member = member; this.document = document; this.agreed = agreed;
        this.recordedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
