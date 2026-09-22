package com.fuzis.ssoident.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class UserWriteRepository {
    private final DatabaseClient db;

    public UserWriteRepository(DatabaseClient db) { this.db = db; }

    public Mono<Long> createUser(UUID userId, String passwordHash, String email, String username, String firstName, String lastName) {
        return db.sql("""
            INSERT INTO users(id, status_id)
            VALUES (:userId, (SELECT id FROM user_statuses WHERE code = 'PENDING'))
            """).bind("userId", userId).fetch().rowsUpdated()
            .then(db.sql("""
                INSERT INTO user_credentials(id, user_id, credential_type_id, secret_hash)
                VALUES (:id, :userId, (SELECT id FROM credential_types WHERE code = 'PASSWORD'), :passwordHash)
                """).bind("id", UUID.randomUUID()).bind("userId", userId).bind("passwordHash", passwordHash).fetch().rowsUpdated())
            .then(insertInitialAttribute(userId, "email", email))
            .then(insertAttributeIfPresent(userId, "username", username))
            .then(insertAttributeIfPresent(userId, "first_name", firstName))
            .then(insertAttributeIfPresent(userId, "last_name", lastName))
            .then(insertInitialAttribute(userId, "email_2fa_enabled", "false"));
    }

    private Mono<Long> insertInitialAttribute(UUID userId, String code, String value) {
        return db.sql("""
            INSERT INTO user_attributes(user_id, attribute_id, value)
            VALUES (:userId, (SELECT id FROM attributes WHERE code = :code), :value)
            """).bind("userId", userId).bind("code", code).bind("value", value).fetch().rowsUpdated();
    }

    private Mono<Long> insertAttributeIfPresent(UUID userId, String code, String value) {
        return value == null || value.isBlank() ? Mono.just(0L) : insertInitialAttribute(userId, code, value);
    }

    public Mono<Long> updateStatus(UUID userId, String status) {
        return db.sql("""
            UPDATE users SET status_id = (SELECT id FROM user_statuses WHERE code = :status), updated_at = CURRENT_TIMESTAMP
            WHERE id = :userId
            """).bind("status", status).bind("userId", userId).fetch().rowsUpdated();
    }

    public Mono<UUID> createVerificationCode(UUID userId, String typeCode, String destination, String codeHash,
                                              java.time.Instant expiresAt) {
        return createVerificationCode(UUID.randomUUID(), userId, typeCode, destination, codeHash, expiresAt);
    }

    public Mono<UUID> createVerificationCode(UUID codeId, UUID userId, String typeCode, String destination, String codeHash,
                                              java.time.Instant expiresAt) {
        return db.sql("""
            INSERT INTO verification_codes(id, user_id, type_id, destination, code_hash, expires_at)
            VALUES (:id, :userId, (SELECT id FROM verification_code_types WHERE code = :typeCode), :destination, :codeHash, :expiresAt)
            """).bind("id", codeId).bind("userId", userId).bind("typeCode", typeCode)
                .bind("destination", destination).bind("codeHash", codeHash).bind("expiresAt", expiresAt)
                .fetch().rowsUpdated().thenReturn(codeId);
    }

    public Mono<Long> updatePasswordHash(UUID userId, String passwordHash) {
        return db.sql("""
            UPDATE user_credentials uc
            SET secret_hash = :passwordHash, updated_at = CURRENT_TIMESTAMP
            FROM credential_types ct
            WHERE uc.user_id = :userId
              AND uc.credential_type_id = ct.id
              AND ct.code = 'PASSWORD'
            """).bind("userId", userId).bind("passwordHash", passwordHash).fetch().rowsUpdated();
    }

    public Mono<Long> updateAttribute(UUID userId, String attributeCode, String value) {
        return db.sql("""
            UPDATE user_attributes ua
            SET value = :value
            FROM attributes a
            WHERE ua.user_id = :userId
              AND ua.attribute_id = a.id
              AND a.code = :attributeCode
            """).bind("userId", userId).bind("attributeCode", attributeCode).bind("value", value)
                .fetch().rowsUpdated();
    }

    public Mono<Long> insertIdentityCdc(UUID userId) {
        return db.sql("""
            INSERT INTO sso_ident_user_cdc (
                user_id, event_type, email, username, first_name, last_name, updated_at
            )
            SELECT :userId, 'USER_UPDATED',
                   MAX(ua.value) FILTER (WHERE a.code = 'email'),
                   MAX(ua.value) FILTER (WHERE a.code = 'username'),
                   MAX(ua.value) FILTER (WHERE a.code = 'first_name'),
                   MAX(ua.value) FILTER (WHERE a.code = 'last_name'),
                   CURRENT_TIMESTAMP
            FROM user_attributes ua
            JOIN attributes a ON a.id = ua.attribute_id
            WHERE ua.user_id = :userId
            """).bind("userId", userId).fetch().rowsUpdated();
    }

    public Mono<Long> insertAttribute(UUID userId, String attributeCode, String value) {
        return db.sql("""
            INSERT INTO user_attributes(user_id, attribute_id, value)
            VALUES (:userId, (SELECT id FROM attributes WHERE code = :attributeCode), :value)
            """).bind("userId", userId).bind("attributeCode", attributeCode).bind("value", value)
                .fetch().rowsUpdated();
    }

    public Mono<Long> upsertAttribute(UUID userId, String attributeCode, String value) {
        return updateAttribute(userId, attributeCode, value)
                .flatMap(updated -> updated > 0 ? Mono.just(updated) : insertAttribute(userId, attributeCode, value));
    }

    public Mono<VerificationRow> findActiveVerificationCode(UUID codeId) {
        return db.sql("""
            SELECT vc.id, vc.user_id, vc.code_hash, vc.destination, vc.expires_at, vc.status
            FROM verification_codes vc WHERE vc.id = :id
            """).bind("id", codeId).map((row, meta) -> new VerificationRow(
                row.get("id", UUID.class), row.get("user_id", UUID.class), row.get("code_hash", String.class),
                row.get("destination", String.class), row.get("expires_at", java.time.Instant.class), row.get("status", String.class)
            )).one();
    }

    public Mono<Long> useVerificationCode(UUID codeId) {
        return db.sql("""
            UPDATE verification_codes
            SET status = 'USED', used_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status = 'ACTIVE'
            """).bind("id", codeId).fetch().rowsUpdated();
    }

    public record VerificationRow(UUID id, UUID userId, String codeHash, String destination, java.time.Instant expiresAt, String status) {}
}
