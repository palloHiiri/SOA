CREATE TABLE train_set_cdc (
    train_set_id INTEGER NOT NULL,
    data JSONB NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (train_set_id, version)
);

CREATE OR REPLACE FUNCTION inventory_build_train_set_snapshot(p_train_set_id INTEGER)
RETURNS JSONB
LANGUAGE SQL
STABLE
AS $$
    SELECT jsonb_build_object(
        'id', ts.id,
        'code', ts.code,
        'buildNumber', ts.build_number,
        'name', ts.name,
        'technicalName', ts.technical_name,
        'description', ts.description,
        'lifecycleStatus', jsonb_build_object(
            'id', status.id,
            'code', status.code,
            'name', status.name,
            'description', status.description
        ),
        'carriages', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id', c.id,
                    'inventoryNumber', c.inventory_number,
                    'serialNumber', c.serial_number,
                    'carriageNumber', tsc.carriage_number,
                    'position', tsc.position,
                    'carriageType', jsonb_build_object(
                        'id', ct.id,
                        'code', ct.code,
                        'name', ct.name,
                        'description', ct.description
                    ),
                    'scheme', CASE
                        WHEN scheme.id IS NULL THEN NULL
                        ELSE jsonb_build_object(
                            'id', scheme.id,
                            'code', scheme.code,
                            'name', scheme.name,
                            'storageKey', scheme.storage_key,
                            'version', scheme.version,
                            'seatPositions', COALESCE((
                                SELECT jsonb_agg(
                                    jsonb_build_object(
                                        'id', sp.id,
                                        'seatNumber', sp.seat_number,
                                        'x', sp.x,
                                        'y', sp.y,
                                        'rotation', sp.rotation
                                    ) ORDER BY sp.seat_number
                                )
                                FROM scheme_seat_positions sp
                                WHERE sp.scheme_id = scheme.id
                            ), '[]'::jsonb)
                        )
                    END
                ) ORDER BY tsc.position
            )
            FROM train_set_carriages tsc
            JOIN carriages c ON c.id = tsc.carriage_id
            JOIN carriage_types ct ON ct.id = c.carriage_type_id
            LEFT JOIN carriage_type_schemes scheme
                ON scheme.carriage_type_id = ct.id
               AND scheme.is_active = TRUE
            WHERE tsc.train_set_id = ts.id
        ), '[]'::jsonb)
    )
    FROM train_sets ts
    JOIN train_set_lifecycle_statuses status
        ON status.id = ts.train_set_lifecycle_status_id
    WHERE ts.id = p_train_set_id;
$$;

CREATE OR REPLACE FUNCTION inventory_validate_train_set_lifecycle_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM train_set_lifecycle_statuses status
            WHERE status.id = NEW.train_set_lifecycle_status_id
              AND status.code = 'DRAFT'
        ) THEN
            RAISE EXCEPTION 'A new train set must be created in DRAFT status';
        END IF;

        RETURN NEW;
    END IF;

    IF NEW.train_set_lifecycle_status_id IS DISTINCT FROM OLD.train_set_lifecycle_status_id
       AND NOT EXISTS (
           SELECT 1
           FROM train_set_lifecycle_transitions transition
           WHERE transition.train_set_lifecycle_statuses_from = OLD.train_set_lifecycle_status_id
             AND transition.train_set_lifecycle_status_to = NEW.train_set_lifecycle_status_id
       )
    THEN
        RAISE EXCEPTION
            'Invalid train set lifecycle transition: % -> %',
            OLD.train_set_lifecycle_status_id,
            NEW.train_set_lifecycle_status_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_train_sets_validate_lifecycle_transition
BEFORE UPDATE OF train_set_lifecycle_status_id ON train_sets
FOR EACH ROW
EXECUTE FUNCTION inventory_validate_train_set_lifecycle_transition();

CREATE OR REPLACE FUNCTION inventory_validate_train_set_carriage_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    lifecycle_code VARCHAR(64);
BEGIN
    SELECT status.code
    INTO lifecycle_code
    FROM train_sets ts
    JOIN train_set_lifecycle_statuses status
        ON status.id = ts.train_set_lifecycle_status_id
    WHERE ts.id = COALESCE(NEW.train_set_id, OLD.train_set_id);

    IF lifecycle_code NOT IN ('DRAFT', 'MAINTENANCE') THEN
        RAISE EXCEPTION
            'Train set composition can only be changed in DRAFT or MAINTENANCE status; current status is %',
            lifecycle_code;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_train_set_carriages_validate_lifecycle
BEFORE INSERT OR UPDATE OR DELETE ON train_set_carriages
FOR EACH ROW
EXECUTE FUNCTION inventory_validate_train_set_carriage_change();

CREATE OR REPLACE FUNCTION inventory_validate_new_carriage()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.carriage_id IS DISTINCT FROM OLD.carriage_id)
       AND NOT EXISTS (
           SELECT 1
           FROM carriages carriage
           WHERE carriage.id = NEW.carriage_id
             AND carriage.is_active = TRUE
       )
    THEN
        RAISE EXCEPTION 'Carriage % is inactive and cannot be added to a train set', NEW.carriage_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_train_set_carriages_validate_carriage
BEFORE INSERT OR UPDATE OF carriage_id ON train_set_carriages
FOR EACH ROW
EXECUTE FUNCTION inventory_validate_new_carriage();

CREATE OR REPLACE FUNCTION inventory_create_train_set_snapshot()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    next_version BIGINT;
BEGIN
    IF NEW.train_set_lifecycle_status_id IS DISTINCT FROM OLD.train_set_lifecycle_status_id
       AND EXISTS (
           SELECT 1
           FROM train_set_lifecycle_statuses status
           WHERE status.id = NEW.train_set_lifecycle_status_id
             AND status.code = 'ACTIVE'
       )
    THEN
        SELECT COALESCE(MAX(version), 0) + 1
        INTO next_version
        FROM train_set_cdc
        WHERE train_set_id = NEW.id;

        INSERT INTO train_set_cdc (train_set_id, data, version)
        VALUES (
            NEW.id,
            inventory_build_train_set_snapshot(NEW.id),
            next_version
        );
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_train_sets_create_cdc_snapshot
AFTER UPDATE OF train_set_lifecycle_status_id ON train_sets
FOR EACH ROW
EXECUTE FUNCTION inventory_create_train_set_snapshot();

--changeset fuzis:inventory-003-cdc-role splitStatements:false
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN
        CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD 'debezium';
    ELSE
        ALTER ROLE debezium WITH LOGIN REPLICATION PASSWORD 'debezium';
    END IF;
END $$;

--changeset fuzis:inventory-003-cdc-grants
GRANT CONNECT ON DATABASE inventory TO debezium;
GRANT USAGE ON SCHEMA public TO debezium;
GRANT SELECT ON TABLE train_set_cdc TO debezium;

--changeset fuzis:inventory-003-cdc-publication
CREATE PUBLICATION inventory_train_set_cdc_publication
    FOR TABLE train_set_cdc
    WITH (publish = 'insert');
