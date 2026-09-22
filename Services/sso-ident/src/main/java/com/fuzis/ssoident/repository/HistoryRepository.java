package com.fuzis.ssoident.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class HistoryRepository {
    private final DatabaseClient db;

    public HistoryRepository(DatabaseClient db) { this.db = db; }

    public Mono<Void> write(UUID userId, String action, UUID actorUserId, String ip, String userAgent,
                             boolean success, String metadataJson) {
        String sql = """
            SELECT write_user_sso_action_history(
                CAST(:userId AS uuid), :action, CAST(:actorUserId AS uuid), CAST(:ip AS inet),
                :userAgent, :success, CAST(:metadata AS jsonb)
            )
            """;
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("userId", userId)
                .bind("action", action)
                .bind("userAgent", userAgent == null ? "" : userAgent)
                .bind("success", success)
                .bind("metadata", metadataJson == null ? "{}" : metadataJson);
        if (actorUserId == null) spec = spec.bindNull("actorUserId", UUID.class);
        else spec = spec.bind("actorUserId", actorUserId);
        if (ip == null || ip.isBlank()) spec = spec.bindNull("ip", String.class);
        else spec = spec.bind("ip", ip);
        return spec.fetch().rowsUpdated().then();
    }
}
