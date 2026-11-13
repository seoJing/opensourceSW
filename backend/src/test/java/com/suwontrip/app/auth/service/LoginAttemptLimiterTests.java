package com.suwontrip.app.auth.service;

import com.suwontrip.app.auth.config.AuthProperties;
import com.suwontrip.app.auth.exception.AuthException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTests {
    @Test
    void attemptsBecomeAvailableAfterWindowWithoutSleeping() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(2), clock);
        for (int i = 0; i < 5; i++) limiter.acquire("client");
        assertThatThrownBy(() -> limiter.acquire("client")).isInstanceOfSatisfying(
                AuthException.class, failure -> {
                    assertThat(failure.code().status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(failure.retryAfter()).isEqualTo(60);
                });

        clock.advance(Duration.ofMillis(59_500));
        assertThatThrownBy(() -> limiter.acquire("client")).isInstanceOfSatisfying(
                AuthException.class, failure -> assertThat(failure.retryAfter()).isEqualTo(1));
        clock.advance(Duration.ofMillis(500));
        limiter.acquire("client");
    }

    @Test
    void boundedStorageRejectsOverflowAndReclaimsExpiredClients() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(2), clock);
        limiter.acquire("one");
        limiter.acquire("two");
        assertThatThrownBy(() -> limiter.acquire("three")).isInstanceOfSatisfying(
                AuthException.class, failure -> assertThat(failure.code().status())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        clock.advance(Duration.ofMinutes(1));
        limiter.acquire("three");
    }

    @Test
    void concurrentRequestsCannotExceedLimit() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(2), new MutableClock());
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            attempts.add(() -> {
                try {
                    limiter.acquire("same-client");
                    return true;
                } catch (AuthException failure) {
                    assertThat(failure.code().status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    return false;
                }
            });
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            int accepted = 0;
            for (var result : executor.invokeAll(attempts)) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(5);
        }
    }

    private static AuthProperties properties(int maxClients) {
        return new AuthProperties(new AuthProperties.Demo("", "", "", ""),
                5, Duration.ofMinutes(1), maxClients);
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }
}
