package com.suwontrip.app.taste;

import com.suwontrip.app.catalog.persistence.CatalogDatabaseFixture;
import com.suwontrip.app.catalog.persistence.CatalogImporter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TasteMigrationTests {
    @Test
    void v2PreservesAnAlreadyImportedV1Catalog() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        try {
            Flyway.configure().dataSource(source).target("1").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            new CatalogImporter(jdbc, new JdbcTransactionManager(source))
                    .importDocument(CatalogDatabaseFixture.document("existing-v1"));
            var before = jdbc.queryForMap("SELECT * FROM catalog_dataset");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForMap("SELECT * FROM catalog_dataset")).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_menu", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT data_version FROM catalog_active", String.class)).isEqualTo("existing-v1");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_taste_profile", Integer.class)).isZero();
        } finally {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }
}
