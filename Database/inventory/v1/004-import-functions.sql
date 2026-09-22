--liquibase formatted sql

--changeset fuzis:inventory-004-import splitStatements:false
CREATE OR REPLACE FUNCTION inventory_import_train_set(p_data JSONB)
RETURNS train_sets
LANGUAGE plpgsql
AS $$
DECLARE
    v_train_set_id INTEGER;
    v_draft_status_id INTEGER;
    v_carriage_type_id INTEGER;
    v_scheme_id INTEGER;
    v_carriage_id INTEGER;
    v_train_set train_sets%ROWTYPE;
    v_carriage JSONB;
    v_type JSONB;
    v_scheme JSONB;
    v_seat JSONB;
    v_type_ref TEXT;
    v_type_ids JSONB := '{}'::jsonb;
    v_build_number INTEGER;
BEGIN
    IF p_data IS NULL OR jsonb_typeof(p_data) <> 'object' THEN
        RAISE EXCEPTION 'Import payload must be a JSON object' USING ERRCODE = '22023';
    END IF;

    IF jsonb_path_exists(p_data, '$.**.id') OR p_data ? 'lifecycleStatus' THEN
        RAISE EXCEPTION 'Import payload must not contain database identifiers or lifecycle status'
            USING ERRCODE = '22023';
    END IF;

    IF p_data->>'code' IS NULL
       OR p_data->>'name' IS NULL
       OR p_data->>'technicalName' IS NULL
       OR p_data->>'buildNumber' IS NULL
       OR jsonb_typeof(p_data->'carriages') <> 'array'
    THEN
        RAISE EXCEPTION 'Import payload is missing required fields'
            USING ERRCODE = '22023';
    END IF;

    v_build_number := (p_data->>'buildNumber')::INTEGER;

    SELECT id
    INTO v_draft_status_id
    FROM train_set_lifecycle_statuses
    WHERE code = 'DRAFT';

    IF v_draft_status_id IS NULL THEN
        RAISE EXCEPTION 'DRAFT lifecycle status is not configured' USING ERRCODE = '22023';
    END IF;

    INSERT INTO train_sets (
        code, build_number, name, technical_name, description, train_set_lifecycle_status_id
    ) VALUES (
        p_data->>'code',
        v_build_number,
        p_data->>'name',
        p_data->>'technicalName',
        p_data->>'description',
        v_draft_status_id
    )
    RETURNING id INTO v_train_set_id;

    -- The shape intentionally follows the CDC snapshot: a carriage contains
    -- its carriageType and optional scheme. IDs are omitted and references
    -- between repeated carriage types use the local `ref` field.
    FOR v_carriage IN SELECT value FROM jsonb_array_elements(p_data->'carriages') LOOP
        v_type := v_carriage->'carriageType';
        v_type_ref := v_type->>'ref';

        IF v_type_ref IS NULL
           OR v_type->>'code' IS NULL
           OR v_type->>'name' IS NULL
        THEN
            RAISE EXCEPTION 'Each carriageType must contain ref, code and name'
                USING ERRCODE = '22023';
        END IF;

        IF v_type_ids ? v_type_ref THEN
            v_carriage_type_id := (v_type_ids->>v_type_ref)::INTEGER;
            IF EXISTS (
                SELECT 1
                FROM carriage_types ct
                WHERE ct.id = v_carriage_type_id
                  AND (ct.code <> v_type->>'code' OR ct.name <> v_type->>'name')
            ) THEN
                RAISE EXCEPTION 'Repeated carriageType ref % has different type data', v_type_ref
                    USING ERRCODE = '22023';
            END IF;
        ELSE
            INSERT INTO carriage_types (code, name, description)
            VALUES (v_type->>'code', v_type->>'name', v_type->>'description')
            RETURNING id INTO v_carriage_type_id;

            v_type_ids := v_type_ids || jsonb_build_object(v_type_ref, v_carriage_type_id);

            -- CDC embeds the active scheme as `scheme`; the import may also
            -- provide a local `schemes` array when more than one version must
            -- be imported for a carriage type.
            IF v_type ? 'schemes' AND jsonb_typeof(v_type->'schemes') = 'array' THEN
                FOR v_scheme IN SELECT value FROM jsonb_array_elements(v_type->'schemes') LOOP
                    PERFORM inventory_import_scheme(v_carriage_type_id, v_scheme);
                END LOOP;
            ELSIF v_type ? 'scheme' AND NOT (v_type->'scheme' IS NULL OR jsonb_typeof(v_type->'scheme') = 'null') THEN
                PERFORM inventory_import_scheme(v_carriage_type_id, v_type->'scheme');
            END IF;
        END IF;

        INSERT INTO carriages (
            carriage_type_id, inventory_number, serial_number, is_active
        ) VALUES (
            v_carriage_type_id,
            v_carriage->>'inventoryNumber',
            v_carriage->>'serialNumber',
            COALESCE((v_carriage->>'isActive')::BOOLEAN, TRUE)
        )
        RETURNING id INTO v_carriage_id;

        INSERT INTO train_set_carriages (
            train_set_id, carriage_id, carriage_number, position
        ) VALUES (
            v_train_set_id,
            v_carriage_id,
            v_carriage->>'carriageNumber',
            (v_carriage->>'position')::INTEGER
        );
    END LOOP;

    SELECT * INTO v_train_set FROM train_sets WHERE id = v_train_set_id;
    RETURN v_train_set;
END;
$$;

CREATE OR REPLACE FUNCTION inventory_import_scheme(p_carriage_type_id INTEGER, p_scheme JSONB)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_scheme_id INTEGER;
    v_seat JSONB;
BEGIN
    IF p_scheme->>'code' IS NULL
       OR p_scheme->>'name' IS NULL
       OR p_scheme->>'storageKey' IS NULL
       OR p_scheme->>'version' IS NULL
    THEN
        RAISE EXCEPTION 'Each scheme must contain code, name, storageKey and version'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO carriage_type_schemes (
        carriage_type_id, code, name, storage_key, version, is_active
    ) VALUES (
        p_carriage_type_id,
        p_scheme->>'code',
        p_scheme->>'name',
        p_scheme->>'storageKey',
        (p_scheme->>'version')::INTEGER,
        COALESCE((p_scheme->>'isActive')::BOOLEAN, FALSE)
    )
    RETURNING id INTO v_scheme_id;

    IF jsonb_typeof(p_scheme->'seatPositions') = 'array' THEN
        FOR v_seat IN SELECT value FROM jsonb_array_elements(p_scheme->'seatPositions') LOOP
            INSERT INTO scheme_seat_positions (
                scheme_id, seat_number, x, y, rotation
            ) VALUES (
                v_scheme_id,
                v_seat->>'seatNumber',
                (v_seat->>'x')::DOUBLE PRECISION,
                (v_seat->>'y')::DOUBLE PRECISION,
                COALESCE((v_seat->>'rotation')::DOUBLE PRECISION, 0)
            );
        END LOOP;
    END IF;

    RETURN v_scheme_id;
END;
$$;
