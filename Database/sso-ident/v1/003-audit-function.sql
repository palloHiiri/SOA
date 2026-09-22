CREATE OR REPLACE FUNCTION write_user_sso_action_history(
    p_user_id UUID,
    p_action_code VARCHAR,
    p_actor_user_id UUID,
    p_ip INET,
    p_user_agent TEXT,
    p_success BOOLEAN,
    p_metadata JSONB
)
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
    v_history_id UUID;
    v_action_id INTEGER;
BEGIN
    SELECT id
      INTO v_action_id
      FROM sso_actions
     WHERE code = p_action_code;

    IF v_action_id IS NULL THEN
        RAISE EXCEPTION 'Unknown SSO action: %', p_action_code;
    END IF;

    INSERT INTO user_sso_action_histories (
        id,
        user_id,
        action_id,
        actor_user_id,
        created_at,
        ip,
        user_agent,
        success,
        metadata
    )
    VALUES (
        gen_random_uuid(),
        p_user_id,
        v_action_id,
        p_actor_user_id,
        CURRENT_TIMESTAMP,
        p_ip,
        p_user_agent,
        p_success,
        p_metadata
    )
    RETURNING id INTO v_history_id;

    RETURN v_history_id;
END;
$$;