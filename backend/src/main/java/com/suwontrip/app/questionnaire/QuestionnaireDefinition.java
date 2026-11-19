package com.suwontrip.app.questionnaire;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record QuestionnaireDefinition(
        String version,
        List<Question> questions
) {
    public QuestionnaireDefinition {
        questions = questions == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(questions));
    }

    public record Question(
            String id,
            String label,
            QuestionType type,
            Integer minSelections,
            Integer maxSelections,
            List<Option> options
    ) {
        public Question {
            options = options == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(options));
        }
    }

    public enum QuestionType {
        @JsonProperty("single")
        SINGLE,
        @JsonProperty("multiple")
        MULTIPLE
    }

    public record Option(String id, String label) {
    }
}
