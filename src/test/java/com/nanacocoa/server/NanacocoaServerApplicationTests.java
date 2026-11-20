package com.nanacocoa.server;

import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.dto.reqeust.SignupRequest;
import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.member.service.AuthService;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static com.nanacocoa.server.CsrfRequests.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "policy.catalog-location=classpath:policies/approved-test-catalog.json",
        "policy.signup-enabled=true",
    "spring.datasource.url=jdbc:h2:mem:nanacocoa-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "toss-payments.client-key=test_ck_integration"
})
class NanacocoaServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductsRepository productsRepository;

    @Autowired
    private AuthService authService;

    private MockHttpSession loginMember(String email, String password, String name, boolean admin) throws Exception {
        Member member = memberRepository.findByEmail(email).orElseGet(() -> Member.builder()
            .email(email)
            .password(passwordEncoder.encode(password))
            .name(name)
            .build());
        member.setAdmin(admin);
        memberRepository.saveAndFlush(member);

        MvcResult login = mockMvc.perform(post("/api/auth/login").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();

        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private MockHttpSession adminSession() throws Exception {
        return loginMember("products-admin@example.com", "admin123!", "상품 운영자", true);
    }

    private MockMultipartFile productPart(String name) {
        String productJson = """
            {
              "name": "%s",
              "price": 38000,
              "summary": "multipart 상품 등록 테스트입니다.",
              "detailTitle": "multipart 상품 상세 제목",
              "description": "대표 이미지와 함께 등록되는 상품 설명입니다."
            }
            """.formatted(name);
        return new MockMultipartFile(
            "product", "product.json", MediaType.APPLICATION_JSON_VALUE,
            productJson.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile pngImagePart(byte[] bytes) {
        return new MockMultipartFile("image", "product.png", MediaType.IMAGE_PNG_VALUE, bytes);
    }

    private byte[] validPngBytes(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void healthApiReturnsOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void loginCreatesAuthenticatedSessionForRegisteredUser() throws Exception {
        MockHttpSession session = loginMember("demo@example.test", "fixture-password", "테스트 회원", false);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void loginRejectsInvalidPasswordWithoutAuthenticatingSession() throws Exception {
        loginMember("invalid-login@example.test", "fixture-password", "테스트 회원", false);
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invalid-login@example.test","password":"wrong-password"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MEMBER_02"))
            .andReturn();

        var session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void signupStoresCompleteProfileWithBcryptedPassword() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "signup-success@example.com",
                      "password": "signup123!",
                      "name": "가입자",
                      "address": "서울시 성동구",
                      "phoneNumber": "010-1111-2222",
                      "emailReceiveConsent": "N",
                      "consents": [{"documentCode":"TERMS","version":"test-v1","agreed":true},{"documentCode":"PRIVACY","version":"test-v1","agreed":true},{"documentCode":"EMAIL_RECEIVE","version":"test-v1","agreed":false}]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("회원가입성공"))
            .andReturn();

        var session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));

        Member member = memberRepository.findByEmail("signup-success@example.com").orElseThrow();
        assertThat(member.getName()).isEqualTo("가입자");
        assertThat(member.getAddress()).isEqualTo("서울시 성동구");
        assertThat(member.getPhoneNumber()).isEqualTo("010-1111-2222");
        assertThat(member.getEmailReceiveConsent()).isEqualTo(EmailReceiveConsent.N);
        assertThat(member.isAdmin()).isFalse();
        assertThat(member.getPassword()).isNotEqualTo("signup123!");
        assertThat(passwordEncoder.matches("signup123!", member.getPassword())).isTrue();
    }

    @Test
    void signupCreatesNonAdminMember() {
        String email = "non-admin-signup@example.com";

        authService.signUp(new SignupRequest(
            email,
            "signup123!",
            "일반 가입자",
            "서울시 성동구",
            "010-1111-2222",
            EmailReceiveConsent.N,
            com.nanacocoa.server.policy.PolicyTestFixtures.consents(false)
        ));

        Member member = memberRepository.findByEmail(email).orElseThrow();
        assertThat(member.isAdmin()).isFalse();
    }

    @Test
    void sessionReturnsAnonymousStateWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void sessionReflectsLatestAdminStateFromDatabase() throws Exception {
        String email = "session-admin-state@example.com";
        MockHttpSession session = loginMember(email, "session123!", "권한 확인 회원", false);

        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.admin").value(false));

        Member member = memberRepository.findByEmail(email).orElseThrow();
        member.setAdmin(true);
        memberRepository.saveAndFlush(member);

        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.name").value("권한 확인 회원"))
            .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void adminRegistersProductWithMultipartImageInMockMode() throws Exception {
        mockMvc.perform(multipart("/api/products")
                .file(productPart("multipart 이미지 상품"))
                .file(pngImagePart(validPngBytes(16))).with(csrf(mockMvc))
                .session(adminSession()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("상품등록성공"))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.name").value("multipart 이미지 상품"))
            .andExpect(jsonPath("$.data.imageUrl").value("IMG_2398.JPG"));

        Products product = productsRepository.findAll().stream()
            .filter(item -> item.getName().equals("multipart 이미지 상품"))
            .findFirst()
            .orElseThrow();
        assertThat(product.getImageUrl()).isEqualTo("IMG_2398.JPG");
    }

    @Test
    void multipartRegistrationRejectsNonAdminBeforeSaving() throws Exception {
        long productCount = productsRepository.count();
        MockHttpSession memberSession = loginMember(
            "multipart-member@example.com", "member123!", "일반 회원", false);

        mockMvc.perform(multipart("/api/products")
                .file(productPart("일반 회원 multipart 상품"))
                .file(pngImagePart(validPngBytes(16))).with(csrf(mockMvc))
                .session(memberSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_02"));

        assertThat(productsRepository.count()).isEqualTo(productCount);
    }

    @Test
    void multipartRegistrationRejectsInvalidImageSignature() throws Exception {
        MockMultipartFile invalidImage = new MockMultipartFile(
            "image", "fake.png", MediaType.IMAGE_PNG_VALUE, "not-an-image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/products")
                .file(productPart("잘못된 이미지 상품"))
                .file(invalidImage).with(csrf(mockMvc))
                .session(adminSession()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRODUCTS_03"));
    }

    @Test
    void multipartRegistrationRejectsImageLargerThanFiveMegabytes() throws Exception {
        mockMvc.perform(multipart("/api/products")
                .file(productPart("대용량 이미지 상품"))
                .file(pngImagePart(validPngBytes(5 * 1024 * 1024 + 1))).with(csrf(mockMvc))
                .session(adminSession()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PRODUCTS_04"));
    }

    @Test
    void signupMemberCanLoginWithEmailAndPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login-after-signup@example.com",
                      "password": "signup123!",
                      "name": "로그인회원",
                      "address": "서울시 성동구",
                      "phoneNumber": "010-1111-2222",
                      "emailReceiveConsent": "N",
                      "consents": [{"documentCode":"TERMS","version":"test-v1","agreed":true},{"documentCode":"PRIVACY","version":"test-v1","agreed":true},{"documentCode":"EMAIL_RECEIVE","version":"test-v1","agreed":false}]
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login-after-signup@example.com",
                      "password": "signup123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("로그인성공"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("login-after-signup@example.com"))
            .andExpect(jsonPath("$.name").value("로그인회원"));
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        String signupBody = """
            {
              "email": "duplicate@example.com",
              "password": "signup123!",
              "name": "중복회원",
              "address": "서울시 성동구",
              "phoneNumber": "010-1111-2222",
              "emailReceiveConsent": "N",
                      "consents": [{"documentCode":"TERMS","version":"test-v1","agreed":true},{"documentCode":"PRIVACY","version":"test-v1","agreed":true},{"documentCode":"EMAIL_RECEIVE","version":"test-v1","agreed":false}]
            }
            """;

        mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MEMBER_01"))
            .andExpect(jsonPath("$.error").value("이미 등록된 이메일입니다."));
    }

    @Test
    void signupRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-email",
                      "password": "signup123!",
                      "name": "가입자"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_01"));

        mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "blank-name@example.com",
                      "password": "signup123!",
                      "name": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_01"));

        mockMvc.perform(post("/api/auth/signup").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "short-password@example.com",
                      "password": "short",
                      "name": "가입자"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_01"));
    }

    @Test
    void registerProductStoresDetailPageFields() throws Exception {
        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "테스트 핑크 머그",
                      "price": 28000,
                      "summary": "부드러운 핑크 톤의 데일리 머그입니다.",
                      "detailTitle": "매일 손이 가는 핑크빛 머그",
                      "description": "손으로 빚은 곡선과 투명한 유약의 광택을 살린 머그입니다.",
                      "imageUrl": "IMG_2398.JPG"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("상품등록성공"))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.name").value("테스트 핑크 머그"))
            .andExpect(jsonPath("$.data.price").value(28000))
            .andExpect(jsonPath("$.data.summary").value("부드러운 핑크 톤의 데일리 머그입니다."))
            .andExpect(jsonPath("$.data.detailTitle").value("매일 손이 가는 핑크빛 머그"))
            .andExpect(jsonPath("$.data.description").value("손으로 빚은 곡선과 투명한 유약의 광택을 살린 머그입니다."))
            .andExpect(jsonPath("$.data.imageUrl").value("IMG_2398.JPG"));

        Products product = productsRepository.findAll().stream()
            .filter(item -> item.getName().equals("테스트 핑크 머그"))
            .findFirst()
            .orElseThrow();

        assertThat(product.getPrice()).isEqualTo(28000);
        assertThat(product.getSummary()).isEqualTo("부드러운 핑크 톤의 데일리 머그입니다.");
        assertThat(product.getDetailTitle()).isEqualTo("매일 손이 가는 핑크빛 머그");
        assertThat(product.getDescription()).isEqualTo("손으로 빚은 곡선과 투명한 유약의 광택을 살린 머그입니다.");
        assertThat(product.getImageUrl()).isEqualTo("IMG_2398.JPG");
        assertThat(product.getCreatedAt()).isNotNull();
    }

    @Test
    void getProductsReturnsRegisteredProductsInNewestOrder() throws Exception {
        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "목록 조회 첫 상품",
                      "price": 21000,
                      "summary": "목록 조회용 첫 상품입니다.",
                      "detailTitle": "목록 첫 상품 상세",
                      "description": "목록 조회 테스트의 첫 번째 상품 설명입니다.",
                      "imageUrl": "IMG_2398.JPG"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "목록 조회 최신 상품",
                      "price": 42000,
                      "summary": "목록 조회용 최신 상품입니다.",
                      "detailTitle": "목록 최신 상품 상세",
                      "description": "목록 조회 테스트의 최신 상품 설명입니다.",
                      "imageUrl": "IMG_1483.JPG"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("상품목록조회성공"))
            .andExpect(jsonPath("$.data[0].name").value("목록 조회 최신 상품"))
            .andExpect(jsonPath("$.data[0].price").value(42000))
            .andExpect(jsonPath("$.data[0].summary").value("목록 조회용 최신 상품입니다."))
            .andExpect(jsonPath("$.data[0].detailTitle").value("목록 최신 상품 상세"))
            .andExpect(jsonPath("$.data[0].description").value("목록 조회 테스트의 최신 상품 설명입니다."))
            .andExpect(jsonPath("$.data[0].imageUrl").value("IMG_1483.JPG"))
            .andExpect(jsonPath("$.data[1].name").value("목록 조회 첫 상품"));
    }

    @Test
    void getProductReturnsDetailFieldsById() throws Exception {
        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "상세 조회 머그",
                      "price": 33000,
                      "summary": "상세 조회용 상품입니다.",
                      "detailTitle": "상세 조회 제목",
                      "description": "상세 조회 테스트용 상품 설명입니다.",
                      "imageUrl": "IMG_2398.JPG"
                    }
                    """))
            .andExpect(status().isCreated());

        Products product = productsRepository.findAll().stream()
            .filter(item -> item.getName().equals("상세 조회 머그"))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(get("/api/products/{productId}", product.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("상품조회성공"))
            .andExpect(jsonPath("$.data.id").value(product.getId().intValue()))
            .andExpect(jsonPath("$.data.name").value("상세 조회 머그"))
            .andExpect(jsonPath("$.data.price").value(33000))
            .andExpect(jsonPath("$.data.summary").value("상세 조회용 상품입니다."))
            .andExpect(jsonPath("$.data.detailTitle").value("상세 조회 제목"))
            .andExpect(jsonPath("$.data.description").value("상세 조회 테스트용 상품 설명입니다."))
            .andExpect(jsonPath("$.data.imageUrl").value("IMG_2398.JPG"));
    }

    @Test
    void getProductRejectsUnknownProductId() throws Exception {
        mockMvc.perform(get("/api/products/{productId}", 999999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCTS_02"))
            .andExpect(jsonPath("$.error").value("상품을 찾을 수 없습니다."));
    }

    @Test
    void registerProductRejectsDuplicateName() throws Exception {
        String productBody = """
            {
              "name": "중복 테스트 머그",
              "price": 28000,
              "summary": "부드러운 핑크 톤의 데일리 머그입니다.",
              "detailTitle": "매일 손이 가는 핑크빛 머그",
              "description": "손으로 빚은 곡선과 투명한 유약의 광택을 살린 머그입니다.",
              "imageUrl": "IMG_2398.JPG"
            }
            """;

        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRODUCTS_01"))
            .andExpect(jsonPath("$.error").value("이미 등록된 상품입니다."));
    }

    @Test
    void registerProductRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "price": 28000,
                      "summary": "부드러운 핑크 톤의 데일리 머그입니다.",
                      "detailTitle": "매일 손이 가는 핑크빛 머그",
                      "description": "손으로 빚은 곡선과 투명한 유약의 광택을 살린 머그입니다."
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "가격 오류 머그",
                      "price": 0,
                      "summary": "부드러운 핑크 톤의 데일리 머그입니다.",
                      "detailTitle": "매일 손이 가는 핑크빛 머그",
                      "description": "손으로 빚은 곡선과 투명한 유약의 광택을 살린 머그입니다."
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "상세 오류 머그",
                      "price": 28000,
                      "summary": "",
                      "detailTitle": "",
                      "description": ""
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerProductRequiresLogin() throws Exception {
        long productCount = productsRepository.count();

        mockMvc.perform(post("/api/products").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "비로그인 등록 시도 상품",
                      "price": 28000,
                      "summary": "비로그인 등록 차단 테스트입니다.",
                      "detailTitle": "비로그인 등록 차단",
                      "description": "로그인하지 않은 사용자는 상품을 등록할 수 없습니다."
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_01"))
            .andExpect(jsonPath("$.error").value("로그인이 필요합니다."));

        assertThat(productsRepository.count()).isEqualTo(productCount);
    }

    @Test
    void registerProductRejectsNonAdminMember() throws Exception {
        long productCount = productsRepository.count();
        MockHttpSession memberSession = loginMember(
            "products-member@example.com", "member123!", "일반 회원", false);

        mockMvc.perform(post("/api/products")
                .session(memberSession).with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "일반 회원 등록 시도 상품",
                      "price": 28000,
                      "summary": "일반 회원 등록 차단 테스트입니다.",
                      "detailTitle": "일반 회원 등록 차단",
                      "description": "일반 회원은 상품을 등록할 수 없습니다."
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_02"))
            .andExpect(jsonPath("$.error").value("운영자만 접근할 수 있습니다."));

        assertThat(productsRepository.count()).isEqualTo(productCount);
    }

    @Test
    void registerProductReflectsAdminPromotionDuringExistingSession() throws Exception {
        String email = "promoted-products-admin@example.com";
        MockHttpSession memberSession = loginMember(email, "promoted123!", "승격 회원", false);

        Member member = memberRepository.findByEmail(email).orElseThrow();
        member.setAdmin(true);
        memberRepository.saveAndFlush(member);

        mockMvc.perform(post("/api/products")
                .session(memberSession).with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "운영자 승격 후 등록 상품",
                      "price": 35000,
                      "summary": "운영자 승격 반영 테스트입니다.",
                      "detailTitle": "운영자 승격 즉시 반영",
                      "description": "기존 로그인 세션에서도 DB의 최신 운영자 권한을 확인합니다."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("상품등록성공"))
            .andExpect(jsonPath("$.data.name").value("운영자 승격 후 등록 상품"));
    }

    @Test
    void sessionReturnsAuthenticatedUserAfterLogin() throws Exception {
        MockHttpSession session = loginMember("session@example.test", "fixture-password", "테스트 회원", false);
        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("session@example.test"))
            .andExpect(jsonPath("$.name").value("테스트 회원"));
    }

    @Test
    void logoutClearsAuthenticatedSession() throws Exception {
        MockHttpSession session = loginMember("logout@example.test", "fixture-password", "테스트 회원", false);
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf(mockMvc)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void createOrderStoresShippingInformationAndServerCalculatedAmount() throws Exception {
        MockHttpSession memberSession = loginMember(
            "order-member@example.com", "order123!", "주문 회원", false);
        Products product = productsRepository.saveAndFlush(Products.builder()
            .name("주문 API 테스트 머그")
            .price(28000L)
            .summary("주문 API 테스트 상품입니다.")
            .detailTitle("주문 API 상세")
            .description("주문 API 테스트 상품 설명입니다.")
            .build());

        product.updateInventory(10, com.nanacocoa.server.products.entity.SaleStatus.ON_SALE, product.getStockVersion());
        productsRepository.saveAndFlush(product);

        mockMvc.perform(post("/api/orders")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .session(memberSession).with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "김준우",
                      "phoneNumber": "010-1234-5678",
                      "shippingAddress": "경기도 용인시 수지구",
                      "customerRequest": "문 앞에 놓아주세요.",
                      "items": [{"productId": %d, "quantity": 2}]
                    }
                    """.formatted(product.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("주문생성성공"))
            .andExpect(jsonPath("$.data.orderId").isString())
            .andExpect(jsonPath("$.data.recipientName").value("김준우"))
            .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.shippingAddress").value("경기도 용인시 수지구"))
            .andExpect(jsonPath("$.data.customerRequest").value("문 앞에 놓아주세요."))
            .andExpect(jsonPath("$.data.items[0].productName").value("주문 API 테스트 머그"))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value(28000))
            .andExpect(jsonPath("$.data.items[0].quantity").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(56000));
    }

    @Test
    void createOrderRejectsMissingShippingInformation() throws Exception {
        MockHttpSession memberSession = loginMember(
            "invalid-order-member@example.com", "order123!", "주문 검증 회원", false);

        mockMvc.perform(post("/api/orders")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .session(memberSession).with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "",
                      "phoneNumber": "",
                      "shippingAddress": "",
                      "items": [{"productId": 1, "quantity": 1}]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_01"));
    }

    @Test
    void createOrderRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/orders").with(csrf(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "김준우",
                      "phoneNumber": "010-1234-5678",
                      "shippingAddress": "경기도 용인시 수지구",
                      "items": [{"productId": 1, "quantity": 1}]
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_01"));
    }

    @Test
    void disabledPaymentsRequireLoginAndRejectWritesButAllowQueries() throws Exception {
        mockMvc.perform(get("/api/payments/client-config"))
            .andExpect(status().isUnauthorized());

        UserDetailsImpl principal = new UserDetailsImpl(null, "payment-config@example.com");
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        ));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            securityContext
        );

        mockMvc.perform(get("/api/payments/client-config").session(session))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_11"))
            .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/api/payments/confirm").session(session).with(csrf(mockMvc))
                .header("Idempotency-Key", "disabled-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"paymentKey":"fixture-payment","orderId":"fixture-order","amount":1000}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_11"));

        mockMvc.perform(post("/api/payments/1/cancel").session(session).with(csrf(mockMvc))
                .header("Idempotency-Key", "disabled-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"cancelReason":"테스트 취소"}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_11"));

        mockMvc.perform(get("/api/payments/1").session(session))
            .andExpect(status().isNotFound());
    }

    @Test
    void servesStaticPages() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nanacocoa | cheerful handmade ceramics")));

        mockMvc.perform(get("/products.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-products-grid")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<script src=\"script.js\"></script>")));

        mockMvc.perform(get("/login.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-login-form")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<script src=\"script.js\"></script>")));

        mockMvc.perform(get("/signup.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-signup-form")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<script src=\"script.js\"></script>")));

        mockMvc.perform(get("/product-register.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-product-register-page")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-product-register-form")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"image\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<script src=\"script.js\"></script>")));

        mockMvc.perform(get("/checkout.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-checkout-page")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-checkout-form")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-postal-code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<button type=\"button\" data-address-search disabled>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-basic")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-detail")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-search-status")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-payment-method-toggle")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"TOSSPAY\" checked")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"KAKAOPAY\" disabled")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"CARD\" disabled")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"NAVERPAY\" disabled")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-payment-terms required")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-checkout-result")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("쿠폰"))));

        MvcResult scriptResult = mockMvc.perform(get("/script.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("https://js.tosspayments.com/v2/standard")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("easyPay: \"TOSSPAY\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Idempotency-Key")))
            .andReturn();

        String script = scriptResult.getResponse().getContentAsString();
        int callbackReturnUrlStart = script.indexOf("function isPaymentSuccessCallbackUrl(url) {");
        String callbackReturnUrl = script.substring(
            callbackReturnUrlStart,
            script.indexOf("function checkoutCustomerKey() {", callbackReturnUrlStart)
        );
        int loginStart = script.indexOf("function initLoginPage() {");
        String login = script.substring(
            loginStart,
            script.indexOf("function initSignupPage() {", loginStart)
        );
        int renewalStart = script.indexOf("function renewCheckoutPaymentAttempt(context) {");
        String renewal = script.substring(
            renewalStart,
            script.indexOf("function getProductImageUrl(product) {", renewalStart)
        );
        int retryStart = script.indexOf("async function retryTossPayment(context) {");
        String retry = script.substring(
            retryStart,
            script.indexOf("function renderPaymentFailure(", retryStart)
        );
        int callbackStart = script.indexOf("async function processPaymentSuccessCallback() {");
        String callback = script.substring(
            callbackStart,
            script.indexOf("const session = await loadAuthSession();", callbackStart)
        );
        int checkoutSessionStart = script.indexOf("const session = await loadAuthSession();", callbackStart);
        String checkoutSession = script.substring(
            checkoutSessionStart,
            script.indexOf("if (paymentResult === \"fail\") {", checkoutSessionStart)
        );

        assertThat(callbackReturnUrl)
            .contains(
                "url.origin === window.location.origin",
                "url.pathname === new URL(\"checkout.html\", window.location.href).pathname",
                "url.searchParams.get(\"paymentResult\") === \"success\"",
                "[\"paymentKey\", \"orderId\", \"amount\", \"checkoutOrderId\"]"
            )
            .containsSubsequence(
                "storedUrl = readCheckoutSessionValue(paymentCallbackLoginReturnUrlKey);",
                "removeCheckoutSessionValue(paymentCallbackLoginReturnUrlKey);",
                "return isPaymentSuccessCallbackUrl(callbackUrl) ? callbackUrl.toString() : null;"
            );
        assertThat(login).containsSubsequence(
            "const paymentReturnUrl = takePaymentCallbackLoginReturnUrl();",
            "window.location.href = orderHistoryLoginReturnUrl() || checkoutLoginReturnUrl()",
            "|| paymentReturnUrl || loginRedirectUrl;"
        );
        mockMvc.perform(get("/orders.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-order-history")));

        assertThat(renewal).containsSubsequence(
            "context.idempotencyKey = randomUuid();",
            "delete context.paymentId;",
            "writeCheckoutPaymentContext(context);"
        );
        assertThat(retry).containsSubsequence(
            "const clientKey = await fetchTossPaymentClientKey();",
            "const reservationResponse = await requestAuth(",
            "if (!reservationResponse.ok) {",
            "const payment = await createTossPayment(clientKey);",
            "renewCheckoutPaymentAttempt(context);",
            "await requestTossPay(payment, context);"
        );
        assertThat(callback)
            .contains("headers: { \"Idempotency-Key\": context.idempotencyKey }")
            .containsSubsequence(
                "if (response.status === 401) {",
                "preservePaymentCallbackLoginReturnUrl();",
                "renderPaymentFailure("
            )
            .doesNotContain("renewCheckoutPaymentAttempt(context)");
        assertThat(checkoutSession).containsSubsequence(
            "if (!session.authenticated) {",
            "if (paymentResult === \"success\") {",
            "preservePaymentCallbackLoginReturnUrl();",
            "renderCheckoutGuard(guard,"
        );
        int accountMismatchStart = checkoutSession.indexOf(
            "if (paymentResult && storedPaymentContext?.checkoutAccount");
        String accountMismatch = checkoutSession.substring(accountMismatchStart,
            checkoutSession.indexOf("await processPaymentSuccessCallback();", accountMismatchStart));
        assertThat(accountMismatch).containsSubsequence(
            "storedPaymentContext.checkoutAccount !== session.checkoutAccount",
            "if (paymentResult === \"success\") {",
            "preservePaymentCallbackLoginReturnUrl();",
            "renderCheckoutGuard(guard,",
            "return;"
        );
    }
}
