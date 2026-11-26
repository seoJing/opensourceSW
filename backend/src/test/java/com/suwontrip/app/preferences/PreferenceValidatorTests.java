package com.suwontrip.app.preferences;

import com.suwontrip.app.questionnaire.QuestionnaireDefinition;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition.Option;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition.Question;
import com.suwontrip.app.questionnaire.QuestionnaireDefinition.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenceValidatorTests {
    private final List<Option> options = List.of(new Option("A", "첫째"), new Option("B", "둘째"));
    private final PreferenceValidator validator = new PreferenceValidator(new QuestionnaireDefinition("v0.1.0", List.of(
            new Question("Q1", "종류", QuestionType.SINGLE, 0, 1, options),
            new Question("Q4", "커피", QuestionType.SINGLE, 0, 1, options),
            new Question("M", "복수", QuestionType.MULTIPLE, 0, 2, options))));

    private PreferenceAnswers.Answer answer(String id, String... choices) {
        return new PreferenceAnswers.Answer(id, List.of(choices));
    }

    private PreferenceAnswers request(PreferenceAnswers.Answer... answers) {
        return new PreferenceAnswers("v0.1.0", List.of(answers));
    }

    @Test
    void storesQuestionAndOptionOrderFromTheDefinition() {
        assertThat(validator.validate(request(answer("M", "B", "A"), answer("Q4"), answer("Q1", "B"))))
                .containsExactly(answer("Q1", "B"), answer("Q4"), answer("M", "A", "B"));
    }

    @Test
    void rejectsHiddenAnswerAndAllowsAnsweredCoffeeQuestionForBeverages() {
        rejects(request(answer("M"), answer("Q4", "B"), answer("Q1", "B")), "SELECTION_COUNT");
        assertThat(validator.validate(request(answer("Q1", "A"), answer("Q4", "B"), answer("M")))).hasSize(3);
    }

    @Test
    void rejectsIncompleteDuplicateAndUnknownAnswers() {
        rejects(request(answer("Q1"), answer("M")), "MISSING_QUESTION");
        rejects(request(answer("Q1"), answer("Q1")), "DUPLICATE_QUESTION");
        rejects(request(answer("wrong")), "UNKNOWN_QUESTION");
        rejects(request(answer("Q1", "wrong")), "UNKNOWN_OPTION");
        rejects(request(answer("M", "A", "A")), "DUPLICATE_OPTION");
        rejects(request(answer("Q1", "A", "B")), "SELECTION_COUNT");
    }

    @Test
    void checksStructureBeforeVersionAndVersionBeforeQuestionIds() {
        assertThatThrownBy(() -> validator.validate(new PreferenceAnswers("old", List.of(answer("Q1", "")))))
                .isInstanceOfSatisfying(PreferenceProblem.class, e -> assertThat(e.body.code()).isEqualTo("INVALID_REQUEST"));
        assertThatThrownBy(() -> validator.validate(new PreferenceAnswers("old", List.of(answer("wrong")))))
                .isInstanceOfSatisfying(PreferenceProblem.class, e -> assertThat(e.body.code()).isEqualTo("QUESTIONNAIRE_VERSION_MISMATCH"));
    }

    private void rejects(PreferenceAnswers request, String reason) {
        assertThatThrownBy(() -> validator.validate(request)).isInstanceOfSatisfying(PreferenceProblem.class,
                e -> assertThat(e.body.errors()).extracting(PreferenceProblem.Detail::reason).containsExactly(reason));
    }
}
