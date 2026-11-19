package com.nanacocoa.server.policy.dto.response;

import com.nanacocoa.server.policy.entity.PolicyDocument;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record PolicyResponse(String documentCode, String version, String title, OffsetDateTime effectiveAt,
                             String consentMode, String bodyPath, String body) {
    public static PolicyResponse from(PolicyDocument d, boolean includeBody) {
        return new PolicyResponse(d.getDocumentCode(), d.getDocumentVersion(), d.getTitle(),
                d.getEffectiveAt().atOffset(ZoneOffset.UTC), d.getConsentMode().name(),
                "/api/policies/" + d.getDocumentCode() + "/" + d.getDocumentVersion(), includeBody ? d.getBody() : null);
    }
    public record Catalog(long revision, boolean signupReady, List<PolicyResponse> documents) {}
}
