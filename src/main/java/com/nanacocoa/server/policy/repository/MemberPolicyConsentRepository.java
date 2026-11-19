package com.nanacocoa.server.policy.repository;

import com.nanacocoa.server.policy.entity.MemberPolicyConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MemberPolicyConsentRepository extends JpaRepository<MemberPolicyConsent, Long> {
    List<MemberPolicyConsent> findByMemberId(Long memberId);
}
