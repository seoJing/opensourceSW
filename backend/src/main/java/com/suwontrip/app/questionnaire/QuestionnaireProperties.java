package com.suwontrip.app.questionnaire;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.questionnaire")
public record QuestionnaireProperties(String path) {
}
