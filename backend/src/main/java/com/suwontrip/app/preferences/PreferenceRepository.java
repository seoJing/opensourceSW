package com.suwontrip.app.preferences;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PreferenceRepository {
    private static final TypeReference<List<PreferenceAnswers.Answer>> ANSWERS = new TypeReference<>() {};
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    PreferenceRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    PreferenceAnswers.Saved find(String userId) {
        return jdbc.sql("""
                SELECT questionnaire_version, answers_json, updated_at
                FROM user_preferences WHERE user_id = ?
                """).param(userId).query(this::read).optional().orElseGet(PreferenceAnswers.Saved::empty);
    }

    PreferenceAnswers.Saved replace(String userId, String version, List<PreferenceAnswers.Answer> answers) {
        try {
            return jdbc.sql("""
                    INSERT INTO user_preferences (user_id, questionnaire_version, answers_json, updated_at)
                    VALUES (?, ?, ?, statement_timestamp())
                    ON CONFLICT (user_id) DO UPDATE SET
                        questionnaire_version = EXCLUDED.questionnaire_version,
                        answers_json = EXCLUDED.answers_json,
                        updated_at = clock_timestamp()
                    RETURNING questionnaire_version, answers_json, updated_at
                    """).param(userId).param(version).param(json.writeValueAsString(answers))
                    .query(this::read).single();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode preference answers", exception);
        }
    }

    private PreferenceAnswers.Saved read(ResultSet row, int index) throws SQLException {
        try {
            return new PreferenceAnswers.Saved(true, row.getString("questionnaire_version"),
                    json.readValue(row.getString("answers_json"), ANSWERS),
                    row.getObject("updated_at", OffsetDateTime.class).toInstant());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot decode stored preference answers", exception);
        }
    }
}
