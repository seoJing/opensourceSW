package com.suwontrip.app.auth.dto;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 본문을 수신.
 * Validation은 Controller의 {@code @Valid}가 실행
 *
 * @param loginId
 * @param password
 */
public record LoginRequest(
        @NotBlank @Size(max = 128) String loginId,
        @NotEmpty String password) {

    @AssertTrue
    @JsonIgnore
    public boolean isPasswordWithinBcryptLimit() {
        // null은 @NotEmpty가 별도로 처리하며 여기서는 바이트 길이만 검사
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        // record의 기본 toString은 두 자격 증명을 그대로 출력하므로 로그용 문자열을 숨김
        return "LoginRequest[credentials=REDACTED]";
    }
}
