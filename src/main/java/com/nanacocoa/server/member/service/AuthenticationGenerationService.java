package com.nanacocoa.server.member.service;

import com.nanacocoa.server.common.security.SessionGenerationVerifier;
import com.nanacocoa.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationGenerationService implements SessionGenerationVerifier {
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public boolean isCurrent(Long memberId, Long generation) {
        return memberId != null && generation != null
                && members.findAuthenticationGeneration(memberId).filter(generation::equals).isPresent();
    }
}
