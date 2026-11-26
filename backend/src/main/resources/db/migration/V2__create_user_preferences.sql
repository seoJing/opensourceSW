CREATE TABLE user_preferences (
    user_id TEXT PRIMARY KEY,
    questionnaire_version TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
