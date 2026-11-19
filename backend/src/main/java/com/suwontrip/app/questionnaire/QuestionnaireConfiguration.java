package com.suwontrip.app.questionnaire;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.questionnaire", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QuestionnaireProperties.class)
public class QuestionnaireConfiguration {

    @Bean
    @Lazy(false)
    QuestionnaireDefinition questionnaireDefinition(QuestionnaireProperties properties) {
        String configuredPath = properties.path();
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException(
                    "app.questionnaire.path is required when app.questionnaire.enabled=true"
            );
        }

        Path path;
        try {
            path = Path.of(configuredPath);
        } catch (InvalidPathException exception) {
            throw new IllegalStateException(
                    "app.questionnaire.path is not a valid filesystem path",
                    exception
            );
        }

        return new QuestionnaireDefinitionLoader().load(path);
    }
}
