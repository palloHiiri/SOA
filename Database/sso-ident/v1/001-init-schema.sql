--liquibase formatted sql

--changeset fuzis:v1-001
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE user_statuses (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status_id INTEGER NOT NULL REFERENCES user_statuses(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_status_id ON users(status_id);

CREATE TABLE credential_types (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE user_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_type_id INTEGER NOT NULL REFERENCES credential_types(id),
    identifier VARCHAR(255),
    secret_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_credentials_user_id ON user_credentials(user_id);
CREATE INDEX idx_user_credentials_type_id ON user_credentials(credential_type_id);
CREATE INDEX idx_user_credentials_identifier ON user_credentials(identifier);

CREATE TABLE attributes (
    id SERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_unique BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE user_attributes (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attribute_id INTEGER NOT NULL REFERENCES attributes(id) ON DELETE CASCADE,
    value TEXT NOT NULL,
    PRIMARY KEY (user_id, attribute_id)
);
CREATE INDEX idx_user_attributes_attribute_id ON user_attributes(attribute_id);
CREATE INDEX idx_user_attributes_attribute_value ON user_attributes(attribute_id, value);
CREATE INDEX idx_user_attributes_attribute_lower_value ON user_attributes(attribute_id, LOWER(value));

CREATE TABLE verification_code_purposes (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE verification_code_channels (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE verification_code_types (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    purpose_id INTEGER NOT NULL REFERENCES verification_code_purposes(id),
    channel_id INTEGER NOT NULL REFERENCES verification_code_channels(id),
    ttl_seconds INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    CONSTRAINT chk_verification_code_types_ttl CHECK (ttl_seconds > 0),
    CONSTRAINT chk_verification_code_types_max_attempts CHECK (max_attempts > 0)
);

CREATE TABLE verification_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type_id INTEGER NOT NULL REFERENCES verification_code_types(id),
    destination VARCHAR(320) NOT NULL,
    code_hash TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT chk_verification_codes_status CHECK (status IN ('ACTIVE', 'USED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_verification_codes_expiration CHECK (expires_at > created_at)
);
CREATE INDEX idx_verification_codes_user_id ON verification_codes(user_id);
CREATE INDEX idx_verification_codes_user_type_status ON verification_codes(user_id, type_id, status);
CREATE INDEX idx_verification_codes_expires_at ON verification_codes(expires_at);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    ip INET,
    user_agent TEXT,
    CONSTRAINT chk_user_sessions_expiration CHECK (expires_at > created_at)
);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_user_sessions_user_active ON user_sessions(user_id, revoked_at, expires_at);

CREATE TABLE sso_actions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE user_sso_action_histories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action_id integer NOT NULL REFERENCES sso_actions(id),
    actor_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip INET,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    metadata JSONB
);
CREATE INDEX idx_user_sso_action_histories_user_id ON user_sso_action_histories(user_id);
CREATE INDEX idx_user_sso_action_histories_actor_user_id ON user_sso_action_histories(actor_user_id);
CREATE INDEX idx_user_sso_action_histories_action_id ON user_sso_action_histories(action_id);
CREATE INDEX idx_user_sso_action_histories_created_at ON user_sso_action_histories(created_at);
CREATE INDEX idx_user_sso_action_histories_user_created_at ON user_sso_action_histories(user_id, created_at);
