package com.suwontrip.app.auth.support;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 시연 계정에 주입할 BCrypt 해시를 로컬 대화형 터미널에서 만드는 독립 실행 도구
 */
public class PasswordHashTool {
    public static void main(String[] args) {

        // Console이 없으면 입력을 숨길 수 없으므로 파이프나 비대화형 실행을 거부
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("입력을 숨길 수 있는 대화형 터미널에서 실행해 주세요.");
        }

        // 입력한 원문이 터미널에 표시되지 않도록 char 배열로 읽는다.
        char[] password = console.readPassword("시연 계정 비밀번호: ");
        if (password == null) {
            throw new IllegalArgumentException("비밀번호 입력이 취소됐어요.");
        }

        try {
            // 인코더가 CharSequence를 받으므로 String으로 변환한다. 이 복사본은 직접 지울 수 없다.
            String value = new String(password);
            // 로그인 요청과 같은 BCrypt 72바이트 한도를 적용하며 UTF-8 바이트 수로 검사한다.
            if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("비밀번호는 UTF-8 기준 1~72바이트여야 해요.");
            }

            // 기본 cost의 BCrypt 해시만 출력한다. 원문이나 계정 정보는 출력하지 않는다.
            console.printf("%s%n", new BCryptPasswordEncoder().encode(value));
        } finally {
            // 정상 종료와 오류 발생 모두에서 입력 char 배열을 덮어쓴다.
            Arrays.fill(password, '\0');
        }
    }
}
