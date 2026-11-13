package com.suwontrip.app.auth.controller;

import com.suwontrip.app.auth.support.AuthTestAccount;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthSessionTests.ProtectedProbe.class)
class AuthSessionTests {
    private static final String PATH = "/api/v1/auth/session";
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @DynamicPropertySource
    static void account(DynamicPropertyRegistry registry) {
        AuthTestAccount.configure(registry);
    }

    @Test
    void anonymousSessionIssuesTokenWithoutExposingUserOrPassword() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").isEmpty())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginRotatesSessionAndTokenAndPersistsAuthentication() throws Exception {
        Session anonymous = anonymous();
        String oldId = anonymous.session().getId();
        Session loggedIn = login(anonymous);
        assertThat(loggedIn.session().getId()).isNotEqualTo(oldId);

        mvc.perform(get(PATH).session(loggedIn.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value("test-user"))
                .andExpect(jsonPath("$.user.displayName").value("테스트 사용자"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
        mvc.perform(post("/api/v1/test-auth-probe").session(loggedIn.session())
                        .header("X-CSRF-TOKEN", anonymous.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/test-auth-probe").session(loggedIn.session())
                        .header("X-CSRF-TOKEN", loggedIn.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value(AuthTestAccount.LOGIN_ID));
    }

    @Test
    void logoutInvalidatesOldSessionAndCreatesAnonymousSession() throws Exception {
        Session loggedIn = login(anonymous());
        String oldId = loggedIn.session().getId();
        MvcResult result = mvc.perform(delete(PATH).session(loggedIn.session())
                        .header("X-CSRF-TOKEN", loggedIn.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").isEmpty()).andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();
        assertThat(loggedIn.session().isInvalid()).isTrue();
        MockHttpSession next = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(next).isNotNull();
        assertThat(next.getId()).isNotEqualTo(oldId);
        mvc.perform(get(PATH).session(next)).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(delete(PATH).session(next).header("X-CSRF-TOKEN", loggedIn.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void wrongPasswordAndUnknownAccountUseSameError() throws Exception {
        Session session = anonymous();
        String first = mvc.perform(loginRequest(session, AuthTestAccount.LOGIN_ID, "wrong-password"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(loginRequest(session, "unknown-account", AuthTestAccount.PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(second).doesNotContain(AuthTestAccount.HASH, AuthTestAccount.PASSWORD);
        mvc.perform(get(PATH).session(session.session())).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void missingInvalidAndOtherSessionsTokensAreRejected() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        Session first = anonymous();
        Session other = anonymous();
        mvc.perform(post(PATH).session(first.session()).header("X-CSRF-TOKEN", "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).session(other.session()).header("X-CSRF-TOKEN", first.token())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOrExpiredAuthenticationTakesPriorityOverCsrf() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        Session loggedIn = login(anonymous());
        loggedIn.session().invalidate();
        mvc.perform(post("/api/v1/test-auth-probe").session(loggedIn.session())
                        .header("X-CSRF-TOKEN", loggedIn.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "{broken", "{\"loginId\":\"test-login\"}",
            "{\"loginId\":42,\"password\":\"value\"}",
            "{\"loginId\":true,\"password\":\"value\"}",
            "{\"loginId\":\"test-login\",\"password\":null}",
            "{\"loginId\":\"first\",\"loginId\":\"second\",\"password\":\"value\"}",
            "{\"loginId\":\"test-login\",\"password\":\"\"}",
            "{\"loginId\":\" \",\"password\":\"value\"}",
            "{\"loginId\":\"test-login\",\"password\":\"value\",\"role\":\"ADMIN\"}",
            "{\"loginId\":\"test-login\",\"password\":\"value\",\"passwordWithinBcryptLimit\":true}"
    })
    void malformedOrUnexpectedFieldsAreRejected(String body) throws Exception {
        Session session = anonymous();
        mvc.perform(post(PATH).session(session.session()).header("X-CSRF-TOKEN", session.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void passwordLengthUsesUtf8BytesInsteadOfCharacterCount() throws Exception {
        Session session = anonymous();
        mvc.perform(loginRequest(session, AuthTestAccount.LOGIN_ID, "가".repeat(25)))
                .andExpect(status().isBadRequest());
        mvc.perform(loginRequest(session, AuthTestAccount.LOGIN_ID, "a".repeat(72)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLoginsAreLimitedAndForwardedHeaderCannotBypassLimit() throws Exception {
        Session session = anonymous();

        for (int i = 0; i < 5; i++) {
            mvc.perform(loginRequest(session, AuthTestAccount.LOGIN_ID, "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        MvcResult result = mvc.perform(loginRequest(session, AuthTestAccount.LOGIN_ID, "wrong")
                        .header("X-Forwarded-For", "192.0.2.250"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(header().exists("Retry-After")).andReturn();
        assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After"))).isPositive();
    }

    @Test
    void healthRemainsPublicAndChecksDatabase() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    private Session anonymous() throws Exception {
        MvcResult result = mvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new Session((MockHttpSession) result.getRequest().getSession(false),
                body.get("csrfToken").asText(), "192.0.2." + NEXT_ADDRESS.incrementAndGet());
    }

    private Session login(Session session) throws Exception {
        MvcResult result = mvc.perform(loginRequest(session, AuthTestAccount.LOGIN_ID, AuthTestAccount.PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false),
                mapper.readTree(result.getResponse().getContentAsString()).get("csrfToken").asText(), session.ip());
    }

    private MockHttpServletRequestBuilder loginRequest(Session session, String loginId, String password)
            throws Exception {
        return post(PATH).session(session.session()).header("X-CSRF-TOKEN", session.token())
                .with(request -> { request.setRemoteAddr(session.ip()); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("loginId", loginId, "password", password)));
    }

    private String validBody() throws Exception {
        return mapper.writeValueAsString(Map.of("loginId", AuthTestAccount.LOGIN_ID,
                "password", AuthTestAccount.PASSWORD));
    }

    private record Session(MockHttpSession session, String token, String ip) { }

    @RestController
    static class ProtectedProbe {
        @PostMapping("/api/v1/test-auth-probe")
        Map<String, String> protectedRequest(Authentication authentication) {
            return Map.of("loginId", authentication.getName());
        }
    }
}
