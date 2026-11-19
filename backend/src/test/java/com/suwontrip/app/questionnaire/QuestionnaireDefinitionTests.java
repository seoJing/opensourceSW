package com.suwontrip.app.questionnaire;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionnaireDefinitionTests {
    static final String JSON = """
            {
              "version": "test-only-v1",
              "questions": [
                {
                  "id": "question-z",
                  "label": "가상 단일 선택 문항",
                  "type": "single",
                  "minSelections": 0,
                  "maxSelections": 1,
                  "options": [
                    {"id": "option-b", "label": "가상 선택지 B"},
                    {"id": "option-a", "label": "가상 선택지 A"}
                  ]
                },
                {
                  "id": "question-a",
                  "label": "가상 복수 선택 문항",
                  "type": "multiple",
                  "minSelections": 1,
                  "maxSelections": 2,
                  "options": [
                    {"id": "option-b", "label": "다른 문항의 가상 선택지 B"},
                    {"id": "option-a", "label": "다른 문항의 가상 선택지 A"}
                  ]
                }
              ]
            }
            """;

    private final JsonMapper mapper = new JsonMapper();
    private final QuestionnaireDefinitionLoader loader = new QuestionnaireDefinitionLoader();

    @TempDir
    Path directory;

    @Test
    void preservesQuestionAndOptionDisplayOrderAndTypes() throws Exception {
        QuestionnaireDefinition definition = load(document());

        assertThat(definition.version()).isEqualTo("test-only-v1");
        assertThat(definition.questions())
                .extracting(QuestionnaireDefinition.Question::id)
                .containsExactly("question-z", "question-a");
        assertThat(definition.questions().getFirst().options())
                .extracting(QuestionnaireDefinition.Option::id)
                .containsExactly("option-b", "option-a");
        assertThat(definition.questions().getFirst().minSelections()).isZero();
        assertThat(definition.questions().getFirst().type())
                .isEqualTo(QuestionnaireDefinition.QuestionType.SINGLE);
        assertThat(definition.questions().get(1).type())
                .isEqualTo(QuestionnaireDefinition.QuestionType.MULTIPLE);
    }

    @Test
    void serializesExactlyTheDocumentedResponseShape() throws Exception {
        QuestionnaireDefinition definition = load(document());

        assertThat(mapper.readTree(mapper.writeValueAsString(definition)))
                .isEqualTo(mapper.readTree(JSON));
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("invalidValues")
    void rejectsInvalidDefinitionWithItsFieldPath(
            String pointer,
            String jsonValue,
            String expectedPath
    ) throws Exception {
        ObjectNode definition = document();
        JsonPointer target = JsonPointer.compile(pointer);
        JsonNode parent = definition.at(target.head());
        JsonNode value = mapper.readTree(jsonValue);
        if (parent instanceof ArrayNode array) {
            array.set(target.last().getMatchingIndex(), value);
        } else {
            ((ObjectNode) parent).set(target.last().getMatchingProperty(), value);
        }

        assertThatExceptionOfType(QuestionnaireDefinitionException.class)
                .isThrownBy(() -> load(definition))
                .satisfies(error -> assertThat(error.getFieldPath()).isEqualTo(expectedPath));
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("/version", "null", "$.version"),
                Arguments.of("/version", "\" \"", "$.version"),
                Arguments.of("/version", "42", "$.version"),
                Arguments.of("/questions", "null", "$.questions"),
                Arguments.of("/questions", "[]", "$.questions"),
                Arguments.of("/questions/0", "null", "$.questions[0]"),
                Arguments.of("/questions/0/id", "\"\"", "$.questions[0].id"),
                Arguments.of("/questions/0/label", "\" \"", "$.questions[0].label"),
                Arguments.of("/questions/0/type", "null", "$.questions[0].type"),
                Arguments.of("/questions/0/type", "\"text\"", "$.questions[0].type"),
                Arguments.of("/questions/0/type", "0", "$.questions[0].type"),
                Arguments.of("/questions/0/type", "\"0\"", "$.questions[0].type"),
                Arguments.of("/questions/0/minSelections", "-1", "$.questions[0].minSelections"),
                Arguments.of("/questions/0/minSelections", "null", "$.questions[0].minSelections"),
                Arguments.of("/questions/0/minSelections", "\"0\"", "$.questions[0].minSelections"),
                Arguments.of("/questions/0/minSelections", "\" \"", "$.questions[0].minSelections"),
                Arguments.of("/questions/0/minSelections", "0.5", "$.questions[0].minSelections"),
                Arguments.of(
                        "/questions/0/minSelections",
                        "2147483648",
                        "$.questions[0].minSelections"
                ),
                Arguments.of("/questions/0/maxSelections", "null", "$.questions[0].maxSelections"),
                Arguments.of("/questions/0/maxSelections", "3", "$.questions[0].maxSelections"),
                Arguments.of("/questions/0/maxSelections", "2", "$.questions[0].maxSelections"),
                Arguments.of("/questions/0/maxSelections", "0", "$.questions[0].maxSelections"),
                Arguments.of("/questions/1/maxSelections", "0", "$.questions[1].maxSelections"),
                Arguments.of("/questions/0/options", "[]", "$.questions[0].options"),
                Arguments.of("/questions/0/options", "null", "$.questions[0].options"),
                Arguments.of("/questions/0/options/0", "null", "$.questions[0].options[0]"),
                Arguments.of("/questions/0/options/0/id", "\" \"", "$.questions[0].options[0].id"),
                Arguments.of(
                        "/questions/0/options/0/label",
                        "null",
                        "$.questions[0].options[0].label"
                ),
                Arguments.of(
                        "/questions/0/options/0/label",
                        "true",
                        "$.questions[0].options[0].label"
                ),
                Arguments.of("/questions/1/id", "\"question-z\"", "$.questions[1].id"),
                Arguments.of(
                        "/questions/0/options/1/id",
                        "\"option-b\"",
                        "$.questions[0].options[1].id"
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "label", "type", "minSelections", "maxSelections", "options"})
    void rejectsOmittedRequiredQuestionFields(String field) throws Exception {
        ObjectNode definition = document();
        ((ObjectNode) definition.at("/questions/0")).remove(field);

        assertThatExceptionOfType(QuestionnaireDefinitionException.class)
                .isThrownBy(() -> load(definition))
                .satisfies(error -> assertThat(error.getFieldPath())
                        .isEqualTo("$.questions[0]." + field));
    }

    @Test
    void rejectsAnUnknownFieldInsteadOfIgnoringATypo() throws Exception {
        ObjectNode definition = document();
        ((ObjectNode) definition.at("/questions/0")).put("maxSelection", 1);

        assertThatExceptionOfType(QuestionnaireDefinitionException.class)
                .isThrownBy(() -> load(definition))
                .satisfies(error -> assertThat(error.getFieldPath())
                        .isEqualTo("$.questions[0].maxSelection"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "[]", "{}"})
    void rejectsEmptyOrInvalidDocuments(String json) throws Exception {
        Path file = Files.writeString(directory.resolve("invalid.json"), json);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(QuestionnaireDefinitionException.class);
    }

    @Test
    void rejectsDuplicateJsonKeysAndTrailingDocuments() throws Exception {
        String duplicate = JSON.replace(
                "\"maxSelections\": 1",
                "\"maxSelections\": 1, \"maxSelections\": 0"
        );
        Path file = Files.writeString(directory.resolve("duplicate.json"), duplicate);
        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(QuestionnaireDefinitionException.class);

        Files.writeString(file, JSON + "{}");
        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(QuestionnaireDefinitionException.class);
    }

    @Test
    void reportsAnUnreadableFileWithItsOriginalCause() {
        assertThatExceptionOfType(QuestionnaireDefinitionException.class)
                .isThrownBy(() -> loader.load(directory.resolve("missing.json")))
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    void protectsBothQuestionAndOptionListsFromMutation() throws Exception {
        QuestionnaireDefinition definition = load(document());
        var questions = new ArrayList<>(definition.questions());
        var options = new ArrayList<>(questions.getFirst().options());
        var originalQuestion = questions.getFirst();
        var question = new QuestionnaireDefinition.Question(
                originalQuestion.id(),
                originalQuestion.label(),
                originalQuestion.type(),
                originalQuestion.minSelections(),
                originalQuestion.maxSelections(),
                options
        );
        var copiedDefinition = new QuestionnaireDefinition(definition.version(), questions);

        questions.clear();
        options.clear();

        assertThat(copiedDefinition.questions()).hasSize(2);
        assertThat(question.options()).hasSize(2);
        assertThatThrownBy(() -> copiedDefinition.questions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> question.options().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ObjectNode document() throws IOException {
        return (ObjectNode) mapper.readTree(JSON);
    }

    private QuestionnaireDefinition load(ObjectNode document) throws IOException {
        Path file = Files.writeString(directory.resolve("questionnaire.json"), document.toString());
        return loader.load(file);
    }
}
