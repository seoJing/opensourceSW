package com.suwontrip.app.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.suwontrip.app.recommendation.PreferenceProfile.*;

@Repository
public class PreferenceProfileRepository {
    private static final String VERSION = "v0.1.0";
    private static final Set<String> QUESTIONS = Set.of("Q1", "Q2", "Q3", "Q4", "Q5", "Q6");
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public PreferenceProfileRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<PreferenceProfile> findForUser(String userId, String activeQuestionnaireVersion) {
        return VERSION.equals(activeQuestionnaireVersion) ? findForUser(userId) : Optional.empty();
    }

    public Optional<PreferenceProfile> findForUser(String userId) {
        return jdbc.sql("""
                        SELECT answers_json FROM user_preferences
                        WHERE user_id = :userId AND questionnaire_version = :version
                        """)
                .param("userId", userId)
                .param("version", VERSION)
                .query((row, index) -> decode(row.getString("answers_json")))
                .optional();
    }

    private PreferenceProfile decode(String stored) {
        JsonNode rows;
        try {
            rows = json.readTree(stored);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored questionnaire JSON", e);
        }
        if (rows == null || !rows.isArray() || rows.size() != QUESTIONS.size()) {
            throw invalidAnswers();
        }
        Map<String, String> selected = new HashMap<>();
        for (JsonNode row : rows) {
            String id = row.path("questionId").textValue();
            JsonNode options = row.path("optionIds");
            if (id == null || !QUESTIONS.contains(id) || selected.containsKey(id)
                    || !options.isArray() || options.size() > 1) {
                throw invalidAnswers();
            }
            if (!options.isEmpty() && !options.get(0).isTextual()) {
                throw invalidAnswers();
            }
            selected.put(id, options.isEmpty() ? null : options.get(0).textValue());
        }

        MenuType type = choice(selected.get("Q1"), "D", MENU_TYPES);
        Boolean coffee = choice(selected.get("Q4"), "C", BOOLEAN_CHOICES);
        return new PreferenceProfile(
                type,
                choice(selected.get("Q2"), "D", LEVELS),
                choice(selected.get("Q3"), "D", LEVELS),
                type == MenuType.BEVERAGE ? coffee : null,
                choice(selected.get("Q5"), "C", TEMPERATURES),
                choice(selected.get("Q6"), "C", BOOLEAN_CHOICES)
        );
    }

    private static final Map<String, MenuType> MENU_TYPES = Map.of(
            "A", MenuType.BEVERAGE, "B", MenuType.DESSERT, "C", MenuType.MEAL);
    private static final Map<String, Level> LEVELS = Map.of("A", Level.LOW, "B", Level.MEDIUM, "C", Level.HIGH);
    private static final Map<String, Temperature> TEMPERATURES = Map.of("A", Temperature.HOT, "B", Temperature.COLD);
    private static final Map<String, Boolean> BOOLEAN_CHOICES = Map.of("A", true, "B", false);

    private static <T> T choice(String selected, String indifferent, Map<String, T> values) {
        if (selected == null || indifferent.equals(selected)) {
            return null;
        }
        T value = values.get(selected);
        if (value == null) {
            throw invalidAnswers();
        }
        return value;
    }

    private static IllegalStateException invalidAnswers() {
        return new IllegalStateException("Invalid stored answers for questionnaire " + VERSION);
    }
}
