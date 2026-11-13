package com.suwontrip.app.auth.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTests {
    @Test
    void emptyDemoAccountRemainsDisabled() {
        assertThat(new AuthProperties.Demo("", "", "", "").configured()).isFalse();
    }

    @Test
    void invalidBcryptFormatFailsWithoutEchoingConfiguredValues() {
        assertThatThrownBy(() -> new AuthProperties.Demo("user", "name", "login", "not-a-hash"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("not-a-hash");
    }

    @Test
    void partialAccountFailsWithoutEchoingConfiguredValues() {
        assertThatThrownBy(() -> new AuthProperties.Demo("user", "name", "test-login-id", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("test-login-id");
    }

    @Test
    void invalidBcryptCostFailsAtConfigurationBoundary() {
        assertThatThrownBy(() -> new AuthProperties.Demo("user", "name", "login", "$2a$99$" + "a".repeat(53)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidRateLimitPolicyFailsAtConfigurationBoundary() {
        AuthProperties.Demo demo = new AuthProperties.Demo("", "", "", "");
        assertThatThrownBy(() -> new AuthProperties(demo, 0, Duration.ofMinutes(1), 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthProperties(demo, 5, Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthProperties(demo, 5, Duration.ofMillis(999), 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthProperties(demo, 5, Duration.ofHours(2), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
