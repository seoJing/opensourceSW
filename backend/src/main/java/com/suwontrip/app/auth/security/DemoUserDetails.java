package com.suwontrip.app.auth.security;

import com.suwontrip.app.auth.config.AuthProperties;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/**
 * Spring Security가 인증에 사용하는 시연 계정의 principal
 * 계정 조회 결과를 그대로 API에 반환하지 않고, 응답에는 userId와 displayName만 복사
 * 이 타입은 auth.security 패키지 안에서만 사용
 */
final class DemoUserDetails extends User {

    // 부모 User가 Serializable이므로 직렬화 형식의 버전을 명시
    private static final long serialVersionUID = 1L;

    // 로그인에 쓰는 loginId와 별개로, 응답에서 사용할 고정 사용자 ID와 표시 이름을 보관
    private final String userId;
    private final String displayName;

    DemoUserDetails(AuthProperties.Demo demo) {
        // 부모 User에는 로그인 ID, 평문이 아닌 BCrypt 해시, 일반 사용자 권한만 전달
        super(demo.loginId(), demo.passwordHash(), AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.userId = demo.userId();
        this.displayName = demo.displayName();
    }

    // SessionSecurity가 응답용 User DTO를 만들 때만 읽는다.
    String userId() {
        return userId;
    }

    String displayName() {
        return displayName;
    }
}
