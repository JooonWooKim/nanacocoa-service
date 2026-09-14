package com.nanacocoa.server.member.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.dto.reqeust.ChangeMemberPasswordRequest;
import com.nanacocoa.server.member.dto.reqeust.UpdateMemberProfileRequest;
import com.nanacocoa.server.member.dto.response.MemberProfileResponse;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nanacocoa.server.common.exception.ErrorCode.MEMBER_ID_IMMUTABLE;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_EMAIL;
import static com.nanacocoa.server.common.exception.ErrorCode.PASSWORD_CONFIRMATION_MISMATCH;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(String email) {
        return MemberProfileResponse.from(findMember(email));
    }

    @Transactional
    public MemberProfileResponse updateProfile(String email, UpdateMemberProfileRequest request) {
        Member member = findMember(email);
        member.checkPassword(passwordEncoder, request.getCurrentPassword());

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
        member.checkPassword(passwordEncoder, request.getCurrentPassword());

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new NanacocoaException(PASSWORD_CONFIRMATION_MISMATCH);
        }

        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new NanacocoaException(NOT_FOUND_EMAIL));
    }
}
