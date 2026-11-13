package com.suwontrip.app.auth.controller;

import com.suwontrip.app.auth.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthSessionController.class)
// 예외의 HTTP 변환만 격리한다. 실제 보안 필터는 AuthSessionTests와 AuthHttpTests에서 검증한다.
@AutoConfigureMockMvc(addFilters = false)
class AuthExceptionHandlerTests {
    private static final String PATH = "/api/v1/auth/session";

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthSessionService sessions;

    @Test
    void unexpectedFailureUsesContractWithoutExposingInternalMessage() throws Exception {
        when(sessions.current(any())).thenThrow(new IllegalStateException("internal-detail-marker"));

        mvc.perform(get(PATH)).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(content().string(not(containsString("internal-detail-marker"))));
    }

    @Test
    void frameworkStatusExceptionRetainsItsStatus() throws Exception {
        when(sessions.current(any())).thenThrow(new ResponseStatusException(HttpStatus.GONE));

        mvc.perform(get(PATH)).andExpect(status().isGone());
    }

    @Test
    void unsupportedMethodRetains405() throws Exception {
        mvc.perform(put(PATH)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unsupportedMediaTypeRetains415() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
