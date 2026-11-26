package com.suwontrip.app.questionnaire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionnaireControllerTests {
    private static final String URL = "/api/v1/preference-questionnaire";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(
                    QuestionnaireConfiguration.class,
                    QuestionnaireController.class,
                    MvcConfiguration.class
            );

    @TempDir
    Path directory;

    @Test
    void doesNotRegisterAnEndpointWithoutAnEnabledDefinition() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(QuestionnaireController.class);
            mvc(context).perform(get(URL)).andExpect(status().isNotFound());
        });
    }

    @Test
    void returnsTheDocumentedDefinitionForAnAuthenticatedPrincipal() throws Exception {
        enabled().run(context -> {
            mvc(context).perform(get(URL).principal(() -> "test-user"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().json(QuestionnaireDefinitionTests.JSON, true))
                    .andExpect(jsonPath("$.questions[0].type").value("single"))
                    .andExpect(jsonPath("$.questions[1].type").value("multiple"));
        });
    }

    @Test
    void rejectsAnonymousRequestsWithoutLeakingTheDefinition() throws Exception {
        enabled().run(context -> {
            mvc(context).perform(get(URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(jsonPath("$.errors").isEmpty())
                    .andExpect(jsonPath("$.questions").doesNotExist())
                    .andExpect(jsonPath("$.version").doesNotExist());
        });
    }

    @Test
    void doesNotTrustUserHeadersOrAnUnauthenticatedSession() throws Exception {
        enabled().run(context -> {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("userId", "forged-user");

            mvc(context).perform(get(URL)
                            .session(session)
                            .header("X-User-Id", "forged-user")
                            .header("Authorization", "Bearer not-a-session"))
                    .andExpect(status().isUnauthorized());
        });
    }

    private WebApplicationContextRunner enabled() throws Exception {
        Path file = Files.writeString(directory.resolve("questionnaire.json"), QuestionnaireDefinitionTests.JSON);
        return runner.withPropertyValues(
                "app.questionnaire.enabled=true",
                "app.questionnaire.path=" + file
        );
    }

    private MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @EnableWebMvc
    static class MvcConfiguration {
    }
}
