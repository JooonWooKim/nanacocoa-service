package com.nanacocoa.server.policy.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.policy.dto.reqeust.PolicyConsentRequest;
import com.nanacocoa.server.policy.dto.response.PolicyResponse;
import com.nanacocoa.server.policy.entity.*;
import com.nanacocoa.server.policy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service @RequiredArgsConstructor
public class PolicyService {
    private final PolicyDocumentRepository documents;
    private final PolicyPublicationRepository publications;
    private final MemberPolicyConsentRepository consents;
    private final PolicyReadiness readiness;

    @Transactional
    public PolicyResponse.Catalog catalog() {
        var publication = publications.lockForSignup().orElseThrow(() -> new NanacocoaException(POLICY_UNAVAILABLE));
        var current = documents.findByActiveTrueOrderByDocumentCodeAsc();
        return new PolicyResponse.Catalog(publication.getRevision(), readiness.available() && publication.getRevision() > 0 && !current.isEmpty(),
                current.stream().map(d -> PolicyResponse.from(d,false)).toList());
    }
    @Transactional(readOnly = true)
    public PolicyResponse document(String code, String version) {
        return PolicyResponse.from(documents.findByDocumentCodeAndDocumentVersion(code,version)
                .orElseThrow(() -> new NanacocoaException(POLICY_NOT_FOUND)),true);
    }
    // AuthService의 트랜잭션에 참여한다. 공유 잠금은 회원·동의 저장이 끝날 때까지 유지한다.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<VerifiedConsent> validateSignup(List<PolicyConsentRequest> submitted, EmailReceiveConsent email) {
        var publication = publications.lockForSignup().orElseThrow(() -> new NanacocoaException(POLICY_UNAVAILABLE));
        if (!readiness.available() || publication.getRevision() == 0) throw new NanacocoaException(POLICY_UNAVAILABLE);
        var current = documents.findByActiveTrueOrderByDocumentCodeAsc();
        if (current.isEmpty()) throw new NanacocoaException(POLICY_UNAVAILABLE);
        if (submitted == null || email == null) throw new NanacocoaException(INVALID_REQUEST);
        Map<String,PolicyConsentRequest> inputs = new HashMap<>();
        for (var input : submitted) {
            if (input == null || input.documentCode() == null || input.version() == null || input.agreed() == null
                    || inputs.putIfAbsent(input.documentCode(),input) != null) throw new NanacocoaException(INVALID_REQUEST);
            var document = current.stream().filter(d -> d.getDocumentCode().equals(input.documentCode())).findFirst().orElse(null);
            if (document == null) throw new NanacocoaException(documents.existsByDocumentCode(input.documentCode()) ? POLICY_VERSION_CHANGED : INVALID_REQUEST);
            if (!document.getDocumentVersion().equals(input.version())) throw new NanacocoaException(POLICY_VERSION_CHANGED);
            if (document.getConsentMode() == ConsentMode.NONE) throw new NanacocoaException(INVALID_REQUEST);
        }
        List<VerifiedConsent> verified = new ArrayList<>();
        for (var document : current) {
            if (document.getConsentMode() == ConsentMode.NONE) continue;
            var input = inputs.get(document.getDocumentCode());
            if (input == null || (document.getConsentMode() == ConsentMode.REQUIRED && !input.agreed())
                    || (document.getConsentMode() == ConsentMode.OPTIONAL_EMAIL && input.agreed() != (email == EmailReceiveConsent.Y)))
                throw new NanacocoaException(INVALID_REQUEST);
            verified.add(new VerifiedConsent(document,input.agreed()));
        }
        return verified;
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordSignup(Member member, List<VerifiedConsent> verified) {
        consents.saveAllAndFlush(verified.stream().map(v -> new MemberPolicyConsent(member,v.document(),v.agreed())).toList());
    }
    public record VerifiedConsent(PolicyDocument document, boolean agreed) {}
}
