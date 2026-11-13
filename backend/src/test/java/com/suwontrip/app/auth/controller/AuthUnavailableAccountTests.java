package com.suwontrip.app.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.auth.demo.user-id=", "app.auth.demo.display-name=",
        "app.auth.demo.login-id=", "app.auth.demo.password-hash="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthUnavailableAccountTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void missingDemoAccountIsReportedRatherThanInventingOne() throws Exception {
        mvc.perform(post("/api/v1/auth/session").with(csrf()).contentType("application/json")
                        .content("{\"loginId\":\"any-account\",\"password\":\"any-password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
