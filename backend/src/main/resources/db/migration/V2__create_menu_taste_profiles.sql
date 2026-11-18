CREATE TABLE taste_profile_run (
    profile_version VARCHAR(128) PRIMARY KEY,
    data_version VARCHAR(128) NOT NULL REFERENCES catalog_dataset(data_version),
    catalog_sha256 CHAR(64) NOT NULL,
    bundle_sha256 CHAR(64) NOT NULL,
    definition_version VARCHAR(32) NOT NULL CHECK (definition_version = 'taste-v1'),
    model TEXT NOT NULL,
    model_digest TEXT NOT NULL,
    prompt_sha256 CHAR(64) NOT NULL,
    ollama_version TEXT NOT NULL,
    record_count INTEGER NOT NULL CHECK (record_count > 0),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (profile_version, data_version)
);

CREATE TABLE menu_taste_profile (
    profile_version VARCHAR(128) NOT NULL,
    data_version VARCHAR(128) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    input_sha256 CHAR(64) NOT NULL,
    input_basis VARCHAR(24) NOT NULL CHECK (input_basis IN ('NAME_ONLY', 'NAME_AND_DESCRIPTION')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ESTIMATED', 'UNKNOWN', 'NOT_MENU')),
    sweetness DOUBLE PRECISION,
    sourness DOUBLE PRECISION,
    bitterness DOUBLE PRECISION,
    reason TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'UNREVIEWED'
        CHECK (review_status IN ('UNREVIEWED', 'APPROVED', 'REJECTED')),
    PRIMARY KEY (profile_version, menu_id),
    FOREIGN KEY (profile_version, data_version) REFERENCES taste_profile_run(profile_version, data_version),
    FOREIGN KEY (data_version, menu_id) REFERENCES catalog_menu(data_version, menu_id),
    CHECK ((status = 'ESTIMATED' AND
            sweetness IS NOT NULL AND sweetness BETWEEN 0 AND 1 AND
            sourness IS NOT NULL AND sourness BETWEEN 0 AND 1 AND
            bitterness IS NOT NULL AND bitterness BETWEEN 0 AND 1) OR
           (status IN ('UNKNOWN', 'NOT_MENU') AND
            sweetness IS NULL AND sourness IS NULL AND bitterness IS NULL))
);
