package com.fuzis.clients.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuzis.clients.dto.ClientProfileResponse;
import com.fuzis.clients.dto.ClientProfileUpdateRequest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class ClientRepository {
    private final DatabaseClient db;
    private final ObjectMapper mapper;

    public ClientRepository(DatabaseClient db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    public Mono<UUID> findClientIdByUserId(UUID userId) {
        return db.sql("SELECT id FROM clients WHERE user_id = :userId")
                .bind("userId", userId)
                .map((row, meta) -> row.get("id", UUID.class)).one();
    }

    public Mono<Long> updateProfile(UUID userId, ClientProfileUpdateRequest request) {
        String extraFields = request.extraFields() == null ? "{}" : request.extraFields().toString();
        return db.sql("""
            UPDATE client_profiles cp
            SET phone_number = NULLIF(:phoneNumber, ''),
                extra_fields = CAST(:extraFields AS jsonb),
                updated_at = CURRENT_TIMESTAMP
            FROM clients c
            WHERE cp.client_id = c.id
              AND c.user_id = :userId
            """)
                .bind("userId", userId)
                .bind("phoneNumber", request.phoneNumber() == null ? "" : request.phoneNumber().trim())
                .bind("extraFields", extraFields)
                .fetch().rowsUpdated();
    }

    public Mono<ClientProfileResponse> findProfileByUserId(UUID userId) {
        return db.sql("""
            SELECT c.id AS client_id, c.user_id, c.email, c.first_name, c.last_name, c.username,
                   cp.phone_number, cp.extra_fields::text AS extra_fields,
                   GREATEST(c.updated_at, cp.updated_at) AS updated_at
            FROM clients c
            JOIN client_profiles cp ON cp.client_id = c.id
            WHERE c.user_id = :userId
            """).bind("userId", userId)
                .map((row, meta) -> {
                    String extra = row.get("extra_fields", String.class);
                    JsonNode json;
                    try { json = mapper.readTree(extra == null ? "{}" : extra); }
                    catch (Exception e) { throw new IllegalStateException("Invalid extra_fields JSON", e); }
                    return new ClientProfileResponse(
                            row.get("client_id", UUID.class),
                            row.get("user_id", UUID.class),
                            row.get("email", String.class),
                            row.get("first_name", String.class),
                            row.get("last_name", String.class),
                            row.get("username", String.class),
                            row.get("phone_number", String.class),
                            json,
                            row.get("updated_at", OffsetDateTime.class)
                    );
                }).one();
    }
}
