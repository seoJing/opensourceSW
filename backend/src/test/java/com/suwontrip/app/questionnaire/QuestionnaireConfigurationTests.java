package com.suwontrip.app.questionnaire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionnaireConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(QuestionnaireConfiguration.class);

    @TempDir
    Path directory;

    @Test
    void keepsDefaultStartupIndependentFromUnconfirmedQuestionnaireData() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(QuestionnaireDefinition.class);
        });
    }

    @Test
    void doesNotReadTheFileWhenDisabled() {
        runner.withPropertyValues(
                "app.questionnaire.enabled=false",
                "app.questionnaire.path=" + directory.resolve("missing.json")
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(QuestionnaireDefinition.class);
        });
    }

    @Test
    void loadsOnceAndKeepsTheValidatedVersionUntilRestart() throws Exception {
        Path file = Files.writeString(directory.resolve("설문 definition.json"), QuestionnaireDefinitionTests.JSON);

        enabled(file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(QuestionnaireDefinition.class);
            QuestionnaireDefinition definition = context.getBean(QuestionnaireDefinition.class);
            assertThat(definition.version()).isEqualTo("test-only-v1");

            Files.writeString(file, "{}");

            assertThat(context.getBean(QuestionnaireDefinition.class)).isSameAs(definition);
        });
    }

    @Test
    void readsTheUpdatedDefinitionOnTheNextStartup() throws Exception {
        Path file = Files.writeString(directory.resolve("versioned.json"), QuestionnaireDefinitionTests.JSON);

        enabled(file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(QuestionnaireDefinition.class).version())
                    .isEqualTo("test-only-v1");
        });

        Files.writeString(file, QuestionnaireDefinitionTests.JSON.replace("test-only-v1", "test-only-v2"));

        enabled(file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(QuestionnaireDefinition.class).version())
                    .isEqualTo("test-only-v2");
        });
    }

    @Test
    void rejectsMissingPathWhenEnabled() {
        runner.withPropertyValues("app.questionnaire.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("app.questionnaire.path is required");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankPath(String path) {
        runner.withPropertyValues(
                "app.questionnaire.enabled=true",
                "app.questionnaire.path=" + path
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsAMissingFile() {
        enabled(directory.resolve("missing.json")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("cannot read questionnaire definition file");
        });
    }

    @Test
    void rejectsInvalidFilesystemPath() {
        runner.withPropertyValues(
                "app.questionnaire.enabled=true",
                "app.questionnaire.path=invalid\u0000path"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("not a valid filesystem path");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "null"})
    void rejectsInvalidDefinitionDuringStartup(String json) throws Exception {
        Path file = Files.writeString(directory.resolve("invalid.json"), json);

        enabled(file).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(QuestionnaireDefinitionException.class.getName());
        });
    }

    @Test
    void validatesDefinitionEvenWithGlobalLazyInitialization() throws Exception {
        Path file = Files.writeString(directory.resolve("invalid-lazy.json"), "{}");

        enabled(file)
                .withInitializer(context -> context.addBeanFactoryPostProcessor(
                        new LazyInitializationBeanFactoryPostProcessor()
                ))
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner enabled(Path file) {
        return runner.withPropertyValues(
                "app.questionnaire.enabled=true",
                "app.questionnaire.path=" + file
        );
    }
}
