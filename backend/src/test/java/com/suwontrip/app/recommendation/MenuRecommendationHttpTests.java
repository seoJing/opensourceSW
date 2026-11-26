package com.suwontrip.app.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwontrip.app.auth.dto.SessionResponse;
import com.suwontrip.app.auth.security.SessionSecurity;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.questionnaire.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuRecommendationHttpTests {
    private static final String URL = "/api/v1/menu-recommendations";
    private static final PreferenceProfile PROFILE = new PreferenceProfile(null, null, null, null, null, null);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private QuestionnaireDefinition definition;
    @MockitoBean private SessionSecurity sessions;
    @MockitoBean private PreferenceProfileRepository profiles;
    @MockitoBean private MenuCardLookup cards;

    @Test
    void requiresAuthenticationBeforeCsrfAndRequiresTokenAfterLogin() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post(URL).with(user("login"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"excludedMenuIds\":null}", "{\"excludedMenuIds\":[null]}",
            "{\"excludedMenuIds\":[\"  \"]}", "{\"excludedMenuIds\":[1]}", "{\"excludedMenuIds\":[\"a\",\"a\"]}",
            "{\"excludedMenuIds\":[],\"userId\":\"other\"}"})
    void rejectsInvalidExclusions(String body) throws Exception {
        mvc.perform(submit(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void enforcesExclusionLimitAndRequiresCurrentSavedAnswers() throws Exception {
        login();
        var ids = IntStream.range(0, 101).mapToObj(Integer::toString).toList();
        mvc.perform(submit(body(ids))).andExpect(status().isBadRequest());
        mvc.perform(submit(body(ids.subList(0, 100)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREFERENCES_REQUIRED"));
    }

    @Test
    void returnsOneCardWithCafeOrExplicitNullOnExhaustion() throws Exception {
        login();
        when(profiles.findForUser("stored-account", "v0.1.0")).thenReturn(Optional.of(PROFILE));
        var cafe = new MenuCardLookup.Cafe("c", "검증 카페", "검증 주소", null, null);
        var card = new MenuCardLookup.Card("m", "c", "검증 메뉴", "", null, "검증 메뉴", false, cafe);
        when(cards.next(PROFILE, Set.of())).thenReturn(Optional.of(card));
        mvc.perform(submit(body(List.of()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(json.writeValueAsString(Map.of("menu", card)), true));
        mvc.perform(submit(body(List.of("m")))).andExpect(status().isOk()).andExpect(content().string("{\"menu\":null}"));
    }

    @Test
    void reportsStorageFailureWithoutReturningAnEmptyCard() throws Exception {
        login();
        when(profiles.findForUser("stored-account", "v0.1.0")).thenThrow(new DataRetrievalFailureException("test failure"));
        mvc.perform(submit(body(List.of()))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE")).andExpect(jsonPath("$.menu").doesNotExist());
    }

    private void login() {
        when(definition.version()).thenReturn("v0.1.0");
        when(sessions.current(any())).thenReturn(new SessionResponse(true,
                new SessionResponse.User("stored-account", "사용자"), "token"));
    }

    private String body(List<String> ids) throws Exception {
        return json.writeValueAsString(Map.of("excludedMenuIds", ids));
    }

    private MockHttpServletRequestBuilder submit(String body) {
        return post(URL).with(user("login")).with(csrf()).contentType("application/json").content(body);
    }
}
