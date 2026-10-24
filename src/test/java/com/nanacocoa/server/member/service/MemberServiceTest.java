package com.nanacocoa.server.member.service;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.nanacocoa.server.member.controller.MemberController;
import com.nanacocoa.server.common.exception.GlobalExceptionHandler;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.mockito.Mockito;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.session.ProfileVerificationSession;
import com.nanacocoa.server.member.dto.reqeust.ChangeMemberPasswordRequest;
import com.nanacocoa.server.member.dto.reqeust.UpdateMemberProfileRequest;
import com.nanacocoa.server.member.dto.response.MemberProfileResponse;
import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.nanacocoa.server.common.exception.ErrorCode.MEMBER_ID_IMMUTABLE;
import static com.nanacocoa.server.common.exception.ErrorCode.PROFILE_VERIFICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.PASSWORD_CONFIRMATION_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProfileVerificationSession verificationSession;

    private PasswordEncoder passwordEncoder;
    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        memberService = new MemberService(memberRepository, passwordEncoder, verificationSession);
        member = Member.builder()
                .email("member@example.com")
                .password(passwordEncoder.encode("current123!"))
                .name("기존 이름")
                .address("기존 주소")
                .phoneNumber("010-1111-2222")
                .emailReceiveConsent(EmailReceiveConsent.N)
                .build();
    }

    @Test
    void returnsProfileWithoutPassword() {
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
        MemberProfileResponse response = memberService.getProfile(member.getEmail());

        assertThat(response.getEmail()).isEqualTo("member@example.com");
        assertThat(response.getName()).isEqualTo("기존 이름");
        assertThat(response.getAddress()).isEqualTo("기존 주소");
        assertThat(response.getPhoneNumber()).isEqualTo("010-1111-2222");
        assertThat(response.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.N);
    }

    @Test
    void updatesProfileAfterCurrentPasswordVerification() {
        when(memberRepository.findByEmailForUpdate(member.getEmail())).thenReturn(Optional.of(member));
        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest(
                member.getEmail(),
                " 새 이름 ",
                " 새 주소 ",
                "010-3333-4444",
                EmailReceiveConsent.Y
        );

        MemberProfileResponse response = memberService.updateProfile(member.getEmail(), request);

        assertThat(response.getName()).isEqualTo("새 이름");
        assertThat(member.getAddress()).isEqualTo("새 주소");
        assertThat(member.getPhoneNumber()).isEqualTo("010-3333-4444");
        assertThat(member.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.Y);
    }

    @Test
    void rejectsUnverifiedSessionWithoutChangingProfile() {
        when(memberRepository.findByEmailForUpdate(member.getEmail())).thenReturn(Optional.of(member));
        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest(
                member.getEmail(),
                "변경 이름",
                "변경 주소",
                "010-3333-4444",
                EmailReceiveConsent.Y
        );

        doThrow(new NanacocoaException(PROFILE_VERIFICATION_REQUIRED))
                .when(verificationSession).requireVerified(member.getEmail(), member.getPassword());

        assertThatThrownBy(() -> memberService.updateProfile(member.getEmail(), request))
                .isInstanceOfSatisfying(NanacocoaException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PROFILE_VERIFICATION_REQUIRED));
        assertThat(member.getName()).isEqualTo("기존 이름");
        assertThat(member.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.N);
    }

    @Test
    void rejectsEmailChangeAfterCurrentPasswordVerification() {
        when(memberRepository.findByEmailForUpdate(member.getEmail())).thenReturn(Optional.of(member));
        UpdateMemberProfileRequest request = new UpdateMemberProfileRequest(
                "changed@example.com",
                "변경 이름",
                "변경 주소",
                "010-3333-4444",
                EmailReceiveConsent.Y
        );

        assertThatThrownBy(() -> memberService.updateProfile(member.getEmail(), request))
                .isInstanceOfSatisfying(NanacocoaException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MEMBER_ID_IMMUTABLE));
        assertThat(member.getEmail()).isEqualTo("member@example.com");
        assertThat(member.getName()).isEqualTo("기존 이름");
    }

    @Test
    void changesPasswordAsBcryptHash() {
        when(memberRepository.findByEmailForUpdate(member.getEmail())).thenReturn(Optional.of(member));
        memberService.changePassword(
                member.getEmail(),
                new ChangeMemberPasswordRequest("current123!", "new-password123!", "new-password123!")
        );

        assertThat(member.getPassword()).isNotEqualTo("new-password123!");
        assertThat(passwordEncoder.matches("new-password123!", member.getPassword())).isTrue();
    }

    @Test
    void rejectsPasswordConfirmationMismatchWithoutChangingHash() {
        when(memberRepository.findByEmailForUpdate(member.getEmail())).thenReturn(Optional.of(member));
        String originalPassword = member.getPassword();

        assertThatThrownBy(() -> memberService.changePassword(
                member.getEmail(),
                new ChangeMemberPasswordRequest("current123!", "new-password123!", "different123!")
        )).isInstanceOfSatisfying(NanacocoaException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(PASSWORD_CONFIRMATION_MISMATCH));

        assertThat(member.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    void lockFailureReturnsConflictForBothWritesWithoutVerification() throws Exception {
        when(memberRepository.findByEmailForUpdate(member.getEmail()))
                .thenThrow(new CannotAcquireLockException("busy"));
        // 실제 Service와 전역 예외 처리기를 연결해 HTTP 오류 계약도 검증합니다.
        var mvc = MockMvcBuilders
                .standaloneSetup(new MemberController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == UserDetailsImpl.class;
                    }
                    public Object resolveArgument(MethodParameter parameter,
                            ModelAndViewContainer container,
                            NativeWebRequest request,
                            WebDataBinderFactory factory) {
                        return new UserDetailsImpl(member, member.getEmail());
                    }
                }).build();
        String[] paths = {"/api/members/me", "/api/members/me/password"};
        String[] bodies = {
                "{\"email\":\"member@example.com\",\"name\":\"updated\",\"address\":\"address\",\"phoneNumber\":\"010-3333-4444\",\"emailReceiveConsent\":\"Y\"}",
                "{\"currentPassword\":\"current123!\",\"newPassword\":\"changed123!\",\"newPasswordConfirm\":\"changed123!\"}"
        };
        for (int i = 0; i < paths.length; i++) {
            mvc.perform(MockMvcRequestBuilders.put(paths[i])
                            .contentType(MediaType.APPLICATION_JSON).content(bodies[i]))
                    .andExpect(MockMvcResultMatchers.status().isConflict())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("MEMBER_06"));
        }
        Mockito.verifyNoInteractions(verificationSession);
    }

    @Test
    void missingMemberIsRejectedForLockedLookup() {
        when(memberRepository.findByEmailForUpdate(member.getEmail())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.changePassword(member.getEmail(),
                new ChangeMemberPasswordRequest("current123!", "changed123!", "changed123!")))
                .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_EMAIL));
    }
}
