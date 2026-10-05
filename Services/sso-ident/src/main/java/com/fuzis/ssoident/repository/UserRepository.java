package com.fuzis.ssoident.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class UserRepository {
    private final DatabaseClient db;

    public UserRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Boolean> existsByLogin(String login) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM user_attributes ua
                JOIN attributes a ON a.id = ua.attribute_id
                WHERE a.code IN ('email', 'username')
                  AND LOWER(ua.value) = LOWER(:login)
            ) AS exists
            """;
        return db.sql(sql).bind("login", login).map((row, meta) -> row.get("exists", Boolean.class)).one();
    }

    /**
     * Authentication lookup: only ACTIVE users are eligible to log in.
     */
    public Mono<UUID> findUserIdByLogin(String login) {
        String sql = """
            SELECT ua.user_id
            FROM user_attributes ua
            JOIN attributes a ON a.id = ua.attribute_id
            JOIN users u ON u.id = ua.user_id
            JOIN user_statuses us ON us.id = u.status_id
            WHERE a.code IN ('email', 'username')
              AND LOWER(ua.value) = LOWER(:login)
              AND us.code = 'ACTIVE'
            LIMIT 1
            """;
        return db.sql(sql).bind("login", login).map((row, meta) -> row.get("user_id", UUID.class)).one();
    }

    /**
     * Registration recovery lookup: deliberately includes PENDING users.
     */
    public Mono<RegistrationData> findRegistrationByLogin(String login) {
        String sql = """
            SELECT u.id AS user_id,
                   us.code AS status,
                   MAX(ua.value) FILTER (WHERE a.code = 'email') AS email,
                   MAX(ua.value) FILTER (WHERE a.code = 'username') AS username,
                   MAX(ua.value) FILTER (WHERE a.code = 'first_name') AS first_name,
                   MAX(ua.value) FILTER (WHERE a.code = 'last_name') AS last_name
            FROM users u
            JOIN user_statuses us ON us.id = u.status_id
            JOIN user_attributes ua ON ua.user_id = u.id
            JOIN attributes a ON a.id = ua.attribute_id
            WHERE EXISTS (
                SELECT 1
                FROM user_attributes login_ua
                JOIN attributes login_a ON login_a.id = login_ua.attribute_id
                WHERE login_ua.user_id = u.id
                  AND login_a.code IN ('email', 'username')
                  AND LOWER(login_ua.value) = LOWER(:login)
            )
            GROUP BY u.id, us.code
            LIMIT 1
            """;
        return db.sql(sql)
        .bind("login", login)
        .map((row, meta) -> new RegistrationData(
            row.get("user_id", UUID.class),
            row.get("status", String.class),
            row.get("email", String.class),
            row.get("username", String.class),
            row.get("first_name", String.class),
            row.get("last_name", String.class)
        ))
        .one();
    }

    public Mono<UUID> findActiveUserIdByEmail(String email) {
        String sql = """
            SELECT ua.user_id
            FROM user_attributes ua
            JOIN attributes a ON a.id = ua.attribute_id
            JOIN users u ON u.id = ua.user_id
            JOIN user_statuses us ON us.id = u.status_id
            WHERE a.code = 'email'
              AND LOWER(ua.value) = LOWER(:email)
              AND us.code = 'ACTIVE'
            LIMIT 1
            """;
        return db.sql(sql).bind("email", email).map((row, meta) -> row.get("user_id", UUID.class)).one();
    }

    public Mono<Boolean> existsAttributeValueForAnotherUser(UUID userId, String attributeCode, String value) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM user_attributes ua
                JOIN attributes a ON a.id = ua.attribute_id
                WHERE a.code = :attributeCode
                  AND LOWER(ua.value) = LOWER(:value)
                  AND ua.user_id <> :userId
            ) AS exists
            """;
        return db.sql(sql)
        .bind("userId", userId)
        .bind("attributeCode", attributeCode)
        .bind("value", value)
        .map((row, meta) -> row.get("exists", Boolean.class))
        .one();
    }

    public Mono<String> findStatus(UUID userId) {
        return db.sql("""
            SELECT us.code FROM users u JOIN user_statuses us ON us.id = u.status_id WHERE u.id = :userId
            """).bind("userId", userId).map((row, meta) -> row.get("code", String.class)).one();
    }

    public Mono<String> findPasswordHash(UUID userId) {
        return db.sql("""
            SELECT uc.secret_hash
            FROM user_credentials uc
            JOIN credential_types ct ON ct.id = uc.credential_type_id
            WHERE uc.user_id = :userId AND ct.code = 'PASSWORD'
            LIMIT 1
            """)
        .bind("userId", userId)
        .map((row, meta) -> row.get("secret_hash", String.class)).one();
    }

    public Mono<Boolean> isEmail2faEnabled(UUID userId) {
        return db.sql("""
            SELECT COALESCE((SELECT ua.value::boolean
               FROM user_attributes ua JOIN attributes a ON a.id = ua.attribute_id
               WHERE ua.user_id = :userId AND a.code = 'email_2fa_enabled'), false) AS enabled
            """)
        .bind("userId", userId)
        .map((row, meta) -> row.get("enabled", Boolean.class)).one();
    }

    public Mono<String> findAttribute(UUID userId, String code) {
        return db.sql("""
            SELECT ua.value
            FROM user_attributes ua JOIN attributes a ON a.id = ua.attribute_id
            WHERE ua.user_id = :userId AND a.code = :code
            """).bind("userId", userId).bind("code", code)
        .map((row, meta) -> row.get("value", String.class)).one();
    }

    public Mono<java.util.Map<String, String>> findAttributes(UUID userId) {
        return db.sql("""
            SELECT a.code, ua.value
            FROM user_attributes ua JOIN attributes a ON a.id = ua.attribute_id
            WHERE ua.user_id = :userId
            """).bind("userId", userId)
        .map((row, meta) -> java.util.Map.entry(row.get("code", String.class), row.get("value", String.class)))
        .all().collectMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue);
    }

    public record RegistrationData(
        UUID userId,
        String status,
        String email,
        String username,
        String firstName,
        String lastName
    ) {}
}
