--liquibase formatted sql

--changeset fuzis:v1-005
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_roles_name_not_blank CHECK (BTRIM(name) <> '')
);

CREATE UNIQUE INDEX uq_roles_single_default
    ON roles (is_default)
    WHERE is_default = TRUE;

INSERT INTO roles (name, description, is_default) VALUES
    ('User', 'Default role assigned to newly registered users', TRUE),
    ('Admin', 'System administrator role', FALSE);

