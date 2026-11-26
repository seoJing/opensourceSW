package com.suwontrip.app.recommendation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPriceViewTests {
    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
            .generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final JdbcTemplate jdbc = new JdbcTemplate(db);

    @BeforeEach
    void createTables() {
        jdbc.execute("CREATE TABLE catalog_active (singleton_id INT PRIMARY KEY, data_version VARCHAR)");
        jdbc.execute("""
                CREATE TABLE catalog_menu (
                    data_version VARCHAR, menu_id VARCHAR, price_krw BIGINT,
                    PRIMARY KEY (data_version, menu_id)
                )
                """);
        jdbc.update("INSERT INTO catalog_active VALUES (1, NULL)");
    }

    @AfterEach
    void closeDatabase() {
        db.shutdown();
    }

    @Test
    void classifiesExistingPricesAtTheSurveyBoundaries() {
        jdbc.update("UPDATE catalog_active SET data_version = 'v1'");
        jdbc.update("""
                INSERT INTO catalog_menu VALUES
                    ('v1', 'a', 0), ('v1', 'b', 4999), ('v1', 'c', 5000),
                    ('v1', 'd', 7999), ('v1', 'e', 8000), ('v1', 'f', NULL)
                """);

        installView();
        assertThat(jdbc.queryForList("""
                SELECT price_level FROM catalog_menu_price_v1 ORDER BY menu_id
                """, String.class)).containsExactly("low", "low", "medium", "medium", "high", null);
    }

    @Test
    void followsTheActiveSnapshotWithoutMixingReusedMenuIds() {
        installView();
        jdbc.update("INSERT INTO catalog_menu VALUES ('v1', 'coffee', 4500), ('v2', 'coffee', 5500)");
        assertThat(activeRows()).isEmpty();

        jdbc.update("UPDATE catalog_active SET data_version = 'v1'");
        assertThat(activeRows()).containsExactly(new Row("v1", "coffee", 4500L, "low"));

        jdbc.update("UPDATE catalog_active SET data_version = 'v2'");
        installView();
        assertThat(activeRows()).containsExactly(new Row("v2", "coffee", 5500L, "medium"));
    }

    private void installView() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/R__catalog_menu_price_v1.sql"))
                .execute(db);
    }

    private List<Row> activeRows() {
        return jdbc.query("SELECT data_version, menu_id, price_krw, price_level FROM catalog_menu_price_v1",
                (r, i) -> new Row(r.getString("data_version"), r.getString("menu_id"),
                        r.getLong("price_krw"), r.getString("price_level")));
    }

    private record Row(String version, String menuId, long priceKrw, String level) {}
}
