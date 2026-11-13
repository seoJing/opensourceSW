package com.suwontrip.app.auth.exception;

/**
 * 인증 서비스에서 예상 가능한 실패를 Controller 예외 처리기로 전달한다.
 */
public class AuthException extends RuntimeException {

    // RuntimeException이 Serializable이므로 직렬화 형식의 버전을 명시
    private static final long serialVersionUID = 1L;
    private final AuthErrorCode code;

    // 초 단위 재시도 시간이다. 로그인 횟수 제한이 아닐 때는 null
    private final Long retryAfter;

    // 재시도 시간이 없는 일반적인 인증 오류에 사용
    public AuthException(AuthErrorCode code) {
        this(code, null);
    }

    // 횟수 제한처럼 Retry-After 헤더가 필요한 오류에 사용
    public AuthException(AuthErrorCode code, Long retryAfter) {
        super(code.message());
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public AuthErrorCode code() {
        return code;
    }

    public Long retryAfter() {
        return retryAfter;
    }
}
