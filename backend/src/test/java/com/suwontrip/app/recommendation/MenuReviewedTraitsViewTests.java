package com.suwontrip.app.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MenuReviewedTraitsViewTests {
    @Test
    void carriesReviewedFactsAcrossReleasesOnlyWhileTheSourceTextAndOwnerMatch() {
        var db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(db);
            jdbc.execute("CREATE TABLE catalog_active (singleton_id INT, data_version VARCHAR)");
            jdbc.execute("""
                    CREATE TABLE catalog_menu (
                        data_version VARCHAR, menu_id VARCHAR, cafe_id VARCHAR, name VARCHAR, description VARCHAR
                    )
                    """);
            jdbc.update("INSERT INTO catalog_active VALUES (1, 'next-version')");
            jdbc.update("""
                    INSERT INTO catalog_menu VALUES
                        ('next-version', 'menu-edc7ba18dff7927ba4e0', 'cafe-naver-1400430452', '시그니처 추러스', NULL),
                        ('next-version', 'menu-05d2d92d9ec90b33ce0d', 'cafe-naver-2036785843', '피칸 파이',
                         '오직 피칸만으로 채운 고소한 파이, 단맛이 적고 계피를 넣어 느끼하지 않은'),
                        ('inactive', 'other', 'another-cafe', '다른 메뉴', NULL)
                    """);
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/R__catalog_menu_reviewed_traits_v1.sql")).execute(db);

            String query = "SELECT * FROM catalog_menu_reviewed_traits_v1 ORDER BY menu_id";
            assertThat(jdbc.queryForList(query))
                    .extracting(row -> row.get("SWEETNESS"), row -> row.get("IS_SIGNATURE"),
                            row -> row.get("SWEETNESS_EVIDENCE"), row -> row.get("SIGNATURE_EVIDENCE"))
                    .containsExactly(tuple("low", null, "단맛이 적고", null),
                            tuple(null, true, null, "시그니처 추러스"));

            jdbc.update("UPDATE catalog_menu SET description = CONCAT(description, ' (이전 레시피)') WHERE name = '피칸 파이'");
            jdbc.update("UPDATE catalog_menu SET cafe_id = 'another-cafe' WHERE name = '시그니처 추러스'");
            assertThat(jdbc.queryForList(query))
                    .extracting(row -> row.get("SWEETNESS"), row -> row.get("IS_SIGNATURE"),
                            row -> row.get("SWEETNESS_EVIDENCE"), row -> row.get("SIGNATURE_EVIDENCE"))
                    .containsExactly(tuple(null, null, null, null), tuple(null, null, null, null));
        } finally {
            db.shutdown();
        }
    }
}
