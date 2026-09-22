--liquibase formatted sql

--changeset fuzis:v1-002
INSERT INTO user_statuses (code, name) VALUES
('ACTIVE', 'Active'),
('BLOCKED', 'Blocked'),
('DISABLED', 'Disabled'),
('PENDING', 'Pending');

INSERT INTO credential_types (code, name) VALUES
('PASSWORD', 'Password'),
('TOTP', 'TOTP'),
('OAUTH', 'OAuth');

INSERT INTO attributes (code, name, value_type, is_required, is_unique) VALUES
('email', 'Email', 'STRING', TRUE, TRUE),
('username', 'Username', 'STRING', FALSE, TRUE),
('first_name', 'First name', 'STRING', FALSE, FALSE),
('last_name', 'Last name', 'STRING', FALSE, FALSE),
('email_2fa_enabled', 'Email 2FA enabled', 'BOOLEAN', TRUE, FALSE);

INSERT INTO verification_code_purposes (code, name) VALUES
('MFA', 'Multi-factor authentication'),
('PASSWORD_RESET', 'Password reset');

INSERT INTO verification_code_channels (code, name) VALUES
('EMAIL', 'Email');

INSERT INTO verification_code_types (code, name, purpose_id, channel_id, ttl_seconds, max_attempts) VALUES
('EMAIL_2FA', 'Email 2FA code',
 (SELECT id FROM verification_code_purposes WHERE code = 'MFA'),
 (SELECT id FROM verification_code_channels WHERE code = 'EMAIL'),
 300, 5),
('PASSWORD_RESET', 'Password reset code',
 (SELECT id FROM verification_code_purposes WHERE code = 'PASSWORD_RESET'),
 (SELECT id FROM verification_code_channels WHERE code = 'EMAIL'),
 900, 5);

INSERT INTO sso_actions (code, name, description) VALUES
('LOGIN', 'Login', 'Successful login'),
('LOGOUT', 'Logout', 'Logout'),
('LOGIN_FAILED', 'Login failed', 'Failed login attempt'),
('REGISTER', 'Register', 'User registration'),
('CHANGE_PASSWORD', 'Change password', 'Password changed'),
('CHANGE_EMAIL', 'Change email', 'Email changed'),
('VERIFY_EMAIL', 'Verify email', 'Email verified'),
('REQUEST_PASSWORD_RESET', 'Request password reset', 'Password reset requested'),
('CONFIRM_PASSWORD_RESET', 'Confirm password reset', 'Password reset completed'),
('ENABLE_2FA', 'Enable 2FA', 'Two-factor authentication enabled'),
('DISABLE_2FA', 'Disable 2FA', 'Two-factor authentication disabled'),
('CREATE_SESSION', 'Create session', 'Session created'),
('REVOKE_SESSION', 'Revoke session', 'Session revoked'),
('BLOCK_USER', 'Block user', 'User blocked'),
('UNBLOCK_USER', 'Unblock user', 'User unblocked');
