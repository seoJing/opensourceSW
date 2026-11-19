package com.suwontrip.app.catalog.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class CatalogPostgresIT extends CatalogPersistenceContract {
    private JdbcTemplate admin;
    private String schema;

    @Override
    protected DataSource createDatabase() {
        String url = System.getProperty("catalog.test.jdbc-url");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("-Ppostgres requires -Dcatalog.test.jdbc-url=jdbc:postgresql://...");
        }
        String username = System.getProperty("catalog.test.username", "catalog_test");
        String password = System.getProperty("catalog.test.password", "catalog_test");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        schema = "catalog_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        String separator = url.contains("?") ? "&" : "?";
        DataSource dataSource = new DriverManagerDataSource(url + separator + "currentSchema=" + schema, username, password);
        Flyway.configure().dataSource(dataSource).load().migrate();
        return dataSource;
    }

    @AfterEach
    void dropOnlyThisTestsSchema() {
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
