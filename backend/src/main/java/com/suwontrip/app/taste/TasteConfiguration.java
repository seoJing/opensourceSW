package com.suwontrip.app.taste;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class TasteConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    TasteProfileImporter tasteProfileImporter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new TasteProfileImporter(jdbc, manager);
    }

    @Bean
    @DependsOnDatabaseInitialization
    TasteProfileRepository tasteProfileRepository(JdbcTemplate jdbc) {
        return new TasteProfileRepository(jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.taste", name = "import-path")
    ApplicationRunner tasteImport(@Value("${app.taste.import-path}") String path, TasteProfileImporter importer) {
        return arguments -> {
            var result = importer.importFile(Path.of(path));
            LoggerFactory.getLogger(TasteConfiguration.class).info(
                    "Taste profiles stored: version={}, records={}, inserted={}, review=UNREVIEWED",
                    result.profileVersion(), result.records(), result.inserted());
        };
    }
}
