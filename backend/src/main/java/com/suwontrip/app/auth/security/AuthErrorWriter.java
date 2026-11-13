package com.suwontrip.app.auth.security;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwontrip.app.auth.dto.AuthErrorResponse;
import com.suwontrip.app.auth.exception.AuthErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Spring Security 필터에서 발생한 인증·인가 오류를 HTTP 응답으로 직접 기록
 * 필터는 Controller보다 먼저 실행되므로 ControllerAdvice에 의존하지 않고 같은 오류 DTO를 사용
 */
@Component
public class AuthErrorWriter {
    private final ObjectMapper objectMapper;

    public AuthErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, AuthErrorCode code) throws IOException {

        // 오류 코드에 정의된 HTTP 상태와 JSON 콘텐츠 타입을 응답에 설정한다.
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // 한글 오류 메시지가 클라이언트에서 깨지지 않도록 출력 인코딩을 명시한다.
        response.setCharacterEncoding("UTF-8");

        // 인증 상태에 관한 오류 응답이 브라우저나 중간 캐시에 남지 않게 한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        // Controller 쪽 예외 처리와 동일한 AuthErrorResponse 형식으로 본문을 직렬화한다.
        // 출력 실패는 숨기지 않고 IOException으로 호출자에게 전파한다.
        objectMapper.writeValue(response.getOutputStream(), AuthErrorResponse.from(code));
    }
}
