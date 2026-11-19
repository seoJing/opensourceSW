package com.suwontrip.app.questionnaire;

import com.suwontrip.app.SuwonTripApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionnaireApplicationTests {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SuwonTripApplication.class)
            .withPropertyValues("spring.profiles.active=test");

    @TempDir
    Path directory;

    @Test
    void loadsEnabledDefinitionAndRejectsAnonymousRequests() throws Exception {
        runner.withPropertyValues(
                "app.questionnaire.enabled=true",
                "app.questionnaire.path=" + fixture()
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(QuestionnaireDefinition.class);
            assertThat(context).hasSingleBean(QuestionnaireController.class);
            assertThat(context.getBean(QuestionnaireDefinition.class).version())
                    .isEqualTo("test-only-v1");

            MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(get("/api/v1/preference-questionnaire"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.questions").doesNotExist());
        });
    }

    @Test
    void rejectsInvalidDefinitionAtApplicationStartup() throws Exception {
        Path invalid = Files.writeString(directory.resolve("invalid-questionnaire.json"), "{}");

        runner.withPropertyValues(
                "app.questionnaire.enabled=true",
                "app.questionnaire.path=" + invalid
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("$.version: nonblank text is required");
        });
    }

    private Path fixture() throws Exception {
        return Files.writeString(
                directory.resolve("questionnaire-example.json"),
                QuestionnaireDefinitionTests.JSON
        );
    }
}
