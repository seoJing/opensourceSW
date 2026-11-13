package com.suwontrip.app.auth.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml의 app.auth 아래 값을 같은 이름의 record 필드에 바인딩함.
// 이 객체는 설정을 읽을 때 생성되므로 생성자 검증에 실패하면 서버가 시작하지 않는다네~
@ConfigurationProperties("app.auth")
public record AuthProperties(
        Demo demo,
        int maxLoginAttempts,
        Duration loginWindow,
        int maxTrackedClients) {

    // 로그인 제한 정책에 필요한 값이 없거나 허용 범위를 벗어나면 즉시 거부
    // 횟수와 클라이언트 수는 양수이며, 제한 구간은 1초 이상 1시간 이하로 강제
    public AuthProperties {
        if (maxLoginAttempts < 1 || maxTrackedClients < 1 || loginWindow == null
                || loginWindow.isNegative() || loginWindow.compareTo(Duration.ofSeconds(1)) < 0
                || loginWindow.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("로그인 제한 횟수·저장 한도는 양수, 시간은 1초~1시간이어야 한다.");
        }

        // 시연 계정이 비활성화되어도 Demo 객체는 필요.
        // 네 필드가 비어 있는 객체가 비활성 상태다.
        if (demo == null) {
            throw new IllegalArgumentException("app.auth.demo 설정이 필요하다.");
        }
    }

    // 회원가입이나 계정 저장소 없이 환경변수로 주입하는 (그쪽이 원하셨던~) 단일 시연 계정의 설정값
    public record Demo(String userId, String displayName, String loginId, String passwordHash) {

        public Demo {

            // null과 빈 문자열을 동일한 미설정 값으로 취급해 네 필드의 상태를 함께 검사한다.
            List<String> values = List.of(userId == null ? "" : userId,
                    displayName == null ? "" : displayName, loginId == null ? "" : loginId,
                    passwordHash == null ? "" : passwordHash);
            boolean supplied = values.stream().anyMatch(value -> !value.isEmpty());

            // 전부 비었으면 로그인 기능만 비활성화한다. 하나라도 채웠다면 나머지도 모두 필요.
            // loginId 길이는 요청 DTO의 128자 제한과 맞추고, 해시는 BCrypt 형식과 cost 4~31만 검사
            // 이 검사는 해시 문자열의 형식 확인이며 실제 비밀번호 일치는 로그인 과정에서 검증
            if (supplied && (values.stream().anyMatch(String::isBlank) || loginId.length() > 128
                    || !passwordHash.matches("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}"))) {
                throw new IllegalArgumentException("시연 계정 네 항목과 올바른 BCrypt 해시를 함께 설정해야 한다.");
            }
        }

        // 설정이 유효한 경우 loginId의 존재 여부만으로 계정 활성 상태를 판별
        public boolean configured() {
            return loginId != null && !loginId.isEmpty();
        }

        @Override
        public String toString() {
            // record의 기본 toString은 모든 필드를 출력하므로 계정 정보와 해시를 숨김
            return "Demo[configured=" + configured() + "]";
        }
    }
}
