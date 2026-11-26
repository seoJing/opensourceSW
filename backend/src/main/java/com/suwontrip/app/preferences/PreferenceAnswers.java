package com.suwontrip.app.preferences;

import java.time.Instant;
import java.util.List;

record PreferenceAnswers(String questionnaireVersion, List<Answer> answers) {

    record Answer(String questionId, List<String> optionIds) {}

    record Saved(boolean saved, String questionnaireVersion, List<Answer> answers, Instant updatedAt) {
        static Saved empty() {
            return new Saved(false, null, List.of(), null);
        }
    }
}
