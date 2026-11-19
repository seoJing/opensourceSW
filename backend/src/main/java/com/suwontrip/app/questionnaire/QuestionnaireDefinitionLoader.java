package com.suwontrip.app.questionnaire;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class QuestionnaireDefinitionLoader {
    private final QuestionnaireDefinitionValidator validator = new QuestionnaireDefinitionValidator();
    private final ObjectReader reader;

    public QuestionnaireDefinitionLoader() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();

        reader = JsonMapper.builder(factory)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setAcceptBlankAsEmpty(false)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build()
                .readerFor(QuestionnaireDefinition.class);
    }

    public QuestionnaireDefinition load(Path path) {
        Objects.requireNonNull(path, "path");

        try (InputStream input = Files.newInputStream(path)) {
            QuestionnaireDefinition definition = reader.readValue(input);
            validator.validate(definition);
            return definition;
        } catch (JsonMappingException exception) {
            throw new QuestionnaireDefinitionException(
                    fieldPath(exception),
                    "invalid JSON field or value",
                    exception
            );
        } catch (JsonProcessingException exception) {
            throw new QuestionnaireDefinitionException("$", "invalid JSON document", exception);
        } catch (IOException exception) {
            throw new QuestionnaireDefinitionException(
                    "$",
                    "cannot read questionnaire definition file: " + path,
                    exception
            );
        }
    }

    private String fieldPath(JsonMappingException exception) {
        StringBuilder path = new StringBuilder("$");
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                path.append('.').append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
