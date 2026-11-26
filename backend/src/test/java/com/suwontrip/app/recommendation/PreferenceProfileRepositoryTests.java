package com.suwontrip.app.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static com.suwontrip.app.recommendation.PreferenceProfile.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenceProfileRepositoryTests {
    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
            .generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final JdbcClient jdbc = JdbcClient.create(db);
    private final ObjectMapper json = new ObjectMapper();
    private final PreferenceProfileRepository profiles = new PreferenceProfileRepository(jdbc, json);

    @BeforeEach
    void prepare() {
        jdbc.sql("""
                CREATE TABLE user_preferences (
                    user_id VARCHAR PRIMARY KEY, questionnaire_version VARCHAR NOT NULL, answers_json TEXT NOT NULL
                )
                """).update();
    }

    @AfterEach
    void close() {
        db.shutdown();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "AAAAAA, BEVERAGE, LOW, LOW, true, HOT, true",
            "ABBBBB, BEVERAGE, MEDIUM, MEDIUM, false, COLD, false",
            "ACCCCC, BEVERAGE, HIGH, HIGH, -, -, -",
            "ABCABC, BEVERAGE, MEDIUM, HIGH, true, COLD, -",
            "BDD.CC, DESSERT, -, -, -, -, -",
            "CBABAB, MEAL, MEDIUM, LOW, -, HOT, false",
            "DDD.CC, -, -, -, -, -, -",
            "......, -, -, -, -, -, -"
    }, nullValues = "-")
    void mapsByQuestionIdInAnyOrder(String selections, MenuType menuType, Level priceLevel, Level sweetness,
                                    Boolean coffeeBased, Temperature temperature, Boolean signature) {
        store("demo", "v0.1.0", answers(selections).toString());
        assertThat(profiles.findForUser("demo"))
                .contains(new PreferenceProfile(menuType, priceLevel, sweetness, coffeeBased, temperature, signature));
    }

    @Test
    void readsOnlySupportedVersionAndCurrentAccount() {
        store("old", "v0.0.9", "unread legacy payload");
        store("other", "v0.1.0", answers("AAAAAA").toString());
        assertThat(profiles.findForUser("old")).isEmpty();
        assertThat(profiles.findForUser("missing")).isEmpty();
        assertThat(profiles.findForUser("other", "v0.2.0")).isEmpty();
        assertThat(profiles.findForUser("other", "v0.1.0")).isPresent();
        jdbc.sql("UPDATE user_preferences SET answers_json = ? WHERE user_id = ?")
                .params(answers("BBB.BB").toString(), "other").update();
        assertThat(profiles.findForUser("other")).contains(new PreferenceProfile(
                MenuType.DESSERT, Level.MEDIUM, Level.MEDIUM, null, Temperature.COLD, false));
    }

    @Test
    void rejectsCorruptOrAmbiguousCurrentAnswers() {
        var duplicate = answers("......");
        duplicate.set(0, duplicate.get(1));
        var multiple = answers("AAAAAA");
        ((ArrayNode) multiple.get(0).get("optionIds")).add("B");
        var wrongType = answers("AAAAAA");
        ((ArrayNode) wrongType.get(0).get("optionIds")).removeAll().add(false);
        var missing = answers("AAAAAA");
        missing.remove(2);
        for (String invalid : new String[]{
                duplicate.toString(), multiple.toString(), wrongType.toString(),
                missing.toString(), answers("AAAAAD").toString(), "{}", "{"
        }) {
            jdbc.sql("DELETE FROM user_preferences").update();
            store("demo", "v0.1.0", invalid);
            assertThatThrownBy(() -> profiles.findForUser("demo")).isInstanceOf(IllegalStateException.class);
        }
    }

    private ArrayNode answers(String choices) {
        var rows = json.createArrayNode();
        for (int i = choices.length() - 1; i >= 0; i--) {
            var options = rows.addObject().put("questionId", "Q" + (i + 1)).putArray("optionIds");
            if (choices.charAt(i) != '.') {
                options.add(String.valueOf(choices.charAt(i)));
            }
        }
        return rows;
    }

    private void store(String id, String version, String answers) {
        jdbc.sql("INSERT INTO user_preferences VALUES (?, ?, ?)").params(id, version, answers).update();
    }
}
