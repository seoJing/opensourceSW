package com.suwontrip.app.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class MenuTemperatureViewTests {
    @Test
    void readsExplicitServingLabelsForEachActiveMenu() {
        var db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(db);
            jdbc.execute("CREATE TABLE catalog_active (singleton_id INT, data_version VARCHAR)");
            jdbc.execute("CREATE TABLE catalog_menu (data_version VARCHAR, menu_id INT, name VARCHAR, description VARCHAR)");
            jdbc.update("INSERT INTO catalog_active VALUES (1, 'v1')");
            jdbc.update("""
                    INSERT INTO catalog_menu VALUES
                    ('v1', 1, '라떼 (Hot)', NULL), ('v1', 2, '차 (iced only)', NULL),
                    ('v1', 3, '아메리카노 *(ice, hot)', NULL), ('v1', 4, '차', '홍차 (HOT / ICE)'),
                    ('v1', 5, '콜드브루', '차가운 물에 추출'), ('v1', 6, 'Hot dog', 'with ice cream'),
                    ('v1', 7, '라떼 (HOT)', 'ICE ONLY'), ('v1', 8, '라떼 (hot)', '제공 불가'),
                    ('v1', 9, '차', 'HOT or ICE 가격동일'), ('v1', 10, '차 (hot) (ice)', NULL),
                    ('v2', 1, '라떼', 'only ice')
                    """);
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/R__catalog_menu_temperature_v1.sql")).execute(db);
            String query = "SELECT temperature FROM catalog_menu_temperature_v1 ORDER BY menu_id";
            assertThat(jdbc.queryForList(query, String.class)).containsExactly(
                    "hot", "cold", "both", "both", null, null, null, null, "both", null);
            jdbc.update("UPDATE catalog_active SET data_version = 'v2'");
            assertThat(jdbc.queryForList(query, String.class)).containsExactly("cold");
        } finally {
            db.shutdown();
        }
    }
}
