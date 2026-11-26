package com.nanacocoa.server.e2e;

import com.nanacocoa.server.NanacocoaServerApplication;
import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.config.ReturnPolicy;
import com.nanacocoa.server.payment.pg.*;
import com.nanacocoa.server.products.entity.*;
import com.nanacocoa.server.products.repository.ProductsRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.resource.TransformedResource;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/** 테스트 runtime에서만 실행. 운영 jar·기본 test 작업에서는 시작하지 않는다. */
public class CommerceE2eServer {
    public static void main(String[] args) {
        new SpringApplicationBuilder(NanacocoaServerApplication.class, FixtureConfiguration.class)
            .profiles("test", "commerce-e2e").run(
                "--spring.config.name=application-e2e", "--server.address=127.0.0.1", "--server.port=18082",
                "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2",
                "--spring.datasource.url=jdbc:h2:mem:commerce_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.flyway.enabled=true",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                "--spring.sql.init.mode=never", "--toss-payments.enabled=false",
                "--toss-payments.client-key=test_ck_e2e_fixture", "--product-image.storage-mode=mock",
                "--payment.reconciliation.enabled=false", "--order.inventory.expiration-enabled=false",
                "--policy.signup-enabled=false", "--shipping.carriers.test.name=가상 택배",
                "--shipping.carriers.test.tracking-pattern=[0-9]{10}",
                "--logging.level.root=WARN", "--logging.level.nanacocoa.incident=INFO");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("commerce-e2e")
    public static class FixtureConfiguration implements WebMvcConfigurer {
        @Bean @Primary ScenarioProvider scenarioProvider() { return new ScenarioProvider(); }
        @Bean SnapshotService snapshotService(MemberRepository members, JdbcTemplate jdbc, ScenarioProvider pg) {
            return new SnapshotService(members, jdbc, pg);
        }
        @Bean ApplicationRunner seed(MemberRepository members, ProductsRepository products,
                                     PasswordEncoder passwords, ReturnPolicy policy) {
            return args -> {
                for (String role : List.of("buyer", "admin", "other")) {
                    var member = Member.builder().email(role+"@example.test").name("가상 "+role)
                        .password(passwords.encode("E2eFixture123!")).build();
                    member.setAdmin(role.equals("admin")); members.saveAndFlush(member);
                }
                var names = List.of("정상 구매", "승인 확인 중", "승인 거절", "환불 확인 중", "환불 실패 후 재시도", "검수 불합격");
                for (int i=0;i<names.size();i++) {
                    var p = Products.builder().name(names.get(i)).price(41000L+i*1000)
                        .summary("가상 종단 검증 상품").detailTitle(names.get(i)).description("테스트 전용")
                        .imageUrl("/logo.png").build();
                    p.updateInventory(20, SaleStatus.ON_SALE, 0); products.saveAndFlush(p);
                }
                policy.setEnabled(true); policy.setVersion("test-only-v1"); policy.setApprovedBy("test-operator");
                policy.setApprovedAt("2026-01-01T00:00:00Z"); policy.setGuidance("가상 검증 정책 · 실제 운영 승인이 아닙니다.");
                policy.setWindowDays(14); policy.setAllowInTransit(true); policy.setAllowReapply(true);
                policy.setRefundAfterFailedInspection(true);
                System.out.println("E2E_READY http://127.0.0.1:18082/checkout.html?id=1");
            };
        }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/checkout.html").addResourceLocations("classpath:/static/")
                .resourceChain(false).addTransformer((request, resource, chain) -> {
                    var original = chain.transform(request, resource);
                    String html;
                    try (var stream = original.getInputStream()) {
                        html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    return new TransformedResource(original, html.replace("</head>",
                        "<script src=\"/e2e-sdk.js\"></script></head>").getBytes(StandardCharsets.UTF_8));
                });
        }
    }

    /** 금액은 상품 1개 구매 기준. 서로 다른 시나리오도 실제 저장 API를 통과한다. */
    public static class ScenarioProvider implements TossPaymentsClient {
        private final Map<String,TossPaymentResponse> orders = new LinkedHashMap<>();
        private final Map<String,Integer> calls = new LinkedHashMap<>();
        private final Map<String,TossPaymentResponse> canceled = new HashMap<>();
        @Override public boolean isDisabled() { return false; }
        private int count(String operation, String order) { return calls.merge(operation+":"+order,1,Integer::sum); }
        @Override public synchronized TossPaymentResponse confirm(String key, String order, long amount, String idempotency) {
            int attempt=count("confirm",order);
            if (amount==43000) throw new TossPaymentsException(TossPaymentsException.Kind.DEFINITIVE,"E2E_DECLINED","가상 승인 거절");
            var value = orders.computeIfAbsent(order, ignored -> new TossPaymentResponse(key,order,amount,"DONE","카드",OffsetDateTime.now(),List.of()));
            if (amount==42000 && attempt==1) throw new TossPaymentsException(TossPaymentsException.Kind.UNCERTAIN,"E2E_TIMEOUT","가상 승인 응답 유실");
            return value;
        }
        @Override public synchronized TossPaymentResponse findByOrderId(String order) {
            count("lookup",order);
            var response=canceled.getOrDefault(order,orders.get(order));
            if (response==null) throw new TossPaymentsException(TossPaymentsException.Kind.NOT_FOUND,"E2E_NOT_FOUND","가상 거래 없음");
            return response;
        }
        @Override public synchronized TossPaymentResponse cancel(String key, String reason, String idempotency) {
            var original=orders.values().stream().filter(p->p.getPaymentKey().equals(key)).findFirst().orElseThrow();
            String order=original.getOrderId(); int attempt=count("cancel",order);
            if (original.getTotalAmount()==45000 && attempt==1)
                throw new TossPaymentsException(TossPaymentsException.Kind.DEFINITIVE,"E2E_DECLINED","가상 환불 거절");
            var result=canceled.computeIfAbsent(order, ignored -> new TossPaymentResponse(key,order,original.getTotalAmount(),"CANCELED","카드",original.getApprovedAt(),
                List.of(new TossCancelResponse(UUID.randomUUID().toString(),"DONE",OffsetDateTime.now()))));
            if (original.getTotalAmount()==44000 && attempt==1)
                throw new TossPaymentsException(TossPaymentsException.Kind.UNCERTAIN,"E2E_TIMEOUT","가상 환불 응답 유실");
            return result;
        }
        synchronized Map<String,Integer> counts() { return Map.copyOf(calls); }
    }

    public record SnapshotService(MemberRepository members, JdbcTemplate jdbc, ScenarioProvider provider) {
        public Map<String,Object> read(UserDetailsImpl user) {
            members.findByEmail(user.getUsername()).orElseThrow().checkAdmin();
            return Map.of("providerCalls",provider.counts(),
                "inventory",jdbc.queryForList("select id,name,stock_quantity,reserved_quantity from products order by id"),
                "orders",jdbc.queryForList("select order_id,status,stock_state from orders order by id"),
                "refunds",jdbc.queryForList("select id,status,retry_count from payment_cancellations order by id"));
        }
    }
    @RestController @Profile("commerce-e2e")
    public static class SnapshotController {
        private final SnapshotService service;
        SnapshotController(SnapshotService service) { this.service=service; }
        @GetMapping("/api/admin/e2e-snapshot")
        public SuccessMessage<Map<String,Object>> snapshot(@AuthenticationPrincipal UserDetailsImpl user) {
            return new SuccessMessage<>("가상 검증 상태", service.read(user));
        }
    }
}
