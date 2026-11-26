package com.suwontrip.app.preferences;

import java.util.List;
import org.springframework.http.HttpStatus;

final class PreferenceProblem extends RuntimeException {
    record Detail(String field, String questionId, String reason, String message) {}
    record Body(String code, String message, List<Detail> errors) {}

    final HttpStatus status;
    final Body body;

    private PreferenceProblem(HttpStatus status, String code, String message, List<Detail> errors) {
        super(message);
        this.status = status;
        this.body = new Body(code, message, errors);
    }

    static PreferenceProblem malformed() {
        return new PreferenceProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식을 확인해 주세요.", List.of());
    }

    static PreferenceProblem stale() {
        return new PreferenceProblem(HttpStatus.CONFLICT, "QUESTIONNAIRE_VERSION_MISMATCH",
                "현재 설문 버전을 확인해 주세요.", List.of());
    }

    static PreferenceProblem invalid(String field, String questionId, String reason, String message) {
        return new PreferenceProblem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ANSWERS",
                "설문 응답을 확인해 주세요.", List.of(new Detail(field, questionId, reason, message)));
    }
}
