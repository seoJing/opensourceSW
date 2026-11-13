package com.suwontrip.app.auth.security;

import com.suwontrip.app.auth.dto.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Spring Security의 인증 상태를 HTTP 세션에 저장하고 세션 응답으로 변환
 */
@Component
public class SessionSecurity {
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository tokens;
    private final CsrfTokenRequestHandler tokenHandler;
    private final ChangeSessionIdAuthenticationStrategy sessionIds = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SessionSecurity(
            SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository tokens,
            CsrfTokenRequestHandler tokenHandler) {

        this.contexts = contexts;
        this.tokens = tokens;
        this.tokenHandler = tokenHandler;
    }

    // SecurityContext의 principal이 시연 계정일 때만 공개 사용자 정보를 응답에 넣음
    // CSRF 필터가 요청에 올려둔 토큰은 익명·인증 세션 모두에게 반환한다.
    public SessionResponse current(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SessionResponse.User user = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof DemoUserDetails demo
                        ? new SessionResponse.User(demo.userId(), demo.displayName())
                        : null;
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new SessionResponse(user != null, user, token.getToken());
    }

    // 인증 직후 세션 ID를 교체해 로그인 이전 세션 ID의 재사용을 막는다.
    // 새 SecurityContext를 현재 요청과 세션 저장소에 모두 반영한 뒤 CSRF 토큰을 갱신한다.
    public void establish(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        sessionIds.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 보안 설정이 명시적 저장을 요구하므로 홀더에 넣는 것만으로는 다음 요청에 유지되지 않는다.
        contexts.saveContext(context, request, response);
        replaceToken(request, response);
    }

    // Spring의 로그아웃 처리기로 인증 상태를 지우고 기존 세션을 무효화한다.
    public void invalidate(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());

        replaceToken(request, response);
    }

    // 새 토큰을 세션에 저장하고 요청 속성도 갱신해 같은 요청의 응답에서 새 값을 읽게 한다.
    private void replaceToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = tokens.generateToken(request);
        tokens.saveToken(token, request, response);
        tokenHandler.handle(request, response, () -> token);
    }
}
