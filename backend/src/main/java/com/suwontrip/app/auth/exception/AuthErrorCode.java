package com.suwontrip.app.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증 API에서 공개하는 오류 코드와 HTTP 상태, 사용자 메시지를 한곳에 정의한다.
 */
public enum AuthErrorCode {

    // JSON 형식, 필드 타입 또는 로그인 요청의 입력 제약이 잘못됐다.
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "로그인 요청 형식을 확인해 주세요."),

    // 없는 계정과 틀린 비밀번호를 같은 응답으로 처리해 계정 존재 여부를 드러내지 않는다.
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "로그인 정보를 확인해 주세요."),

    // 보호된 API에 인증 정보가 없거나 세션이 만료됐다.
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),

    // 변경 요청의 CSRF 토큰이 없거나 현재 세션의 값과 일치하지 않는다.
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 확인 값을 다시 받아주세요."),

    // Security 필터가 CSRF 이외의 이유로 접근을 거부했을 때 사용한다.
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근할 수 없어요."),

    // 같은 IP의 로그인 시도가 제한 횟수를 넘었다. 재시도 시간은 Retry-After 헤더로 보낸다.
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 로그인해 주세요."),

    // 시연 계정이 없거나 로그인 제한기의 IP 저장 한도가 가득 찼다.
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그인 서비스를 사용할 수 없어요.");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
