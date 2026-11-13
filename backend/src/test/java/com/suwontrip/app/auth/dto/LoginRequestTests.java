package com.suwontrip.app.auth.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTests {
    @Test
    void credentialsAreRedactedFromDiagnosticString() {
        LoginRequest request = new LoginRequest("login-test", "password-test");
        assertThat(request.toString()).doesNotContain("login-test", "password-test");
    }
}
