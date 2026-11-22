package com.nanacocoa.server.recovery.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.recovery.config.RecoveryProperties;
import com.nanacocoa.server.recovery.dto.reqeust.RecoveryRequests;
import com.nanacocoa.server.recovery.entity.RecoveryToken;
import com.nanacocoa.server.recovery.repository.RecoveryTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Service @RequiredArgsConstructor
public class RecoveryTokenService {
    private final MemberRepository members;
    private final RecoveryTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final RecoveryProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // 메일 처리 경로에서만 사용하는 값이며 응답 DTO가 아니다.
    public record Issued(String recipient, String token) {
        @Override public String toString() { return "Issued[redacted]"; }
    }

    @Transactional
    public Issued issue(String email) {
        var member = members.findByEmailForUpdate(email).orElse(null);
        if (member == null) return null;
        tokens.findByMemberIdAndConsumedFalse(member.getId()).forEach(RecoveryToken::consume);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RecoveryToken(member.getId(), RecoveryAdmissionService.hash(raw), member.getPassword(),
                LocalDateTime.now(clock).plus(properties.getTokenLifetime())));
        return new Issued(member.getEmail(), raw);
    }

    @Transactional
    public void reset(RecoveryRequests.Reset request) {
        if (request.token() == null || !request.token().matches("[A-Za-z0-9_-]{43}")) throw new NanacocoaException(RECOVERY_INVALID);
        if (request.newPassword() == null || request.newPassword().isBlank() || request.newPassword().length() < 8
                || request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !request.newPassword().equals(request.passwordConfirmation())) throw new NanacocoaException(INVALID_REQUEST);
        // 토큰 조회는 식별만 한다. 모든 변경은 회원 잠금 후 최신 토큰 상태를 다시 조회한다.
        var memberId = tokens.findMemberIdByHash(RecoveryAdmissionService.hash(request.token()))
                .orElseThrow(() -> new NanacocoaException(RECOVERY_INVALID));
        var member = members.findByIdForUpdate(memberId).orElseThrow(() -> new NanacocoaException(RECOVERY_INVALID));
        // 조회 시점의 persistence context 값 대신 잠금 뒤 DB에서 검증한다.
        var valid = tokens.findByMemberIdAndConsumedFalse(memberId).stream()
                .filter(t -> t.getTokenHash().equals(RecoveryAdmissionService.hash(request.token())) && t.getExpiresAt().isAfter(LocalDateTime.now(clock))
                        && t.getCredentialHash().equals(member.getPassword())).findFirst()
                .orElseThrow(() -> new NanacocoaException(RECOVERY_INVALID));
        if (valid.isConsumed()) throw new NanacocoaException(RECOVERY_INVALID);
        member.changePassword(passwords.encode(request.newPassword()));
        member.setAuthenticationGeneration(Math.addExact(member.getAuthenticationGeneration(), 1));
        tokens.findByMemberIdAndConsumedFalse(memberId).forEach(RecoveryToken::consume);
    }
}
