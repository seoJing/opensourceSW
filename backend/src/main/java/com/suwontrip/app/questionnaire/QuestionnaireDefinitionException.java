package com.suwontrip.app.questionnaire;

public final class QuestionnaireDefinitionException extends RuntimeException {
    private final String fieldPath;

    public QuestionnaireDefinitionException(String fieldPath, String message) {
        super(fieldPath + ": " + message);
        this.fieldPath = fieldPath;
    }

    public QuestionnaireDefinitionException(String fieldPath, String message, Throwable cause) {
        super(fieldPath + ": " + message, cause);
        this.fieldPath = fieldPath;
    }

    public String getFieldPath() {
        return fieldPath;
    }
}
