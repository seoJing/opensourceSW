package com.suwontrip.app.auth.security;

import com.suwontrip.app.auth.config.AuthProperties;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 사전에 설정한 시연 계정 하나를 Spring Security의 UserDetailsService로 제공
 */
@Service
public class DemoUserDetailsService implements UserDetailsService {
    private final AuthProperties properties;

    public DemoUserDetailsService(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        AuthProperties.Demo demo = properties.demo();

        // 계정이 비활성화되었거나 ID가 정확히 일치하지 않으면 같은 조회 실패로 처리
        if (!demo.configured() || !demo.loginId().equals(loginId)) {
            throw new UsernameNotFoundException("계정 정보를 확인해 주세요.");
        }

        // 요청마다 새 객체를 만들어 인증 과정에서 자격 증명이 지워져도 설정값은 유지
        return new DemoUserDetails(demo);
    }
}
