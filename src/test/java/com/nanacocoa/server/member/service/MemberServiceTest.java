package com.nanacocoa.server.member.service;

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
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    @Test
    void returnsProfileWithoutPassword() {
        MemberProfileResponse response = memberService.getProfile(member.getEmail());

        assertThat(response.getEmail()).isEqualTo("member@example.com");
        assertThat(response.getName()).isEqualTo("기존 이름");
        assertThat(response.getAddress()).isEqualTo("기존 주소");
        assertThat(response.getPhoneNumber()).isEqualTo("010-1111-2222");
        assertThat(response.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.N);
    }

    @Test
    void updatesProfileAfterCurrentPasswordVerification() {
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
        memberService.changePassword(
                member.getEmail(),
                new ChangeMemberPasswordRequest("current123!", "new-password123!", "new-password123!")
        );

        assertThat(member.getPassword()).isNotEqualTo("new-password123!");
        assertThat(passwordEncoder.matches("new-password123!", member.getPassword())).isTrue();
    }

    @Test
    void rejectsPasswordConfirmationMismatchWithoutChangingHash() {
        String originalPassword = member.getPassword();

        assertThatThrownBy(() -> memberService.changePassword(
                member.getEmail(),
                new ChangeMemberPasswordRequest("current123!", "new-password123!", "different123!")
        )).isInstanceOfSatisfying(NanacocoaException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(PASSWORD_CONFIRMATION_MISMATCH));

        assertThat(member.getPassword()).isEqualTo(originalPassword);
    }
}
