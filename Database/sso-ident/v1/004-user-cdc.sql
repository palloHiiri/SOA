--liquibase formatted sql

--changeset fuzis:sso-ident-cdc-001
CREATE SEQUENCE sso_ident_user_cdc_version_seq;

CREATE TABLE sso_ident_user_cdc (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_version BIGINT NOT NULL DEFAULT nextval('sso_ident_user_cdc_version_seq'),
    event_type VARCHAR(64) NOT NULL,
    email VARCHAR(320),
    username VARCHAR(128),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sso_ident_user_cdc_user_id ON sso_ident_user_cdc(user_id);
CREATE UNIQUE INDEX uq_sso_ident_user_cdc_event_version ON sso_ident_user_cdc(event_version);

--changeset fuzis:sso-ident-cdc-002 splitStatements:false
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN
        CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD 'debezium';
    ELSE
        ALTER ROLE debezium WITH LOGIN REPLICATION PASSWORD 'debezium';
    END IF;
END $$;

--changeset fuzis:sso-ident-cdc-003
GRANT CONNECT ON DATABASE sso_ident TO debezium;
GRANT USAGE ON SCHEMA public TO debezium;
GRANT SELECT ON TABLE sso_ident_user_cdc TO debezium;

--changeset fuzis:sso-ident-cdc-004
CREATE PUBLICATION sso_ident_user_cdc_publication
    FOR TABLE sso_ident_user_cdc
    WITH (publish = 'insert');
