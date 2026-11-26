package com.suwontrip.app.preferences;

import com.suwontrip.app.auth.security.SessionSecurity;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/preferences")
@ConditionalOnProperty(prefix = "app.questionnaire", name = "enabled", havingValue = "true")
class PreferenceController {
    private final SessionSecurity sessions;
    private final PreferenceRepository repository;
    private final PreferenceValidator validator;

    PreferenceController(SessionSecurity sessions, PreferenceRepository repository, QuestionnaireDefinition definition) {
        this.sessions = sessions;
        this.repository = repository;
        this.validator = new PreferenceValidator(definition);
    }

    @GetMapping
    ResponseEntity<PreferenceAnswers.Saved> get(HttpServletRequest request) {
        return ok(repository.find(sessions.current(request).user().id()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PreferenceAnswers.Saved> put(@RequestBody PreferenceAnswers input, HttpServletRequest request) {
        var answers = validator.validate(input);
        return ok(repository.replace(sessions.current(request).user().id(), input.questionnaireVersion(), answers));
    }

    private static ResponseEntity<PreferenceAnswers.Saved> ok(PreferenceAnswers.Saved value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }

    @ExceptionHandler(PreferenceProblem.class)
    ResponseEntity<PreferenceProblem.Body> problem(PreferenceProblem error) {
        return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(error.body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PreferenceProblem.Body> malformed() {
        return problem(PreferenceProblem.malformed());
    }
}
