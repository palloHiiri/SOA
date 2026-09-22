package com.fuzis.clients.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class IdentityEventConsumer {
    private final DatabaseClient db;
    private final ObjectMapper mapper;
    private final TransactionalOperator tx;

    public IdentityEventConsumer(DatabaseClient db, ObjectMapper mapper, TransactionalOperator tx) {
        this.db = db;
        this.mapper = mapper;
        this.tx = tx;
    }

    @KafkaListener(topics = "${kafka.topics.user-registration}")
    public void registration(String body) throws Exception {
        JsonNode e = mapper.readTree(body);
        UUID userId = UUID.fromString(requiredText(e, "userId", "user_id"));
        String username = nullableText(e, "username");
        String email = nullableText(e, "email");
        String firstName = nullableText(e, "firstName", "first_name");
        String lastName = nullableText(e, "lastName", "last_name");
        long version = e.path("eventVersion").asLong(e.path("event_version").asLong(0));

        tx.transactional(
                db.sql("""
                    INSERT INTO clients(id, user_id, email, first_name, last_name, username, identity_version)
                    VALUES (:id, :userId, :email, :firstName, :lastName, :username, :version)
                    ON CONFLICT (user_id) DO NOTHING
                    """).bind("id", UUID.randomUUID()).bind("userId", userId)
                        .bind("email", requiredValue(email, "email"))
                        .bind("firstName", firstName == null ? "" : firstName)
                        .bind("lastName", lastName == null ? "" : lastName)
                        .bind("username", username == null ? "" : username)
                        .bind("version", version)
                        .fetch().rowsUpdated()
                        .then(findClientId(userId))
                        .flatMap(clientId -> ensureChildren(clientId))
                        .then()
        ).block();
    }

    @KafkaListener(topics = "${kafka.topics.user-updates}")
    public void update(String body) throws Exception {
        JsonNode e = mapper.readTree(body);
        UUID userId = UUID.fromString(requiredText(e, "userId", "user_id"));
        long version = e.path("eventVersion").asLong(e.path("event_version").asLong(0));
        String email = nullableText(e, "email");
        String username = nullableText(e, "username");
        String firstName = nullableText(e, "first_name", "firstName");
        String lastName = nullableText(e, "last_name", "lastName");

        tx.transactional(
                db.sql("""
                    INSERT INTO clients(id, user_id, email, first_name, last_name, username, identity_version)
                    VALUES (:id, :userId, :email, :firstName, :lastName, :username, :version)
                    ON CONFLICT (user_id) DO NOTHING
                    """).bind("id", UUID.randomUUID()).bind("userId", userId)
                        .bind("email", requiredValue(email, "email"))
                        .bind("firstName", firstName == null ? "" : firstName)
                        .bind("lastName", lastName == null ? "" : lastName)
                        .bind("username", username == null ? "" : username)
                        .bind("version", version)
                        .fetch().rowsUpdated()
                        .then(findClientId(userId))
                        .flatMap(clientId -> db.sql("""
                            UPDATE clients SET
                                email = :email,
                                username = :username,
                                first_name = :firstName,
                                last_name = :lastName,
                                identity_version = :version,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = :clientId AND identity_version < :version
                            """).bind("email", requiredValue(email, "email"))
                                .bind("username", username == null ? "" : username)
                                .bind("firstName", firstName == null ? "" : firstName)
                                .bind("lastName", lastName == null ? "" : lastName)
                                .bind("version", version).bind("clientId", clientId)
                                .fetch().rowsUpdated()
                                .then(ensureChildren(clientId))
                        )
                        .then()
        ).block();
    }

    private Mono<UUID> findClientId(UUID userId) {
        return db.sql("SELECT id FROM clients WHERE user_id = :userId")
                .bind("userId", userId)
                .map((row, meta) -> row.get("id", UUID.class)).one();
    }

    private Mono<Void> ensureChildren(UUID clientId) {
        return db.sql("""
                INSERT INTO client_profiles(id, client_id, phone_number, extra_fields)
                VALUES (:id, :clientId, NULL, '{}'::jsonb)
                ON CONFLICT (client_id) DO NOTHING
                """).bind("id", UUID.randomUUID()).bind("clientId", clientId).fetch().rowsUpdated()
                .then(db.sql("""
                INSERT INTO client_attributes(id, client_id, attributes)
                VALUES (:id, :clientId, '{}'::jsonb)
                ON CONFLICT (client_id) DO NOTHING
                """).bind("id", UUID.randomUUID()).bind("clientId", clientId).fetch().rowsUpdated().then());
    }

    private static String requiredText(JsonNode node, String... names) {
        String value = nullableText(node, names);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identity event is missing " + names[0]);
        return value;
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identity event is missing " + name);
        return value;
    }

    private static String nullableText(JsonNode node, String... names) {
        for (String n : names) if (node.has(n) && !node.get(n).isNull()) return node.get(n).asText();
        return null;
    }
}
