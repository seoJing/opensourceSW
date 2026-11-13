package com.suwontrip.app.auth.service;

import com.suwontrip.app.auth.config.AuthProperties;
import com.suwontrip.app.auth.dto.LoginRequest;
import com.suwontrip.app.auth.dto.SessionResponse;
import com.suwontrip.app.auth.exception.AuthErrorCode;
import com.suwontrip.app.auth.exception.AuthException;
import com.suwontrip.app.auth.security.SessionSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 세션 API의 작업 순서를 조정. HTTP 입력은 Controller가 받고,
 * 1. 실제 자격 증명 검증은 AuthenticationManager,
 * 2. 세션 변경은 SessionSecurity가 수행한다.
 */
@Service
public class AuthSessionService {
    private final AuthProperties properties;
    private final LoginAttemptLimiter limiter;
    private final AuthenticationManager authenticationManager;
    private final SessionSecurity sessions;

    public AuthSessionService(AuthProperties properties, LoginAttemptLimiter limiter,
                              AuthenticationManager authenticationManager, SessionSecurity sessions) {
        this.properties = properties;
        this.limiter = limiter;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
    }

    // 인증 상태 조회는 계정 설정 여부와 무관하다. 익명 사용자도 CSRF 토큰을 받을 수 있다.
    public SessionResponse current(HttpServletRequest request) {
        return sessions.current(request);
    }

    // 로그인은 시연 계정 설정 확인 → 시도 제한 → 비밀번호 검증 → 세션 수립 순서로 처리한다.
    // 앞 단계에서 실패하면 뒤 단계는 실행하지 않으므로 인증 실패로 세션이 바뀌지 않는다.
    public SessionResponse login(LoginRequest credentials, HttpServletRequest request,
                                 HttpServletResponse response) {

        // 계정을 설정하지 않은 환경에서는 로그인만 503으로 거부한다.
        if (!properties.demo().configured()) {
            throw new AuthException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }

        // 서버가 본 원격 IP를 기준으로 성공과 실패를 모두 집계한다. 전달 헤더는 신뢰하지 않는다.
        limiter.acquire(request.getRemoteAddr());

        // 미인증 토큰을 전달하고 Spring Security의 계정 조회·BCrypt 검증에 맡긴다.
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.loginId(), credentials.password()));

        // 검증에 성공한 뒤에만 인증 정보를 세션에 저장하고 ID와 CSRF 토큰을 갱신한다.
        sessions.establish(authentication, request, response);
        return sessions.current(request);
    }

    // 기존 인증 세션을 무효화한 다음 새 익명 상태와 CSRF 토큰을 응답한다.
    public SessionResponse logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.invalidate(request, response);
        return sessions.current(request);
    }
}
