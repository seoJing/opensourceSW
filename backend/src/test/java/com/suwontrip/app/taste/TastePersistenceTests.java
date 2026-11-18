package com.suwontrip.app.taste;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.UUID;

class TastePersistenceTests extends TastePersistenceContract {
    @Override
    protected DataSource database() {
        DataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:taste_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).load().migrate();
        return source;
    }

    @AfterEach
    void closeDatabase() throws Exception {
        if (dataSource != null) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }
}
