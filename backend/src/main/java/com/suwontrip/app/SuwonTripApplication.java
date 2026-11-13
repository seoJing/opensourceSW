package com.suwontrip.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * 백엔드 애플리케이션의 시작점이다.
 * 이 패키지 아래의 Controller, Service, 보안 설정 등을 컴포넌트 탐색 대상으로 둔다.
 */
@SpringBootApplication
public class SuwonTripApplication {

    public static void main(String[] args) {
        // Spring 설정을 읽고 애플리케이션 컨텍스트와 내장 웹 서버를 시작한다.
        SpringApplication.run(SuwonTripApplication.class, args);
    }
}
