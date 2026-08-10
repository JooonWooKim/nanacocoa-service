package com.nanacocoa.server.member.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.session.LoginService;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.dto.reqeust.LoginRequest;
import com.nanacocoa.server.member.dto.reqeust.SignupRequest;
import com.nanacocoa.server.member.dto.response.AuthSessionResponse;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
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

    @Transactional
    public void signUp(SignupRequest request) {
        String encodePassword = passwordEncoder.encode(request.getPassword());
        Member member = request.toMember(request, encodePassword);
        memberRepository.save(member);
    }

    public void login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail()).orElseThrow(
                () -> new NanacocoaException(ALREADY_EXIST_EMAIL)
        );
        member.checkEmailDuplicate(member.getEmail());
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
