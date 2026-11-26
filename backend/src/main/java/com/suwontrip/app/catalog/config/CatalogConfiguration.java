package com.suwontrip.app.catalog.config;

import com.suwontrip.app.catalog.persistence.CatalogImporter;
import com.suwontrip.app.catalog.persistence.CatalogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CatalogConfiguration.class);

    @Bean
    @DependsOnDatabaseInitialization
    CatalogRepository catalogRepository(JdbcTemplate jdbc) {
        return new CatalogRepository(jdbc);
    }

    @Bean
    @DependsOnDatabaseInitialization
    CatalogImporter catalogImporter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return new CatalogImporter(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.catalog", name = "enabled", havingValue = "true")
    ApplicationRunner catalogImport(CatalogProperties properties, CatalogImporter importer) {
        return arguments -> {
            if (properties.path() == null || properties.path().isBlank()) {
                throw new IllegalStateException(
                        "app.catalog.path is required when app.catalog.enabled=true"
                );
            }
            Path path;
            try {
                path = Path.of(properties.path());
            } catch (InvalidPathException exception) {
                throw new IllegalStateException("app.catalog.path is not a valid filesystem path", exception);
            }
            CatalogImporter.ImportResult result = importer.importFile(path);
            log.info("Catalog stored in database: cafes={}, menus={}, inserted={}",
                    result.cafes(), result.menus(), result.inserted());
        };
    }
}
