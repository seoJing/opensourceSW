package com.suwontrip.app.preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class PreferenceRepositoryPostgresIT {
    @Test
    void replacesTheAccountsAnswersAndReturnsTheOriginalVersionOnRead() throws Exception {
        var url = System.getProperty("catalog.test.jdbc-url");
        var user = System.getProperty("catalog.test.username", "catalog_test");
        var password = System.getProperty("catalog.test.password", "catalog_test");
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                var jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
                var schema = "preferences_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.sql("CREATE SCHEMA " + schema).update();
                jdbc.sql("SET LOCAL search_path TO " + schema).update();
                var migration = new ClassPathResource("db/migration/V2__create_user_preferences.sql");
                jdbc.sql(migration.getContentAsString(StandardCharsets.UTF_8)).update();
                var repository = new PreferenceRepository(jdbc, new ObjectMapper());
                assertThat(repository.find("first")).isEqualTo(PreferenceAnswers.Saved.empty());

                var answers = List.of(new PreferenceAnswers.Answer("Q1", List.of("A")));
                var first = repository.replace("first", "v1", answers);
                assertThat(repository.find("first")).isEqualTo(first);
                assertThat(first.updatedAt()).isNotNull();
                assertThat(first.questionnaireVersion()).isEqualTo("v1");
                assertThat(repository.find("second")).isEqualTo(PreferenceAnswers.Saved.empty());

                var changed = List.of(new PreferenceAnswers.Answer("Q1", List.of()));
                var second = repository.replace("first", "v2", changed);
                assertThat(repository.find("first")).isEqualTo(second);
                assertThat(second.answers()).isEqualTo(changed);
                assertThat(second.questionnaireVersion()).isEqualTo("v2");
            } finally {
                connection.rollback();
            }
        }
    }
}
