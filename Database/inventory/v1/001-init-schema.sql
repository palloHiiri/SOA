CREATE TABLE carriage_types (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE carriages (
    id SERIAL PRIMARY KEY,
    carriage_type_id INTEGER NOT NULL REFERENCES carriage_types(id),
    inventory_number VARCHAR(128) NOT NULL UNIQUE,
    serial_number VARCHAR(128) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE carriage_type_schemes (
    id SERIAL PRIMARY KEY,
    carriage_type_id INTEGER NOT NULL REFERENCES carriage_types(id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    version INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carriage_type_schemes_type_version UNIQUE (carriage_type_id, version),
    CONSTRAINT ck_carriage_type_schemes_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX uq_carriage_type_schemes_active_type
    ON carriage_type_schemes(carriage_type_id)
    WHERE is_active;

CREATE TABLE scheme_seat_positions (
    id SERIAL PRIMARY KEY,
    scheme_id INTEGER NOT NULL REFERENCES carriage_type_schemes(id) ON DELETE CASCADE,
    seat_number VARCHAR(3) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    rotation DOUBLE PRECISION NOT NULL DEFAULT 0,
    CONSTRAINT uq_scheme_seat_positions_scheme_seat UNIQUE (scheme_id, seat_number)
);

CREATE INDEX idx_carriages_carriage_type_id ON carriages(carriage_type_id);
CREATE INDEX idx_carriage_type_schemes_carriage_type_id ON carriage_type_schemes(carriage_type_id);
CREATE INDEX idx_scheme_seat_positions_scheme_id ON scheme_seat_positions(scheme_id);

CREATE TABLE train_set_lifecycle_statuses (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE train_set_lifecycle_transitions (
    id SERIAL PRIMARY KEY,
    train_set_lifecycle_statuses_from INTEGER NOT NULL REFERENCES train_set_lifecycle_statuses(id),
    train_set_lifecycle_status_to INTEGER NOT NULL REFERENCES train_set_lifecycle_statuses(id),
    CONSTRAINT uq_train_set_lifecycle_transition UNIQUE (
        train_set_lifecycle_statuses_from,
        train_set_lifecycle_status_to
    ),
    CONSTRAINT ck_train_set_lifecycle_transition_different_statuses CHECK (
        train_set_lifecycle_statuses_from <> train_set_lifecycle_status_to
    )
);

CREATE TABLE train_sets (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    build_number INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    technical_name VARCHAR(255) NOT NULL,
    description TEXT,
    train_set_lifecycle_status_id INTEGER NOT NULL REFERENCES train_set_lifecycle_statuses(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_train_sets_code_build_number UNIQUE (code, build_number),
    CONSTRAINT ck_train_sets_build_number_positive CHECK (build_number > 0)
);

CREATE TABLE train_set_carriages (
    id SERIAL PRIMARY KEY,
    train_set_id INTEGER NOT NULL REFERENCES train_sets(id) ON DELETE CASCADE,
    carriage_id INTEGER NOT NULL REFERENCES carriages(id),
    carriage_number VARCHAR(16) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT uq_train_set_carriages_train_set_carriage UNIQUE (train_set_id, carriage_id),
    CONSTRAINT uq_train_set_carriages_train_set_number UNIQUE (train_set_id, carriage_number),
    CONSTRAINT uq_train_set_carriages_train_set_position UNIQUE (train_set_id, position),
    CONSTRAINT ck_train_set_carriages_position_positive CHECK (position > 0)
);

CREATE INDEX idx_train_set_carriages_train_set_id ON train_set_carriages(train_set_id);
CREATE INDEX idx_train_set_carriages_carriage_id ON train_set_carriages(carriage_id);
CREATE INDEX idx_train_sets_lifecycle_status_id ON train_sets(train_set_lifecycle_status_id);

CREATE OR REPLACE FUNCTION inventory_set_modified_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.modified_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_carriage_type_schemes_modified_at
BEFORE UPDATE ON carriage_type_schemes
FOR EACH ROW
EXECUTE FUNCTION inventory_set_modified_at();

CREATE OR REPLACE FUNCTION inventory_set_train_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_train_sets_updated_at
BEFORE UPDATE ON train_sets
FOR EACH ROW
EXECUTE FUNCTION inventory_set_train_set_updated_at();
