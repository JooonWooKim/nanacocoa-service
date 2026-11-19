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
    private final PolicyFileStore store;
    private final MemberPolicyConsentRepository consents;
    private final PolicyReadiness readiness;

    public PolicyResponse.Catalog catalog() {
        var snapshot = store.snapshot();
        return new PolicyResponse.Catalog(snapshot.revision(), readiness.available() && snapshot.revision() > 0 && !snapshot.current().isEmpty(),
                snapshot.current().stream().map(d -> PolicyResponse.from(d, false)).toList());
    }
    public PolicyResponse document(String code, String version) {
        return PolicyResponse.from(store.snapshot().find(code, version)
                .orElseThrow(() -> new NanacocoaException(POLICY_NOT_FOUND)), true);
    }
    // 파일 스냅샷은 시작 시 고정된다. 회원과 이력 저장은 AuthService 트랜잭션에 참여한다.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<VerifiedConsent> validateSignup(List<PolicyConsentRequest> submitted, EmailReceiveConsent email) {
        var snapshot = store.snapshot();
        if (!readiness.available() || snapshot.revision() == 0) throw new NanacocoaException(POLICY_UNAVAILABLE);
        var current = snapshot.current();
        if (current.isEmpty()) throw new NanacocoaException(POLICY_UNAVAILABLE);
        if (submitted == null || email == null) throw new NanacocoaException(INVALID_REQUEST);
        Map<String,PolicyConsentRequest> inputs = new HashMap<>();
        for (var input : submitted) {
            if (input == null || input.documentCode() == null || input.version() == null || input.agreed() == null
                    || inputs.putIfAbsent(input.documentCode(),input) != null) throw new NanacocoaException(INVALID_REQUEST);
            var document = current.stream().filter(d -> d.documentCode().equals(input.documentCode())).findFirst().orElse(null);
            if (document == null) throw new NanacocoaException(snapshot.all().values().stream().anyMatch(d -> d.documentCode().equals(input.documentCode())) ? POLICY_VERSION_CHANGED : INVALID_REQUEST);
            if (!document.version().equals(input.version())) throw new NanacocoaException(POLICY_VERSION_CHANGED);
            if (document.consentMode() == ConsentMode.NONE) throw new NanacocoaException(INVALID_REQUEST);
        }
        List<VerifiedConsent> verified = new ArrayList<>();
        for (var document : current) {
            if (document.consentMode() == ConsentMode.NONE) continue;
            var input = inputs.get(document.documentCode());
            if (input == null || (document.consentMode() == ConsentMode.REQUIRED && !input.agreed())
                    || (document.consentMode() == ConsentMode.OPTIONAL_EMAIL && input.agreed() != (email == EmailReceiveConsent.Y)))
                throw new NanacocoaException(INVALID_REQUEST);
            verified.add(new VerifiedConsent(document,input.agreed()));
        }
        return verified;
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordSignup(Member member, List<VerifiedConsent> verified) {
        consents.saveAllAndFlush(verified.stream().map(v -> new MemberPolicyConsent(member, v.document().documentCode(), v.document().version(), store.hash(v.document()), v.agreed())).toList());
    }
    public record VerifiedConsent(PolicyCatalog.Document document, boolean agreed) {}
}
