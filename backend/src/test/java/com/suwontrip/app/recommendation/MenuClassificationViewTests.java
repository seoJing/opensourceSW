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

class MenuClassificationViewTests {
    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
            .generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final JdbcTemplate jdbc = new JdbcTemplate(db);

    @BeforeEach
    void createView() {
        jdbc.execute("CREATE TABLE catalog_active (singleton_id INT PRIMARY KEY, data_version VARCHAR)");
        jdbc.execute("""
                CREATE TABLE catalog_menu (
                    data_version VARCHAR, menu_id VARCHAR, name VARCHAR, description VARCHAR,
                    PRIMARY KEY (data_version, menu_id)
                )
                """);
        jdbc.update("INSERT INTO catalog_active VALUES (1, 'v1')");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/R__catalog_menu_classification_v1.sql")).execute(db);
    }

    @AfterEach
    void close() {
        db.shutdown();
    }

    @Test
    void distinguishesBeveragesDessertsMealsAndUnknownNames() {
        menu("v1", "1", "카페 라떼", null);
        menu("v1", "2", "레몬에이드", null);
        menu("v1", "3", "라떼", null);
        menu("v1", "4", "티라미수", "에스프레소가 들어간 디저트");
        menu("v1", "5", "치아바타 샌드위치", null);
        menu("v1", "6", "아메리카노 + 케이크 세트", null);
        assertThat(rows()).containsExactly(
                new Row("v1", "1", "beverage", true, "카페라떼"),
                new Row("v1", "2", "beverage", false, "레몬에이드"),
                new Row("v1", "3", "beverage", null, "라떼"),
                new Row("v1", "4", "dessert", null, "티라미수"),
                new Row("v1", "5", "meal", null, "치아바타샌드위치"),
                new Row("v1", "6", null, null, null));
    }

    @Test
    void leavesTheCoffeeFlagUnknownForMixedOrOptionalIngredients() {
        menu("v1", "1", "레몬에이드", "에스프레소 샷 추가 가능");
        menu("v1", "2", "레몬에이드", "Optional ESPRESSO shot");
        menu("v1", "3", "복숭아 아이스티", "샷추가");
        menu("v1", "4", "레몬에이드", "직접 담근 레몬청 사용");
        assertThat(rows()).extracting(Row::coffeeBased).containsExactly(null, null, null, false);
    }

    @Test
    void usesOnlyTheActiveVersionAndPreservesDistinctIds() {
        menu("v1", "1", "아메리카노", null);
        menu("v1", "2", "아메리카노", null);
        menu("v2", "1", "브라우니", null);
        assertThat(rows()).extracting(Row::id).containsExactly("1", "2");
        jdbc.update("UPDATE catalog_active SET data_version = 'v2'");
        assertThat(rows()).containsExactly(new Row("v2", "1", "dessert", null, "브라우니"));
        jdbc.update("UPDATE catalog_active SET data_version = NULL");
        assertThat(rows()).isEmpty();
    }

    private void menu(String version, String id, String name, String description) {
        jdbc.update("INSERT INTO catalog_menu VALUES (?, ?, ?, ?)", version, id, name, description);
    }

    private List<Row> rows() {
        return jdbc.query("""
                SELECT data_version, menu_id, menu_type, coffee_based, matched_name
                FROM catalog_menu_classification_v1 ORDER BY menu_id
                """, (rs, index) -> new Row(rs.getString("data_version"), rs.getString("menu_id"),
                rs.getString("menu_type"), rs.getObject("coffee_based", Boolean.class), rs.getString("matched_name")));
    }

    private record Row(String version, String id, String type, Boolean coffeeBased, String matchedName) {}
}
