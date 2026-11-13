package com.suwontrip.app.auth.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class AuthTestAccount {
    public static final String LOGIN_ID = "test-login";
    public static final String PASSWORD = "test-only-passphrase";

    public static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    private AuthTestAccount() { }

    public static void configure(DynamicPropertyRegistry registry) {
        registry.add("app.auth.demo.user-id", () -> "test-user");
        registry.add("app.auth.demo.display-name", () -> "테스트 사용자");
        registry.add("app.auth.demo.login-id", () -> LOGIN_ID);
        registry.add("app.auth.demo.password-hash", () -> HASH);
    }
}
