package com.nanacocoa.server.policy.service;

import com.nanacocoa.server.policy.entity.ConsentMode;
import java.time.OffsetDateTime;
import java.util.List;

public record PolicyCatalog(long revision, List<Document> documents, List<Document> archivedDocuments) {
    public PolicyCatalog(long revision, List<Document> documents) { this(revision, documents, List.of()); }
    public PolicyCatalog {
        archivedDocuments = archivedDocuments == null ? List.of() : archivedDocuments;
    }
    public record Document(String documentCode, String version, String title, String body,
                           ConsentMode consentMode, OffsetDateTime effectiveAt,
                           String approvedBy, OffsetDateTime approvedAt) {}
}
