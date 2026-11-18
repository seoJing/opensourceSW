package com.suwontrip.app.taste;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.UUID;

class TastePostgresIT extends TastePersistenceContract {
    private JdbcTemplate admin;
    private String schema;

    @Override
    protected DataSource database() {
        String url = System.getProperty("catalog.test.jdbc-url");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("-Ppostgres requires -Dcatalog.test.jdbc-url=jdbc:postgresql://...");
        }
        String user = System.getProperty("catalog.test.username", "catalog_test");
        String password = System.getProperty("catalog.test.password", "catalog_test");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "taste_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        DataSource source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
        Flyway.configure().dataSource(source).load().migrate();
        return source;
    }

    @AfterEach
    void cleanup() {
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
