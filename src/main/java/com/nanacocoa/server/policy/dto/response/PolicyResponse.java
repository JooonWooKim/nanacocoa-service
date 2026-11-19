package com.nanacocoa.server.policy.dto.response;

import com.nanacocoa.server.policy.service.PolicyCatalog;
import java.time.OffsetDateTime;
import java.util.List;

public record PolicyResponse(String documentCode, String version, String title, OffsetDateTime effectiveAt,
                             String consentMode, String bodyPath, String body) {
    public static PolicyResponse from(PolicyCatalog.Document d, boolean includeBody) {
        return new PolicyResponse(d.documentCode(), d.version(), d.title(),
                d.effectiveAt(), d.consentMode().name(),
                "/api/policies/" + d.documentCode() + "/" + d.version(), includeBody ? d.body() : null);
    }
    public record Catalog(long revision, boolean signupReady, List<PolicyResponse> documents) {}
}
