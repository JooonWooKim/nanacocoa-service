package com.nanacocoa.server.policy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "policy_publications") @Getter @NoArgsConstructor
public class PolicyPublication {
    @Id private Long id = 1L;
    @Column(nullable = false) private long revision;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash = "";
    public void publish(long revision, String hash) { this.revision = revision; this.contentHash = hash; }
}
