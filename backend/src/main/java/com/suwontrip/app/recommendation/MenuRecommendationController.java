package com.suwontrip.app.recommendation;

import com.suwontrip.app.auth.dto.AuthErrorResponse;
import com.suwontrip.app.auth.security.SessionSecurity;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@RestController
@ConditionalOnProperty(prefix = "app.questionnaire", name = "enabled", havingValue = "true")
class MenuRecommendationController {
    private static final Logger log = LoggerFactory.getLogger(MenuRecommendationController.class);
    private final SessionSecurity sessions;
    private final PreferenceProfileRepository profiles;
    private final Optional<MenuCardLookup> catalog;
    private final QuestionnaireDefinition definition;

    MenuRecommendationController(SessionSecurity sessions, PreferenceProfileRepository profiles,
                                 Optional<MenuCardLookup> catalog, QuestionnaireDefinition definition) {
        this.sessions = sessions;
        this.profiles = profiles;
        this.catalog = catalog;
        this.definition = definition;
    }

    record Request(List<String> excludedMenuIds) {}
    record Response(MenuCardLookup.Card menu) {}

    @PostMapping(path = "/api/v1/menu-recommendations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> recommend(@RequestBody Request input, HttpServletRequest request) {
        var ids = input.excludedMenuIds();
        if (ids == null || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            return malformed();
        }
        var excluded = new HashSet<>(ids);
        if (excluded.size() != ids.size()) {
            return malformed();
        }
        var user = sessions.current(request).user();
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요해요.");
        }
        var profile = profiles.findForUser(user.id(), definition.version());
        if (profile.isEmpty()) {
            return error(HttpStatus.CONFLICT, "PREFERENCES_REQUIRED", "현재 설문에 먼저 응답해 주세요.");
        }
        if (catalog.isEmpty()) {
            return unavailable();
        }
        var menu = catalog.get().next(profile.get(), excluded);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Response(menu.orElse(null)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AuthErrorResponse> malformed() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "추천 요청 형식을 확인해 주세요.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<AuthErrorResponse> databaseFailure(Exception exception) {
        log.error("Recommendation database unavailable", exception);
        return unavailable();
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<AuthErrorResponse> invalidState(IllegalStateException exception) {
        log.error("Invalid recommendation state", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "추천을 처리할 수 없어요.");
    }

    private static ResponseEntity<AuthErrorResponse> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "추천 자료를 사용할 수 없어요.");
    }

    private static ResponseEntity<AuthErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new AuthErrorResponse(code, message, List.of()));
    }
}
