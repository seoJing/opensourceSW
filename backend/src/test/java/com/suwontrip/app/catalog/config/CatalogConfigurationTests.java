package com.suwontrip.app.catalog.config;

import com.suwontrip.app.SuwonTripApplication;
import com.suwontrip.app.catalog.Catalog;
import com.suwontrip.app.catalog.CatalogLoadException;
import com.suwontrip.app.catalog.persistence.CatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.suwontrip.app.catalog.persistence.CatalogDatabaseFixture.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogConfigurationTests {
    private final String databaseUrl = "jdbc:h2:mem:startup_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @TempDir
    Path directory;

    @AfterEach
    void closeDatabase() throws Exception {
        try (var connection = new DriverManagerDataSource(databaseUrl, "sa", "").getConnection();
             var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void startsWithAnEmptyDatabaseWhenActivationIsOmitted() {
        try (var context = start()) {
            assertThat(context.getBeansOfType(Catalog.class)).isEmpty();
            assertThat(context.getBean(CatalogRepository.class).activeDataset()).isEmpty();
        }
    }

    @Test
    void ignoresTheCatalogPathWhenDisabled() {
        try (var context = start("--app.catalog.enabled=false", "--app.catalog.path=" + directory.resolve("missing.json"))) {
            assertThat(context.getBean(CatalogRepository.class).activeDataset()).isEmpty();
        }
    }

    @Test
    void importsDuringStartupAndRestartsWithoutTheSourceFile() throws Exception {
        Path file = Files.write(directory.resolve("메뉴 catalog.json"), document("startup-v1"));
        try (var context = start("--app.catalog.enabled=true", "--app.catalog.path=" + file)) {
            assertThat(context.getBeansOfType(Catalog.class)).isEmpty();
            assertThat(context.getBean(CatalogRepository.class).activeDataset().orElseThrow().menus()).isEqualTo(3);
        }
        Files.delete(file);
        try (var context = start("--app.catalog.enabled=false")) {
            var repository = context.getBean(CatalogRepository.class);
            assertThat(repository.activeDataset().orElseThrow().dataVersion()).isEqualTo("startup-v1");
            assertThat(repository.findMenu("menu-1").orElseThrow().name()).isEqualTo("라테");
        }
    }

    @Test
    void rejectsAMissingPathDuringStartup() {
        assertThatThrownBy(() -> start("--app.catalog.enabled=true"))
                .hasStackTraceContaining("app.catalog.path is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankPathDuringStartup(String path) {
        assertThatThrownBy(() -> start("--app.catalog.enabled=true", "--app.catalog.path=" + path))
                .hasStackTraceContaining("app.catalog.path is required");
    }

    @Test
    void rejectsAMissingFileDuringStartup() {
        assertThatThrownBy(() -> start("--app.catalog.enabled=true", "--app.catalog.path=" + directory.resolve("missing.json")))
                .hasStackTraceContaining("cannot read catalog file");
    }

    @Test
    void rejectsADirectoryInsteadOfAFile() {
        assertThatThrownBy(() -> start("--app.catalog.enabled=true", "--app.catalog.path=" + directory))
                .hasStackTraceContaining("cannot read catalog file");
    }

    @Test
    void rejectsAnInvalidFilesystemPath() {
        assertThatThrownBy(() -> start("--app.catalog.enabled=true", "--app.catalog.path=invalid\u0000path"))
                .hasStackTraceContaining("app.catalog.path is not a valid filesystem path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}"})
    void rejectsInvalidCatalogDocumentsDuringStartup(String json) throws Exception {
        Path file = Files.writeString(directory.resolve("invalid.json"), json);
        assertThatThrownBy(() -> start("--app.catalog.enabled=true", "--app.catalog.path=" + file))
                .hasStackTraceContaining(CatalogLoadException.class.getName());
    }

    @Test
    void rejectsCatalogReferenceErrorsDuringStartup() throws Exception {
        String json = new String(document("startup-v1"), StandardCharsets.UTF_8)
                .replace("\"cafeId\": \"cafe-1\"", "\"cafeId\": \"missing-cafe\"");
        Path file = Files.writeString(directory.resolve("invalid-reference.json"), json);
        assertThatThrownBy(() -> start("--app.catalog.enabled=true", "--app.catalog.path=" + file))
                .hasStackTraceContaining("$.menus[0].cafeId");
    }

    @Test
    void validatesAtStartupEvenWhenBeansAreGloballyLazy() throws Exception {
        Path file = Files.writeString(directory.resolve("invalid-lazy.json"), "{");
        assertThatThrownBy(() -> start(
                "--spring.main.lazy-initialization=true", "--app.catalog.enabled=true", "--app.catalog.path=" + file
        )).hasStackTraceContaining(CatalogLoadException.class.getName());
    }

    private ConfigurableApplicationContext start(String... arguments) {
        SpringApplication application = new SpringApplication(SuwonTripApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        List<String> options = new ArrayList<>(List.of(
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.main.banner-mode=off", "--logging.level.root=OFF"
        ));
        options.addAll(List.of(arguments));
        return application.run(options.toArray(String[]::new));
    }
}
