package com.nanacocoa.server.common.security;

public interface SessionGenerationVerifier {
    boolean isCurrent(Long memberId, Long generation);
}
