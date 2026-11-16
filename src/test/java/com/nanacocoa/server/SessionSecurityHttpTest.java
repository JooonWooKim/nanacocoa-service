package com.nanacocoa.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:session-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
class SessionSecurityHttpTest {
    private static final String LOGIN = "/api/auth/login";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String ORDERS = "/api/orders";
    private static final String CREDENTIALS = """
        {"email":"session-test@example.test","password":"test-password"}
        """;

    @LocalServerPort
    private int port;
    @Autowired
    private MemberRepository members;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void browserSessionRotatesAndLogoutRevokesAccess() throws Exception {
        members.saveAndFlush(Member.builder().email("session-test@example.test")
                .name("세션 테스트").password(encoder.encode("test-password")).build());

        var initial = issueToken(null);
        String anonymous = sessionCookie(initial);
        Token beforeLogin = token(initial);
        Token otherSession = token(issueToken(null));
        assertCsrfRejected(send("POST", LOGIN, anonymous, null, CREDENTIALS));
        assertCsrfRejected(send("POST", LOGIN, anonymous, otherSession, CREDENTIALS));
        assertThat(send("GET", ORDERS, anonymous, null, null).statusCode()).isEqualTo(401);

        var failedLogin = send("POST", LOGIN, anonymous, beforeLogin,
                CREDENTIALS.replace("test-password", "incorrect"));
        assertThat(failedLogin.statusCode()).isEqualTo(400);
        assertThat(failedLogin.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(send("GET", ORDERS, anonymous, null, null).statusCode()).isEqualTo(401);

        var login = send("POST", LOGIN, anonymous, beforeLogin, CREDENTIALS);
        assertThat(login.statusCode()).isEqualTo(200);
        String authenticated = sessionCookie(login);
        assertThat(authenticated.equals(anonymous)).isFalse();
        assertThat(send("GET", ORDERS, anonymous, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", ORDERS, authenticated, null, null).statusCode()).isEqualTo(200);

        assertCsrfRejected(send("POST", LOGOUT, authenticated, beforeLogin, null));
        assertThat(send("GET", LOGOUT, authenticated, null, null).statusCode()).isEqualTo(405);
        assertThat(send("GET", ORDERS, authenticated, null, null).statusCode()).isEqualTo(200);

        Token afterLogin = token(issueToken(authenticated));
        var logout = send("POST", LOGOUT, authenticated, afterLogin, null);
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(json.readTree(logout.body()).path("authenticated").asBoolean()).isFalse();
        assertThat(send("GET", ORDERS, authenticated, null, null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> issueToken(String cookie) throws Exception {
        var response = send("GET", "/api/auth/csrf", cookie, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return response;
    }

    private String sessionCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    private Token token(HttpResponse<String> response) throws Exception {
        var data = json.readTree(response.body()).path("data");
        return new Token(data.path("headerName").asText(), data.path("token").asText());
    }

    private void assertCsrfRejected(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("CSRF_INVALID");
    }

    private HttpResponse<String> send(String method, String path, String cookie, Token token, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) request.header("Cookie", cookie);
        if (token != null) request.header(token.headerName(), token.value());
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Token(String headerName, String value) {}
}
