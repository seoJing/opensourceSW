package com.suwontrip.app.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.suwontrip.app.auth.config.AuthProperties;
import com.suwontrip.app.auth.exception.AuthErrorCode;
import com.suwontrip.app.auth.exception.AuthException;
import org.springframework.stereotype.Component;

/**
 * 원격 IP별 로그인 시도를 첫 요청 시각부터 시작하는 고정 시간 구간으로 제한
 * 상태는 현재 애플리케이션 프로세스의 메모리에만 있으므로 재시작하거나 서버를 늘리면 공유되지 않는다.
 */
@Component
public class LoginAttemptLimiter {

    // 추적 가능한 IP 수를 설정값으로 제한해 임의의 주소가 메모리를 계속 차지하지 못하게 한다.
    private final Map<String, Window> clients = new HashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    public LoginAttemptLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    // 검사와 횟수 증가를 한 임계 구역에서 처리해 동시 요청이 제한을 넘겨 통과하지 못하게 한다.
    public synchronized void acquire(String clientAddress) {

        Instant now = clock.instant();

        // 구간이 끝난 기록은 새 요청을 처리하기 전에 제거해 저장 공간과 시도 횟수를 회수한다.
        clients.values().removeIf(window -> !now.isBefore(window.endsAt));

        Window window = clients.get(clientAddress);
        if (window == null) {

            // 만료 기록을 지운 뒤에도 꽉 찼다면 새 IP를 추적할 수 없어 503을 반환한다.
            if (clients.size() >= properties.maxTrackedClients()) {
                throw new AuthException(AuthErrorCode.SERVICE_UNAVAILABLE);
            }

            // 첫 시도를 기준으로 구간의 종료 시각을 고정한다. 이후 요청은 종료 시각을 연장하지 않는다.
            window = new Window(now.plus(properties.loginWindow()));
            clients.put(clientAddress, window);
        }

        if (window.attempts >= properties.maxLoginAttempts()) {
            // 남은 시간을 초 단위로 올림해 Retry-After 헤더에 0초가 들어가지 않게 한다.
            long millis = java.time.Duration.between(now, window.endsAt).toMillis();
            long retryAfter = Math.max(1, (millis + 999) / 1000);
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS, retryAfter);
        }

        // 인증 결과를 알기 전에 증가시키므로 성공과 실패가 모두 제한 횟수에 포함된다.
        window.attempts++;
    }

    // IP 하나의 고정 구간 종료 시각과 현재까지 허용한 시도 횟수다.
    private static final class Window {
        private final Instant endsAt;
        private int attempts;

        private Window(Instant endsAt) {
            this.endsAt = endsAt;
        }
    }
}
