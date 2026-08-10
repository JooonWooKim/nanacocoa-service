package com.nanacocoa.server;

import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.member.dto.reqeust.SignupRequest;
import com.nanacocoa.server.member.service.AuthService;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:nanacocoa-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
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

        MvcResult login = mockMvc.perform(post("/api/auth/login")
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
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void loginCreatesAuthenticatedSessionForDemoUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "hello@nanacocoa.kr",
                      "password": "nanacocoa123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("hello@nanacocoa.kr"))
            .andExpect(jsonPath("$.name").value("nanacocoa"))
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void loginRejectsInvalidPasswordWithoutCreatingSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "hello@nanacocoa.kr",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해 주세요."))
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void signupStoresMemberWithBcryptedPasswordAndCreatesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "signup-success@example.com",
                      "password": "signup123!",
                      "name": "가입자"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("signup-success@example.com"))
            .andExpect(jsonPath("$.name").value("가입자"))
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();

        Member member = memberRepository.findByEmail("signup-success@example.com").orElseThrow();
        assertThat(member.getName()).isEqualTo("가입자");
        assertThat(member.isAdmin()).isFalse();
        assertThat(member.getPassword()).isNotEqualTo("signup123!");
        assertThat(passwordEncoder.matches("signup123!", member.getPassword())).isTrue();
    }

    @Test
    void signupCreatesNonAdminMember() {
        String email = "non-admin-signup@example.com";

        authService.signUp(new SignupRequest(email, "signup123!", "일반 가입자"));

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
                .file(pngImagePart(validPngBytes(16)))
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
                .file(pngImagePart(validPngBytes(16)))
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
                .file(invalidImage)
                .session(adminSession()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRODUCTS_03"));
    }

    @Test
    void multipartRegistrationRejectsImageLargerThanFiveMegabytes() throws Exception {
        mockMvc.perform(multipart("/api/products")
                .file(productPart("대용량 이미지 상품"))
                .file(pngImagePart(validPngBytes(5 * 1024 * 1024 + 1)))
                .session(adminSession()))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PRODUCTS_04"));
    }

    @Test
    void signupMemberCanLoginWithEmailAndPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login-after-signup@example.com",
                      "password": "signup123!",
                      "name": "로그인회원"
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login-after-signup@example.com",
                      "password": "signup123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("login-after-signup@example.com"))
            .andExpect(jsonPath("$.name").value("로그인회원"))
            .andReturn();

        assertThat(login.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        String signupBody = """
            {
              "email": "duplicate@example.com",
              "password": "signup123!",
              "name": "중복회원"
            }
            """;

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
    }

    @Test
    void signupRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-email",
                      "password": "signup123!",
                      "name": "가입자"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.authenticated").value(false));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "blank-name@example.com",
                      "password": "signup123!",
                      "name": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.authenticated").value(false));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "short-password@example.com",
                      "password": "short",
                      "name": "가입자"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void registerProductStoresDetailPageFields() throws Exception {
        mockMvc.perform(post("/api/products")
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
        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(post("/api/products")
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
        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(post("/api/products")
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                .session(adminSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRODUCTS_01"))
            .andExpect(jsonPath("$.error").value("이미 등록된 상품입니다."));
    }

    @Test
    void registerProductRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(post("/api/products")
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
                .session(memberSession)
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
                .session(memberSession)
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
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "hello@nanacocoa.kr",
                      "password": "nanacocoa123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("hello@nanacocoa.kr"))
            .andExpect(jsonPath("$.name").value("nanacocoa"));
    }

    @Test
    void logoutClearsAuthenticatedSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "hello@nanacocoa.kr",
                      "password": "nanacocoa123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));

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

        mockMvc.perform(post("/api/orders")
                .session(memberSession)
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
                .session(memberSession)
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
        mockMvc.perform(post("/api/orders")
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-search")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-basic")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-detail")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-address-search-status")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("쿠폰"))));
    }
}
