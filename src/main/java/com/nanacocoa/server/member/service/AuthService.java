package com.nanacocoa.server.member.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.session.LoginService;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.dto.reqeust.LoginRequest;
import com.nanacocoa.server.member.dto.reqeust.SignupRequest;
import com.nanacocoa.server.member.dto.response.AuthSessionResponse;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.policy.service.PolicyService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.nanacocoa.server.common.exception.ErrorCode.ALREADY_EXIST_EMAIL;

@Service
@AllArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final LoginService loginService;
    private final PolicyService policyService;

    @Transactional
    public void signUp(SignupRequest request) {
        var verifiedConsents = policyService.validateSignup(request.getConsents(), request.getEmailReceiveConsent());
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new NanacocoaException(ALREADY_EXIST_EMAIL);
        }

        String encodePassword = passwordEncoder.encode(request.getPassword());
        Member member = Member.builder()
                .email(request.getEmail())
                .name(request.getName().trim())
                .password(encodePassword)
                .address(request.getAddress().trim())
                .phoneNumber(request.getPhoneNumber())
                .emailReceiveConsent(request.getEmailReceiveConsent())
                .build();
        memberRepository.save(member);
        policyService.recordSignup(member, verifiedConsents);
    }

    @Transactional
    public void login(LoginRequest request) {
        Member member = memberRepository.findByEmailForUpdate(request.getEmail()).orElseThrow(
                () -> new NanacocoaException(ALREADY_EXIST_EMAIL)
        );
        member.checkPassword(passwordEncoder, request.getPassword());

        loginService.login(member.getEmail());
    }

    public void logout(){
        loginService.logout();
    }

    public AuthSessionResponse getSession(UserDetailsImpl userDetails) {
        if (userDetails == null) {
            return AuthSessionResponse.anonymous();
        }

        return memberRepository.findByEmail(userDetails.getUsername())
                .map(AuthSessionResponse::from)
                .orElseGet(AuthSessionResponse::anonymous);
    }
}
