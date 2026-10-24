package com.nanacocoa.server.member;

import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "payment.reconciliation.enabled=false"
})
class MemberApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        memberRepository.deleteAll();
    }

    @Test
    void profileApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsProfileWithoutPassword() throws Exception {
        MockHttpSession session = createAndLoginMember();

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원정보조회성공"))
                .andExpect(jsonPath("$.data.email").value("mypage@example.com"))
                .andExpect(jsonPath("$.data.name").value("마이페이지 회원"))
                .andExpect(jsonPath("$.data.address").value("서울시 성동구"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1111-2222"))
                .andExpect(jsonPath("$.data.emailReceiveConsent").value("N"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void updatesProfileAndSessionUsesLatestName() throws Exception {
        MockHttpSession session = createAndLoginMember();

        mockMvc.perform(put("/api/members/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "mypage@example.com",
                                  "name": "수정된 회원",
                                  "address": "경기도 용인시",
                                  "phoneNumber": "010-3333-4444",
                                  "emailReceiveConsent": "Y"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원정보수정성공"))
                .andExpect(jsonPath("$.data.name").value("수정된 회원"))
                .andExpect(jsonPath("$.data.emailReceiveConsent").value("Y"));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정된 회원"));
    }

    @Test
    void rejectsEmailChangeAndInvalidPhoneWithoutSaving() throws Exception {
        MockHttpSession session = createAndLoginMember();

        mockMvc.perform(put("/api/members/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdate("changed@example.com", "010-3333-4444")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_04"));

        mockMvc.perform(put("/api/members/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdate("mypage@example.com", "01033334444")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_01"));

        Member member = memberRepository.findByEmail("mypage@example.com").orElseThrow();
        assertThat(member.getName()).isEqualTo("마이페이지 회원");
    }

    @Test
    void changesPasswordAndKeepsCurrentSession() throws Exception {
        MockHttpSession session = createAndLoginMember();

        mockMvc.perform(put("/api/members/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "current123!",
                                  "newPassword": "changed123!",
                                  "newPasswordConfirm": "changed123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호변경성공"))
                .andExpect(jsonPath("$.data").doesNotExist());

        Member member = memberRepository.findByEmail("mypage@example.com").orElseThrow();
        assertThat(member.getPassword()).isNotEqualTo("changed123!");
        assertThat(passwordEncoder.matches("changed123!", member.getPassword())).isTrue();

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isForbidden());
        verify(session, "changed123!").andExpect(status().isOk());
        mockMvc.perform(get("/api/members/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void rejectsMismatchedAndShortNewPassword() throws Exception {
        MockHttpSession session = createAndLoginMember();

        mockMvc.perform(put("/api/members/me/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"incorrect","newPassword":"changed123!","newPasswordConfirm":"changed123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_02"));

        mockMvc.perform(put("/api/members/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "current123!",
                                  "newPassword": "changed123!",
                                  "newPasswordConfirm": "different123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_05"));

        mockMvc.perform(put("/api/members/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "current123!",
                                  "newPassword": "short",
                                  "newPasswordConfirm": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_01"));
    }

    @Test
    void signupStoresRequiredProfileFields() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new-member@example.com",
                                  "password": "signup123!",
                                  "name": "신규 회원",
                                  "address": "부산시 해운대구",
                                  "phoneNumber": "010-5555-6666",
                                  "emailReceiveConsent": "Y"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입성공"));

        Member member = memberRepository.findByEmail("new-member@example.com").orElseThrow();
        assertThat(member.getAddress()).isEqualTo("부산시 해운대구");
        assertThat(member.getPhoneNumber()).isEqualTo("010-5555-6666");
        assertThat(member.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.Y);
        assertThat(passwordEncoder.matches("signup123!", member.getPassword())).isTrue();
    }

    @Test
    void signupRejectsMissingProfileFieldsAndInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-member@example.com",
                                  "password": "signup123!",
                                  "name": "신규 회원",
                                  "address": "",
                                  "phoneNumber": "01055556666",
                                  "emailReceiveConsent": "N"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_01"));

        assertThat(memberRepository.findByEmail("invalid-member@example.com")).isEmpty();
    }

    @Test
    void servesMypageAndExpandedSignupPage() throws Exception {
        MockHttpSession session = createAndLoginMember();
        mockMvc.perform(get("/mypage.html").session(session))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    String profileForm = html.substring(html.indexOf("data-profile-form"), html.indexOf("</form>"));
                    assertThat(profileForm).doesNotContain("currentPassword", "type=\"password\"");
                    assertThat(html.substring(html.indexOf("data-password-form"))).contains("name=\"currentPassword\"");
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-mypage-page")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-profile-form")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-password-form")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-postal-code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-basic")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-detail")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-search-status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<button type=\"button\" data-address-search disabled>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<textarea name=\"address\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("readonly")));

        mockMvc.perform(get("/signup.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"address\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"phoneNumber\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"emailReceiveConsent\"")));
    }

    @Test
    void unverifiedPageAndApisCannotBeAccessedDirectly() throws Exception {
        mockMvc.perform(get("/mypage.html"))
                .andExpect(redirectedUrl("/mypage-verify.html"));
        mockMvc.perform(get("/mypage-verify.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-verification-form")));
        MockHttpSession session = createAndLoginUnverifiedMember();
        mockMvc.perform(get("/mypage.html").session(session))
                .andExpect(redirectedUrl("/mypage-verify.html"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_03"));
        mockMvc.perform(put("/api/members/me").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdate("mypage@example.com", "010-3333-4444")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/members/me/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"current123!","newPassword":"changed123!","newPasswordConfirm":"changed123!"}
                                """))
                .andExpect(status().isForbidden());
        Member member = memberRepository.findByEmail("mypage@example.com").orElseThrow();
        assertThat(member.getName()).isEqualTo("마이페이지 회원");
        assertThat(passwordEncoder.matches("current123!", member.getPassword())).isTrue();
    }

    @Test
    void verificationRequiresLoginJsonAndSameOriginHeader() throws Exception {
        mockMvc.perform(post("/api/members/me/verification").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mypage-Verification", "1").content("{\"currentPassword\":\"current123!\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession session = createAndLoginUnverifiedMember();
        mockMvc.perform(post("/api/members/me/verification").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"current123!\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/members/me/verification").session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("currentPassword", "current123!"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/members/me/verification")
                        .session(session).header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Mypage-Verification,Content-Type"))
                .andExpect(status().isForbidden());
        verify(session, "").andExpect(status().isBadRequest());
        verify(session, "x".repeat(73)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/members/me").session(session)).andExpect(status().isForbidden());
        verify(session, "current123!").andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("current123!"))));
    }

    @Test
    void wrongPasswordRevokesGrantAndRepeatedFailuresAreLimited() throws Exception {
        MockHttpSession session = createAndLoginMember();
        for (int attempt = 0; attempt < 5; attempt++) {
            verify(session, "incorrect").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MEMBER_02"));
        }
        verify(session, "current123!").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_04"));
        mockMvc.perform(get("/api/members/me").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void verificationIsBoundToSessionAndClearedOnLoginAndLogout() throws Exception {
        MockHttpSession first = createAndLoginMember();
        MockHttpSession second = login(new MockHttpSession());
        mockMvc.perform(get("/api/members/me").session(second)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/members/me").session(first)).andExpect(status().isOk());
        login(first);
        mockMvc.perform(get("/api/members/me").session(first)).andExpect(status().isForbidden());
        verify(first, "current123!").andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").session(first)).andExpect(status().isOk());
        assertThat(first.isInvalid()).isTrue();
        MockHttpSession afterLogout = login(new MockHttpSession());
        mockMvc.perform(get("/api/members/me").session(afterLogout)).andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeInvalidatesOtherVerifiedSessions() throws Exception {
        MockHttpSession first = createAndLoginMember();
        MockHttpSession second = login(new MockHttpSession());
        verify(second, "current123!").andExpect(status().isOk());
        mockMvc.perform(put("/api/members/me/password").session(first).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"current123!","newPassword":"changed123!","newPasswordConfirm":"changed123!"}
                                """))
                .andExpect(status().isOk());
        for (MockHttpSession session : java.util.List.of(first, second)) {
            mockMvc.perform(get("/api/members/me").session(session)).andExpect(status().isForbidden());
            mockMvc.perform(get("/mypage.html").session(session)).andExpect(redirectedUrl("/mypage-verify.html"));
            mockMvc.perform(put("/api/members/me").session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(profileUpdate("mypage@example.com", "010-3333-4444")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_03"));
        }
    }

    private org.springframework.test.web.servlet.ResultActions verify(MockHttpSession session, String password) throws Exception {
        return mockMvc.perform(post("/api/members/me/verification").session(session)
                .header("X-Mypage-Verification", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + password + "\"}"));
    }

    private MockHttpSession login(MockHttpSession session) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mypage@example.com\",\"password\":\"current123!\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private MockHttpSession createAndLoginMember() throws Exception {
        MockHttpSession session = createAndLoginUnverifiedMember();
        verify(session, "current123!").andExpect(status().isOk());
        return session;
    }

    private MockHttpSession createAndLoginUnverifiedMember() throws Exception {
        Member member = Member.builder()
                .email("mypage@example.com")
                .password(passwordEncoder.encode("current123!"))
                .name("마이페이지 회원")
                .address("서울시 성동구")
                .phoneNumber("010-1111-2222")
                .emailReceiveConsent(EmailReceiveConsent.N)
                .build();
        memberRepository.saveAndFlush(member);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "mypage@example.com",
                                  "password": "current123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String profileUpdate(String email, String phoneNumber) {
        return """
                {
                  "email": "%s",
                  "name": "변경된 회원",
                  "address": "경기도 용인시",
                  "phoneNumber": "%s",
                  "emailReceiveConsent": "Y"
                }
                """.formatted(email, phoneNumber);
    }
}
