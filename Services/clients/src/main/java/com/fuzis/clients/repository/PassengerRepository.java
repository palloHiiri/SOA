package com.fuzis.clients.repository;

import com.fuzis.clients.dto.PassengerCreateRequest;
import com.fuzis.clients.dto.PassengerResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class PassengerRepository {
    private final DatabaseClient db;

    public PassengerRepository(DatabaseClient db) { this.db = db; }

    private static String normalizedMiddleName(String value) { return value == null ? "" : value.trim(); }

    public Flux<PassengerResponse> findAllByClientId(UUID clientId) {
        return baseQuery("WHERE p.client_id = :clientId ORDER BY p.last_name, p.first_name, p.id")
                .bind("clientId", clientId).map(this::map).all();
    }

    public Mono<PassengerResponse> findByIdAndClientId(UUID id, UUID clientId) {
        return baseQuery("WHERE p.id = :id AND p.client_id = :clientId")
                .bind("id", id).bind("clientId", clientId).map(this::map).one();
    }

    public Mono<UUID> create(UUID clientId, PassengerCreateRequest r) {
        UUID id = UUID.randomUUID();
        return db.sql("""
            INSERT INTO client_passengers(
                id, client_id, first_name, middle_name, last_name, document_type_id,
                document_series_number, document_number, birth_date, email, phone_number
            ) VALUES (:id, :clientId, :firstName, :middleName, :lastName, :documentTypeId,
                      :seriesNumber, :documentNumber, :birthDate, :email, NULLIF(:phoneNumber, ''))
            """)
                .bind("id", id).bind("clientId", clientId)
                .bind("firstName", r.firstName().trim())
                .bind("middleName", normalizedMiddleName(r.middleName()))
                .bind("lastName", r.lastName().trim())
                .bind("documentTypeId", r.documentTypeId())
                .bind("seriesNumber", r.documentSeriesNumber().trim())
                .bind("documentNumber", r.documentNumber().trim())
                .bind("birthDate", r.birthDate())
                .bind("email", r.email().trim().toLowerCase(java.util.Locale.ROOT))
                .bind("phoneNumber", r.phoneNumber() == null || r.phoneNumber().isBlank() ? "" : r.phoneNumber().trim())
                .fetch().rowsUpdated().thenReturn(id);
    }

    public Mono<Long> update(UUID id, UUID clientId, PassengerCreateRequest r) {
        return db.sql("""
            UPDATE client_passengers SET
                first_name = :firstName, middle_name = :middleName, last_name = :lastName,
                document_type_id = :documentTypeId, document_series_number = :seriesNumber,
                document_number = :documentNumber, birth_date = :birthDate, email = :email,
                phone_number = NULLIF(:phoneNumber, ''), updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND client_id = :clientId
            """).bind("id", id).bind("clientId", clientId)
                .bind("firstName", r.firstName().trim())
                .bind("middleName", normalizedMiddleName(r.middleName()))
                .bind("lastName", r.lastName().trim())
                .bind("documentTypeId", r.documentTypeId())
                .bind("seriesNumber", r.documentSeriesNumber().trim())
                .bind("documentNumber", r.documentNumber().trim())
                .bind("birthDate", r.birthDate())
                .bind("email", r.email().trim().toLowerCase(java.util.Locale.ROOT))
                .bind("phoneNumber", r.phoneNumber() == null || r.phoneNumber().isBlank() ? "" : r.phoneNumber().trim())
                .fetch().rowsUpdated();
    }

    public Mono<Long> delete(UUID id, UUID clientId) {
        return db.sql("DELETE FROM client_passengers WHERE id = :id AND client_id = :clientId")
                .bind("id", id).bind("clientId", clientId).fetch().rowsUpdated();
    }

    private DatabaseClient.GenericExecuteSpec baseQuery(String suffix) {
        return db.sql("""
            SELECT p.id, p.client_id, p.first_name, p.middle_name, p.last_name,
                   p.document_type_id, dt.code AS document_type_code, dt.name AS document_type_name,
                   p.document_series_number, p.document_number, p.birth_date, p.email, p.phone_number
            FROM client_passengers p
            JOIN document_types dt ON dt.id = p.document_type_id
            """ + suffix);
    }

    private PassengerResponse map(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return new PassengerResponse(
                row.get("id", UUID.class), row.get("client_id", UUID.class),
                row.get("first_name", String.class), row.get("middle_name", String.class),
                row.get("last_name", String.class), row.get("document_type_id", Integer.class),
                row.get("document_type_code", String.class), row.get("document_type_name", String.class),
                row.get("document_series_number", String.class), row.get("document_number", String.class),
                row.get("birth_date", java.time.LocalDate.class), row.get("email", String.class),
                row.get("phone_number", String.class)
        );
    }
}
