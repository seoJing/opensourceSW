package com.suwontrip.app.catalog.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class CatalogPersistenceTests extends CatalogPersistenceContract {
    @Override
    protected DataSource createDatabase() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:catalog_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "sa", ""
        );
        Flyway.configure().dataSource(dataSource).load().migrate();
        return dataSource;
    }

    @AfterEach
    void closeDatabase() throws Exception {
        if (jdbc != null) {
            try (var connection = jdbc.getDataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }
}
