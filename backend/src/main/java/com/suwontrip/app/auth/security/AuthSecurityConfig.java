package com.suwontrip.app.auth.security;

import com.suwontrip.app.auth.exception.AuthErrorCode;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * 시연 계정 인증에 사용할 Spring Security 구성 요소와 HTTP 보안 규칙을 연결한다.
 */
@Configuration
public class AuthSecurityConfig {

    // DemoUserDetails에 저장된 BCrypt 해시와 요청 비밀번호를 비교할 때 사용
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ProviderManager는 이 제공자를 통해 로그인 요청의 Authentication을 검증
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    // 인증된 SecurityContext를 HTTP 세션에 보관해 다음 요청에서 다시 읽는다.
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // CSRF 토큰은 서버 세션에 보관하고 변경 요청의 X-CSRF-TOKEN 헤더에서 받는다.
    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    // 응답에서 읽는 CSRF 토큰을 마스킹해 같은 원본 토큰이 그대로 노출되지 않게 한다.
    @Bean
    CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new XorCsrfTokenRequestAttributeHandler();
    }

    // 공개 경로, 인증 필요 경로, CSRF 검사와 API 오류 응답을 하나의 필터 체인에 묶는다.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository tokens, CsrfTokenRequestHandler tokenHandler,
            AuthErrorWriter errors) throws Exception {

        // 로그인 성공 시 SessionSecurity가 인증 상태를 명시적으로 저장해야 다음 요청에 유지된다.
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))

                // health와 세션 조회·로그인만 공개한다. POST 로그인도 아래 CSRF 검사는 통과해야 한다.
                // ERROR 디스패치는 오류 응답을 만들 수 있도록 허용하고, 나머지는 인증을 요구한다.
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                                "/api/v1/auth/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/session").permitAll()
                        .anyRequest().authenticated())

                // 안전하지 않은 HTTP 메서드는 세션에 저장된 CSRF 토큰으로 검증한다.
                .csrf(csrf -> csrf.csrfTokenRepository(tokens).csrfTokenRequestHandler(tokenHandler))

                // 미인증은 401, CSRF 실패는 403으로 같은 JSON 오류 형식을 반환한다.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errors.write(response,
                                AuthErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, exception) -> errors.write(response,
                                exception instanceof CsrfException ? AuthErrorCode.CSRF_INVALID
                                        : AuthErrorCode.ACCESS_DENIED)))

                // 기본 로그인 화면·Basic 인증·기본 로그아웃 경로·로그인 후 원래 요청 복원을 사용하지 않는다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)

                // 보호된 API에서는 CSRF 검사보다 먼저 미인증 여부를 판단해 401을 우선 반환한다.
                .addFilterBefore(new ApiAuthenticationFilter(errors), CsrfFilter.class);
        return http.build();
    }
}
