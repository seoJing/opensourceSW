CREATE TABLE catalog_dataset (
    data_version VARCHAR(128) PRIMARY KEY,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    source_sha256 CHAR(64) NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cafe_count INTEGER NOT NULL CHECK (cafe_count > 0),
    menu_count INTEGER NOT NULL CHECK (menu_count > 0)
);

CREATE TABLE catalog_cafe (
    data_version VARCHAR(128) NOT NULL REFERENCES catalog_dataset(data_version),
    cafe_id VARCHAR(64) NOT NULL,
    name TEXT NOT NULL,
    road_address TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    source_url TEXT NOT NULL,
    source_external_id TEXT NOT NULL,
    source_observed_on DATE NOT NULL,
    PRIMARY KEY (data_version, cafe_id),
    CHECK ((latitude IS NULL AND longitude IS NULL) OR
           (latitude IS NOT NULL AND longitude IS NOT NULL AND
            latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);

CREATE TABLE catalog_menu (
    data_version VARCHAR(128) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    cafe_id VARCHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    name TEXT NOT NULL,
    description TEXT,
    price_krw BIGINT CHECK (price_krw >= 0),
    is_demo BOOLEAN NOT NULL,
    availability VARCHAR(20) NOT NULL CHECK
        (availability IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE', 'SEASON_ENDED')),
    confirmed_on DATE,
    confirmation_reference TEXT,
    source_url TEXT NOT NULL,
    source_external_id TEXT NOT NULL,
    source_observed_on DATE NOT NULL,
    PRIMARY KEY (data_version, menu_id),
    FOREIGN KEY (data_version, cafe_id) REFERENCES catalog_cafe(data_version, cafe_id),
    UNIQUE (data_version, ordinal),
    CHECK ((confirmed_on IS NULL AND confirmation_reference IS NULL) OR
           (confirmed_on IS NOT NULL AND confirmation_reference IS NOT NULL)),
    CHECK (availability <> 'UNKNOWN' OR confirmed_on IS NULL),
    CHECK (availability NOT IN ('AVAILABLE', 'UNAVAILABLE') OR confirmed_on IS NOT NULL)
);

CREATE INDEX catalog_menu_cafe_order ON catalog_menu(data_version, cafe_id, ordinal);

CREATE TABLE catalog_active (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    data_version VARCHAR(128) REFERENCES catalog_dataset(data_version)
);

INSERT INTO catalog_active(singleton_id, data_version) VALUES (1, NULL);
