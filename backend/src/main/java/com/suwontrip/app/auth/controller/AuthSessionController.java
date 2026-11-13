package com.suwontrip.app.auth.controller;

import com.suwontrip.app.auth.dto.LoginRequest;
import com.suwontrip.app.auth.dto.SessionResponse;
import com.suwontrip.app.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 인증 API의 HTTP 경계. 요청을 DTO로 받고 응답 헤더를 구성하며,
 * 계정 검증·시도 제한·세션 변경은 AuthSessionService에 위임
 */
@RestController
@RequestMapping("/api/v1/auth/session")
public class AuthSessionController {
    private final AuthSessionService sessions;

    public AuthSessionController(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    // 공개 엔드포인트, 익명 요청에도 현재 인증 상태와 다음 변경 요청에 쓸 CSRF 토큰을 돌려준다.
    @GetMapping
    public ResponseEntity<SessionResponse> current(HttpServletRequest request) {
        return noStore(sessions.current(request));
    }

    // 1. CSRF 검사는 Controller에 도착하기 전에 Security 필터에서 수행
    // 2. JSON 본문을 LoginRequest로 변환한 뒤 Bean Validation을 실행
    // 3. 응답 객체는 세션 ID 교체와 쿠키 처리에 필요해 서비스까지 전달
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest credentials,
            HttpServletRequest request, HttpServletResponse response) {
        return noStore(sessions.login(credentials, request, response));
    }

    // 인증된 세션을 종료하고 새 익명 세션 상태를 반환
    @DeleteMapping
    public ResponseEntity<SessionResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        return noStore(sessions.logout(request, response));
    }

    // 세션 상태와 CSRF 토큰이 브라우저나 중간 캐시에 저장되지 않도록 공통 응답 헤더를 붙인다.
    private static ResponseEntity<SessionResponse> noStore(SessionResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
