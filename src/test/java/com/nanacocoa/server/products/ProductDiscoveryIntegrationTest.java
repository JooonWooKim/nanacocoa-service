package com.nanacocoa.server.products;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.products.dto.reqeust.CategoryRequests;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.entity.SaleStatus;
import com.nanacocoa.server.products.repository.ProductCategoryRepository;
import com.nanacocoa.server.products.repository.ProductsRepository;
import com.nanacocoa.server.products.service.ProductDiscoveryService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static com.nanacocoa.server.CsrfRequests.csrf;
import static com.nanacocoa.server.common.exception.ErrorCode.CATEGORY_UNAVAILABLE;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.sql.init.mode=never", "spring.jpa.open-in-view=false", "cart.cleanup.initial-delay=1h",
    "product-image.storage-mode=mock", "toss-payments.enabled=false"})
class ProductDiscoveryIntegrationTest {
    private static final String API = "/api/products";
    private static final String ADMIN = API + "/admin/categories";
    private static final String PASSWORD = "SyntheticDiscovery123!";
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/n0sAAAAASUVORK5CYII=";

    @DynamicPropertySource static void database(DynamicPropertyRegistry values) {
        String mysql = System.getenv("DISCOVERY_TEST_DB_URL");
        if (mysql != null && !mysql.startsWith("jdbc:mysql://127.0.0.1:")) {
            throw new IllegalArgumentException("Loopback test database required");
        }
        values.add("spring.datasource.url", () -> mysql == null
                ? "jdbc:h2:mem:discovery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : mysql);
        values.add("spring.datasource.driver-class-name", () -> mysql == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        values.add("spring.datasource.username", () -> mysql == null ? "sa" : "root");
        values.add("spring.datasource.password", () -> "");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @Autowired ProductsRepository products;
    @Autowired ProductCategoryRepository categories;
    @Autowired ProductDiscoveryService discovery;
    @Autowired TransactionTemplate transaction;

    Member admin;
    String fixture;

    @BeforeEach void seedAdmin() {
        fixture = UUID.randomUUID().toString().substring(0, 10);
        admin = member("admin", true);
    }

    private Member member(String role, boolean admin) {
        Member member = Member.builder().email(role + "-" + fixture + "@example.test").name("가상 운영 검증")
                .password(encoder.encode(PASSWORD)).build();
        member.setAdmin(admin);
        return members.saveAndFlush(member);
    }

    private MockHttpSession login(Member member) throws Exception {
        HttpSession session = mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", member.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        return (MockHttpSession) session;
    }

    private UserDetailsImpl principal() { return new UserDetailsImpl(admin, admin.getEmail()); }

    private Long createCategory(String name) {
        return discovery.create(new CategoryRequests.Create(name + " " + fixture), principal()).id();
    }

    private Products product(String name, Long categoryId) {
        Products product = Products.builder().name(name).price(42000L).summary("합성 상품")
                .detailTitle("합성 설명").description("테스트 상품").build();
        product.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        product.updateInventory(5, SaleStatus.ON_SALE, 0);
        product.assignCategory(categoryId);
        return products.saveAndFlush(product);
    }

    @Test void publicSearchCombinesLiteralNameAndCategoryWithoutLeakingInventory() throws Exception {
        Long included = createCategory("검색");
        Long excluded = createCategory("다른 분류");
        var match = product(fixture + " 한글 Mug%_A", included);
        product(fixture + " 한글 MUGxxa", included);
        product(fixture + " 한글 Mug%_A 다른 분류", excluded);

        mvc.perform(get(API + "/search").param("q", "  " + fixture + " 한글 mUG%_a  ")
                        .param("category", included.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(match.getId()))
                .andExpect(jsonPath("$.data.items[0].stockQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].reservedQuantity").doesNotExist());
        mvc.perform(get(API).param("q","n/a")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get(API + "/" + match.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").doesNotExist()).andExpect(jsonPath("$.data.price").value(42000));
    }

    @Test void paginatesInStableOrderAndReturnsEmptyOutOfRange() throws Exception {
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 25; i++) ids.add(product(fixture + " page " + i, null).getId());
        Collections.reverse(ids);
        mvc.perform(get(API + "/search").param("q", fixture))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(24))
                .andExpect(jsonPath("$.data.items[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.data.items[23].id").value(ids.get(23)))
                .andExpect(jsonPath("$.data.totalPages").value(2)).andExpect(jsonPath("$.data.totalItems").value(25));
        mvc.perform(get(API + "/search").param("q", fixture).param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(ids.get(24)));
        mvc.perform(get(API + "/search").param("q", fixture).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get(API + "/search").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/search").param("q", "x".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/search").param("category", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/search").param("category", "9223372036854775807")).andExpect(status().isNotFound());
    }

    @Test void renameHideAndRestoreKeepsIdAndProductLink() throws Exception {
        long id = createCategory("기존");
        var product = product(fixture + " category", id);
        var session = login(admin);
        mvc.perform(patch(ADMIN + "/" + id).session(session).with(csrf(mvc)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "새 이름 " + fixture, "sortOrder", 0, "visible", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.visible").value(false));
        assertThat(discovery.publicCategories()).noneMatch(row -> row.id() == id);
        assertThat(discovery.adminCategories(principal())).anyMatch(row -> row.id() == id && !row.visible()
                && row.sortOrder() == 0 && row.name().equals("새 이름 " + fixture));
        mvc.perform(get(API + "/search").param("category", String.valueOf(id))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCTS_08"));
        mvc.perform(get(API + "/search").param("q", fixture)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(product.getId()));
        assertThat(products.findById(product.getId()).orElseThrow().getCategoryId()).isEqualTo(id);
        discovery.update(id, new CategoryRequests.Change(null, null, true), principal());
        assertThat(discovery.search(fixture, id, 1).items()).hasSize(1);
        assertThat(discovery.publicCategories()).anyMatch(row -> row.id() == id);
    }

    @Test void concurrentCaseInsensitiveNameCreationHasOneWinner() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> createUnlessDuplicate("  Test " + fixture);
            Callable<Boolean> second = () -> createUnlessDuplicate("test " + fixture.toUpperCase(Locale.ROOT));
            var results = workers.invokeAll(List.of(first, second));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally { workers.shutdownNow(); }
    }

    private boolean createUnlessDuplicate(String label) {
        try {
            discovery.create(new CategoryRequests.Create(label), principal());
            return true;
        } catch (NanacocoaException duplicate) {
            assertThat(duplicate.getErrorCode().name()).isEqualTo("CATEGORY_NAME_CONFLICT");
            return false;
        }
    }

    @Test void existingAndMultipartRegistrationAssignAndClearWithoutChangingStock() throws Exception {
        Long id = createCategory("등록");
        var session = login(admin);
        var body = new HashMap<String,Object>(Map.of("name", fixture + " JSON", "price", 42000, "summary", "가상",
                "detailTitle", "가상 상세", "description", "가상 상품", "categoryId", id));
        mvc.perform(post(API).session(session).with(csrf(mvc)).contentType("application/json")
                        .content(json.writeValueAsString(body))).andExpect(status().isCreated());
        body.put("name", fixture + " multipart");
        mvc.perform(multipart(API).file(new org.springframework.mock.web.MockMultipartFile("product", "",
                        "application/json", json.writeValueAsBytes(body)))
                .file(new org.springframework.mock.web.MockMultipartFile("image", "fixture.png", "image/png",
                        Base64.getDecoder().decode(PNG))).session(session).with(csrf(mvc))).andExpect(status().isCreated());
        assertThat(discovery.search(fixture, id, 1).totalItems()).isEqualTo(2);
        var item = product(fixture + " inventory", id);
        String assignment = API + "/admin/" + item.getId() + "/category";
        mvc.perform(put(assignment).session(session).with(csrf(mvc)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(assignment).session(session).with(csrf(mvc)).contentType("application/json")
                .content("{\"categoryId\":null}")).andExpect(status().isOk());
        var saved = products.findById(item.getId()).orElseThrow();
        assertThat(saved.getCategoryId()).isNull();
        assertThat(saved.getStockQuantity()).isEqualTo(5);
        assertThat(saved.getPrice()).isEqualTo(42000);
    }

    @Test void hideCommittedBeforeAssignmentRejectsNewLink() throws Exception {
        Long id = createCategory("경합");
        Long item = product(fixture + " race", null).getId();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var beforeLock = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var hide = workers.submit(() -> transaction.executeWithoutResult(status -> {
                categories.findForUpdate(id).orElseThrow().show(false);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var assign = workers.submit(() -> {
                beforeLock.countDown();
                assertThatThrownBy(() -> discovery.assign(item, new CategoryRequests.Assignment(id), principal()))
                        .isInstanceOfSatisfying(NanacocoaException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CATEGORY_UNAVAILABLE));
            });
            assertThat(beforeLock.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            hide.get(10, TimeUnit.SECONDS);
            assign.get(10, TimeUnit.SECONDS);
            assertThat(products.findById(item).orElseThrow().getCategoryId()).isNull();
        } finally { release.countDown(); workers.shutdownNow(); }
    }

    @Test void directWritesRequireAdminAndCsrfAndExpiredSessionDoesNotWrite() throws Exception {
        String payload = json.writeValueAsString(Map.of("name", fixture + " permission"));
        mvc.perform(get(API + "/categories")).andExpect(status().isOk());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN).with(csrf(mvc)).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN).session(login(member("buyer", false))).with(csrf(mvc))
                .contentType("application/json").content(payload)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_02"));
        var session = login(admin);
        mvc.perform(post(ADMIN).session(session).contentType("application/json").content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        session.invalidate();
        mvc.perform(post(ADMIN).with(csrf(mvc)).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        assertThat(discovery.adminCategories(principal())).noneMatch(row -> row.name().equals(fixture + " permission"));
    }
}
