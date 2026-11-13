package com.suwontrip.app.auth.security;

import java.io.IOException;

import com.suwontrip.app.auth.exception.AuthErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 보호된 API에서 인증 여부를 CSRF 검사보다 먼저 확인
 * 세션이 만료된 변경 요청도 CSRF 오류보다 로그인 필요 오류를 먼저 받게 한다.
 * AuthSecurityConfig가 필터 체인에 직접 넣으므로 서블릿 필터로 중복 등록하지 않는다.
 */
final class ApiAuthenticationFilter extends OncePerRequestFilter {
    private final AuthErrorWriter errors;

    ApiAuthenticationFilter(AuthErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // 실제 서블릿 경로를 사용한다. 테스트 요청처럼 비어 있으면 URI에서 context path를 뺀다.
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }

        // 세션 조회와 로그인만 인증 없이 통과시킨다. 로그인 POST의 CSRF 검사는 뒤 필터가 수행한다.
        boolean publicSessionRequest = path.equals("/api/v1/auth/session")
                && (request.getMethod().equals("GET") || request.getMethod().equals("POST"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Spring의 익명 Authentication도 isAuthenticated()가 true일 수 있어 타입을 따로 제외한다.
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        // 보호된 /api/v1/ 요청만 여기서 401로 끝낸다. 나머지 경로의 권한 규칙은 보안 설정이 처리한다.
        if (path.startsWith("/api/v1/") && !publicSessionRequest && !authenticated) {
            errors.write(response, AuthErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }
        // 인증된 요청과 공개 요청은 다음 보안 필터로 넘겨 CSRF 등 후속 검사를 계속한다.
        chain.doFilter(request, response);
    }
}
