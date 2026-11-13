package com.suwontrip.app.auth.controller;

import com.suwontrip.app.auth.dto.AuthErrorResponse;
import com.suwontrip.app.auth.exception.AuthErrorCode;
import com.suwontrip.app.auth.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 세션 Controller에서 발생한 오류를 공통 JSON 응답으로 바꾼다.
 * 적용 대상을 AuthSessionController로 제한해 다른 API의 오류 계약에 영향을 주지 않는다.
 *
 * Security 필터에서 먼저 발생한 오류는 AuthErrorWriter가 별도로 처리
 */
@RestControllerAdvice(assignableTypes = AuthSessionController.class)
public class AuthExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    // 서비스가 정한 오류 코드의 HTTP 상태를 사용하고 응답 캐싱을 막는다.
    // 로그인 횟수 제한에서 전달한 초 단위 시간만 Retry-After 헤더에 넣는다.
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthErrorResponse> authenticationFailure(AuthException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.code().status())
                .cacheControl(CacheControl.noStore());
        if (exception.retryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, exception.retryAfter().toString());
        }
        return response.body(AuthErrorResponse.from(exception.code()));
    }

    // 존재하지 않는 계정과 잘못된 비밀번호를 구분하지 않고 같은 401로 응답한다.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> invalidCredentials() {
        return response(AuthErrorCode.INVALID_CREDENTIALS);
    }

    // JSON 파싱·타입 변환 실패와 Bean Validation 위반을 같은 400으로 응답한다.
    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentNotValidException.class })
    public ResponseEntity<AuthErrorResponse> invalidRequest() {
        return response(AuthErrorCode.INVALID_REQUEST);
    }

    // 프레임워크가 지정한 상태는 기존 처리기에 맡기고, 예상 밖 실패만 원본 명세의 500으로 변환한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthErrorResponse> internalFailure(Exception exception) throws Exception {
        if (exception instanceof ErrorResponse) {
            throw exception;
        }
        log.error("Unexpected session API failure", exception);
        return response(AuthErrorCode.INTERNAL_ERROR);
    }

    // 재시도 시간이 없는 오류에 동일한 상태·본문·no-store 정책을 적용한다.
    private static ResponseEntity<AuthErrorResponse> response(AuthErrorCode code) {
        return ResponseEntity.status(code.status()).cacheControl(CacheControl.noStore())
                .body(AuthErrorResponse.from(code));
    }
}
