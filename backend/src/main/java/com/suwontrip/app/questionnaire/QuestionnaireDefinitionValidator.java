package com.suwontrip.app.questionnaire;

import java.util.HashSet;
import java.util.Set;

public final class QuestionnaireDefinitionValidator {

    public void validate(QuestionnaireDefinition definition) {
        require(definition != null, "$", "questionnaire definition is required");
        requireText(definition.version(), "$.version");
        require(
                definition.questions() != null && !definition.questions().isEmpty(),
                "$.questions",
                "nonempty questions array is required"
        );

        Set<String> questionIds = new HashSet<>();
        for (int i = 0; i < definition.questions().size(); i++) {
            String path = "$.questions[" + i + "]";
            QuestionnaireDefinition.Question question = definition.questions().get(i);

            require(question != null, path, "question must not be null");
            requireText(question.id(), path + ".id");
            require(questionIds.add(question.id()), path + ".id", "duplicate question id");
            requireText(question.label(), path + ".label");
            require(question.type() != null, path + ".type", "question type is required");
            require(
                    question.options() != null && !question.options().isEmpty(),
                    path + ".options",
                    "nonempty options array is required"
            );
            require(
                    question.minSelections() != null && question.minSelections() >= 0,
                    path + ".minSelections",
                    "minimum selections must be a nonnegative integer"
            );
            require(
                    question.maxSelections() != null
                            && question.maxSelections() >= question.minSelections()
                            && question.maxSelections() <= question.options().size(),
                    path + ".maxSelections",
                    "maximum selections must be between minimum selections and option count"
            );
            require(
                    question.type() != QuestionnaireDefinition.QuestionType.SINGLE
                            || question.maxSelections() == 1,
                    path + ".maxSelections",
                    "single-choice questions must have maximum selections of 1"
            );

            Set<String> optionIds = new HashSet<>();
            for (int j = 0; j < question.options().size(); j++) {
                String optionPath = path + ".options[" + j + "]";
                QuestionnaireDefinition.Option option = question.options().get(j);

                require(option != null, optionPath, "option must not be null");
                requireText(option.id(), optionPath + ".id");
                require(optionIds.add(option.id()), optionPath + ".id", "duplicate option id");
                requireText(option.label(), optionPath + ".label");
            }
        }
    }

    private void requireText(String value, String path) {
        require(value != null && !value.isBlank(), path, "nonblank text is required");
    }

    private void require(boolean condition, String path, String message) {
        if (!condition) {
            throw new QuestionnaireDefinitionException(path, message);
        }
    }
}
