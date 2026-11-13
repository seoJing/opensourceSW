package com.suwontrip.app.auth.integration;

import com.suwontrip.app.auth.support.AuthTestAccount;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthHttpTests {
    @LocalServerPort private int port;
    @Autowired private ObjectMapper mapper;

    @DynamicPropertySource
    static void account(DynamicPropertyRegistry registry) {
        AuthTestAccount.configure(registry);
    }

    @Test
    void realCookieSurvivesRequestsRotatesOnLoginAndLosesAuthenticationOnLogout() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).build();
             HttpClient oldBrowser = HttpClient.newHttpClient()) {
            URI endpoint = URI.create("http://localhost:" + port + "/api/v1/auth/session");
            HttpResponse<String> initial = browser.send(HttpRequest.newBuilder(endpoint).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(initial.statusCode()).isEqualTo(200);
            String cookieHeader = initial.headers().firstValue("Set-Cookie").orElseThrow();
            assertThat(cookieHeader).contains("HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Secure");
            String oldSessionId = sessionId(cookies);
            String token = mapper.readTree(initial.body()).get("csrfToken").asText();

            HttpResponse<String> login = browser.send(HttpRequest.newBuilder(endpoint)
                            .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                                    "loginId", AuthTestAccount.LOGIN_ID, "password", AuthTestAccount.PASSWORD))))
                            .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(sessionId(cookies)).isNotEqualTo(oldSessionId);
            JsonNode loggedIn = mapper.readTree(login.body());
            assertThat(loggedIn.get("authenticated").asBoolean()).isTrue();

            HttpResponse<String> next = browser.send(HttpRequest.newBuilder(endpoint).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(mapper.readTree(next.body()).get("authenticated").asBoolean()).isTrue();

            HttpResponse<String> stale = oldBrowser.send(HttpRequest.newBuilder(endpoint)
                            .header("Cookie", "JSESSIONID=" + oldSessionId).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(stale.statusCode()).isEqualTo(401);

            String authenticatedId = sessionId(cookies);
            HttpResponse<String> logout = browser.send(HttpRequest.newBuilder(endpoint)
                            .header("X-CSRF-TOKEN", loggedIn.get("csrfToken").asText()).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(logout.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(logout.body()).get("authenticated").asBoolean()).isFalse();
            assertThat(sessionId(cookies)).isNotEqualTo(authenticatedId);
            HttpResponse<String> reuse = oldBrowser.send(HttpRequest.newBuilder(endpoint)
                            .header("Cookie", "JSESSIONID=" + authenticatedId).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(reuse.statusCode()).isEqualTo(401);
        }
    }

    private static String sessionId(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .findFirst().orElseThrow().getValue();
    }
}
