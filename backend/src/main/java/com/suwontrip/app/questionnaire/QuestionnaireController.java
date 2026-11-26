package com.suwontrip.app.questionnaire;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@ConditionalOnProperty(prefix = "app.questionnaire", name = "enabled", havingValue = "true")
public class QuestionnaireController {
    private final QuestionnaireDefinition definition;

    public QuestionnaireController(QuestionnaireDefinition definition) {
        this.definition = definition;
    }

    @GetMapping("/api/v1/preference-questionnaire")
    public ResponseEntity<?> current(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(new AuthenticationRequired(
                            "AUTHENTICATION_REQUIRED",
                            "로그인이 필요합니다.",
                            List.of()
                    ));
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(definition);
    }

    private record AuthenticationRequired(String code, String message, List<?> errors) {
    }
}
