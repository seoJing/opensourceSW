package com.suwontrip.app.preferences;

import com.suwontrip.app.questionnaire.QuestionnaireDefinition;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition.Question;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class PreferenceValidator {
    private final QuestionnaireDefinition definition;
    private final Map<String, Question> byId = new HashMap<>();

    PreferenceValidator(QuestionnaireDefinition definition) {
        this.definition = definition;
        definition.questions().forEach(q -> byId.put(q.id(), q));
    }

    List<PreferenceAnswers.Answer> validate(PreferenceAnswers input) {
        if (input == null || blank(input.questionnaireVersion()) || input.answers() == null) {
            throw PreferenceProblem.malformed();
        }
        for (var answer : input.answers()) {
            if (answer == null || blank(answer.questionId()) || answer.optionIds() == null
                    || answer.optionIds().stream().anyMatch(PreferenceValidator::blank)) {
                throw PreferenceProblem.malformed();
            }
        }
        if (!definition.version().equals(input.questionnaireVersion())) {
            throw PreferenceProblem.stale();
        }

        Map<String, PreferenceAnswers.Answer> normalized = new HashMap<>();
        int coffeeQuestionIndex = -1;
        for (int i = 0; i < input.answers().size(); i++) {
            var answer = input.answers().get(i);
            String id = answer.questionId();
            String field = "answers[" + i + "]";
            Question question = byId.get(id);
            if (question == null) {
                throw PreferenceProblem.invalid(field + ".questionId", id, "UNKNOWN_QUESTION", "등록되지 않은 문항이에요.");
            }
            if (normalized.containsKey(id)) {
                throw PreferenceProblem.invalid(field + ".questionId", id, "DUPLICATE_QUESTION", "문항이 중복됐어요.");
            }
            if ("Q4".equals(id)) {
                coffeeQuestionIndex = i;
            }
            normalized.put(id, normalize(question, answer, field + ".optionIds"));
        }
        List<PreferenceAnswers.Answer> ordered = new ArrayList<>();
        for (Question question : definition.questions()) {
            var answer = normalized.get(question.id());
            if (answer == null) {
                throw PreferenceProblem.invalid("answers", question.id(), "MISSING_QUESTION", "누락된 문항이에요.");
            }
            ordered.add(answer);
        }
        if ("v0.1.0".equals(definition.version()) && normalized.containsKey("Q1") && normalized.containsKey("Q4")
                && !normalized.get("Q1").optionIds().contains("A") && !normalized.get("Q4").optionIds().isEmpty()) {
            throw PreferenceProblem.invalid("answers[" + coffeeQuestionIndex + "].optionIds",
                    "Q4", "SELECTION_COUNT", "음료를 고르지 않으면 Q4는 빈 배열로 보내 주세요.");
        }
        return List.copyOf(ordered);
    }

    private static PreferenceAnswers.Answer normalize(Question question, PreferenceAnswers.Answer answer, String field) {
        var selected = new HashSet<>(answer.optionIds());
        if (selected.size() != answer.optionIds().size()) {
            throw PreferenceProblem.invalid(field, question.id(), "DUPLICATE_OPTION", "선택지가 중복됐어요.");
        }
        var ordered = question.options().stream().map(QuestionnaireDefinition.Option::id).filter(selected::contains).toList();
        if (ordered.size() != selected.size()) {
            throw PreferenceProblem.invalid(field, question.id(), "UNKNOWN_OPTION", "등록되지 않은 선택지예요.");
        }
        if (ordered.size() < question.minSelections() || ordered.size() > question.maxSelections()) {
            throw PreferenceProblem.invalid(field, question.id(), "SELECTION_COUNT", "선택 가능한 개수를 확인해 주세요.");
        }
        return new PreferenceAnswers.Answer(question.id(), ordered);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
