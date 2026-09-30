package com.nanacocoa.server.member.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.session.ProfileVerificationSession;
import com.nanacocoa.server.member.dto.reqeust.VerifyMemberPasswordRequest;
import com.nanacocoa.server.member.dto.reqeust.ChangeMemberPasswordRequest;
import com.nanacocoa.server.member.dto.reqeust.UpdateMemberProfileRequest;
import com.nanacocoa.server.member.dto.response.MemberProfileResponse;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST;
import static com.nanacocoa.server.common.exception.ErrorCode.MEMBER_ID_IMMUTABLE;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_EMAIL;
import static com.nanacocoa.server.common.exception.ErrorCode.PASSWORD_CONFIRMATION_MISMATCH;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileVerificationSession verificationSession;

    @Transactional(readOnly = true)
    public void verifyPassword(String email, VerifyMemberPasswordRequest request, String intent) {
        // 커스텀 헤더는 교차 출처 HTML form으로 확인 세션을 만드는 것을 차단합니다.
        if (!"1".equals(intent)) {
            throw new NanacocoaException(INVALID_REQUEST);
        }
        Member member = findMember(email);
        verificationSession.verify(email, member.getPassword(),
                () -> member.checkPassword(passwordEncoder, request.getCurrentPassword()));
    }

    @Transactional(readOnly = true)
    public void requireProfileVerification(String email) {
        Member member = findMember(email);
        verificationSession.requireVerified(email, member.getPassword());
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(String email) {
        Member member = findMember(email);
        verificationSession.requireVerified(email, member.getPassword());
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public MemberProfileResponse updateProfile(String email, UpdateMemberProfileRequest request) {
        Member member = findMember(email);
        verificationSession.requireVerified(email, member.getPassword());

        if (!member.getEmail().equals(request.getEmail())) {
            throw new NanacocoaException(MEMBER_ID_IMMUTABLE);
        }

        member.updateProfile(
                request.getName().trim(),
                request.getAddress().trim(),
                request.getPhoneNumber(),
                request.getEmailReceiveConsent()
        );
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public void changePassword(String email, ChangeMemberPasswordRequest request) {
        Member member = findMember(email);
        verificationSession.requireVerified(email, member.getPassword());
        member.checkPassword(passwordEncoder, request.getCurrentPassword());

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new NanacocoaException(PASSWORD_CONFIRMATION_MISMATCH);
        }

        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
        verificationSession.clear();
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new NanacocoaException(NOT_FOUND_EMAIL));
    }
}
