package com.nanacocoa.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import(HealthContractHttpTest.ProbeConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:health-contract;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class HealthContractHttpTest {
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private ContractProbe probe;
    // 쿠키·로그인·CSRF 토큰 없이 실제 HTTP 필터와 Actuator를 통과한다.
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach @AfterEach
    void resetProbe() { probe.state.set(Status.UP); }

    @Test void anonymousHealthyResponseContainsOnlyUpStatus() throws Exception {
        assertPublicHealth(200, "UP");
    }

    @Test void downIndicatorProduces503WithoutExposingInternalDetails() throws Exception {
        probe.state.set(Status.DOWN);
        assertPublicHealth(503, "DOWN");
    }

    @Test void outOfServiceIndicatorProduces503WithoutExposingInternalDetails() throws Exception {
        probe.state.set(Status.OUT_OF_SERVICE);
        assertPublicHealth(503, "OUT_OF_SERVICE");
    }

    @Test void retiredHealthAndProtectedResourcesStillRequireAuthentication() throws Exception {
        assertThat(send("GET", "/api/health").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/orders").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/logout").statusCode()).isEqualTo(403);
        assertPublicHealth(200, "UP");
    }

    private void assertPublicHealth(int code, String state) throws Exception {
        var response = send("GET", "/actuator/health");
        assertThat(response.statusCode()).isEqualTo(code);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("json"));
        assertThat(json.readTree(response.body())).isEqualTo(json.createObjectNode().put("status", state));
        assertThat(response.body()).doesNotContain("synthetic-private", "contractProbe", "components", "details");
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean ContractProbe contractProbeHealthIndicator() { return new ContractProbe(); }
    }

    static class ContractProbe implements HealthIndicator {
        private final AtomicReference<Status> state = new AtomicReference<>(Status.UP);
        @Override public Health health() {
            return Health.status(state.get()).withDetail("connection", "synthetic-private-connection")
                    .withException(new IllegalStateException("synthetic-private-error")).build();
        }
    }
}
