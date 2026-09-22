package com.fuzis.ssoident.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.util.UUID;

@Repository
public class SessionRepository {
    private final DatabaseClient db;

    public SessionRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> create(UUID id, UUID userId, String tokenHash, java.time.Instant expiresAt,
    String ip, String userAgent) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
            INSERT INTO user_sessions(id, user_id, session_token_hash, expires_at, last_seen_at, ip, user_agent)
            VALUES (:id, :userId, :tokenHash, :expiresAt, CURRENT_TIMESTAMP, CAST(:ip AS inet), :userAgent)
            """).bind("id", id).bind("userId", userId).bind("tokenHash", tokenHash)
        .bind("expiresAt", expiresAt)
        .bind("userAgent", userAgent == null ? "" : userAgent);
        if (ip == null || ip.isBlank()) spec = spec.bindNull("ip", String.class);
        else spec = spec.bind("ip", ip);
        return spec.fetch().rowsUpdated().then();
    }

    public Mono<Long> revoke(UUID sessionId, UUID userId) {
        return db.sql("""
            UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP, last_seen_at = CURRENT_TIMESTAMP
            WHERE id = :sessionId AND user_id = :userId AND revoked_at IS NULL
            """).bind("sessionId", sessionId).bind("userId", userId).fetch().rowsUpdated();
    }

    public Mono<Void> touch(UUID sessionId) {
        return db.sql("UPDATE user_sessions SET last_seen_at = CURRENT_TIMESTAMP WHERE id = :id")
        .bind("id", sessionId).fetch().rowsUpdated().then();
    }
}
