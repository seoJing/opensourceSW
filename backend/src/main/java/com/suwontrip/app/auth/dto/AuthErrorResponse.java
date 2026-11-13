package com.suwontrip.app.auth.dto;

import java.util.List;

import com.suwontrip.app.auth.exception.AuthErrorCode;

/**
 * 인증 API의 공통 오류 본문
 *
 * @param code 클라이언트가 분기 기준으로 사용할 오류 코드다. {@link AuthErrorCode}의 이름을 사용한
 * @param message
 * @param errors
 */
public record AuthErrorResponse(String code, String message, List<?> errors) {

    // 상태 코드와 공개 메시지는 AuthErrorCode 한곳에서 관리해 두 응답 경로의 내용을 맞춘다.
    public static AuthErrorResponse from(AuthErrorCode code) {
        return new AuthErrorResponse(code.name(), code.message(), List.of());
    }
}
