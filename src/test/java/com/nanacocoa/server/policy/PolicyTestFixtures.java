package com.nanacocoa.server.policy;

import com.nanacocoa.server.policy.dto.reqeust.PolicyConsentRequest;
import java.util.List;

public final class PolicyTestFixtures {
    private PolicyTestFixtures() {}
    public static List<PolicyConsentRequest> consents(boolean email) {
        return List.of(new PolicyConsentRequest("TERMS","test-v1",true),
                new PolicyConsentRequest("PRIVACY","test-v1",true),
                new PolicyConsentRequest("EMAIL_RECEIVE","test-v1",email));
    }
}
