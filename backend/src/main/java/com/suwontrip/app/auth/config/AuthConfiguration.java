package com.suwontrip.app.auth.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    // 로그인 제한에는 UTC 시계를 주입하고 단위 테스트에서는 제어 가능한 시계를 전달
    // 사실 나 얘 잘 모르겠음~
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}
