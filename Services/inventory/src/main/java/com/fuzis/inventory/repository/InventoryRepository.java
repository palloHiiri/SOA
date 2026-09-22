package com.fuzis.inventory.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuzis.inventory.dto.TrainSetLifecycleResponse;
import com.fuzis.inventory.dto.TrainSetResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TrainSetResponse importTrainSet(JsonNode payload) {
        return jdbc.queryForObject(
                "SELECT * FROM inventory_import_train_set(?::jsonb)",
                (rs, rowNum) -> mapTrainSet(rs),
                payload.toString()
        );
    }

    public boolean lifecycleStatusExists(String status) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM train_set_lifecycle_statuses WHERE code = ?)",
                Boolean.class, status));
    }

    public TrainSetLifecycleResponse changeLifecycle(int trainSetId, String status) {
        return jdbc.queryForObject("""
                UPDATE train_sets ts
                SET train_set_lifecycle_status_id = status.id
                FROM train_set_lifecycle_statuses status
                WHERE ts.id = ?
                  AND status.code = ?
                RETURNING ts.id, status.code
                """, (rs, rowNum) -> new TrainSetLifecycleResponse(
                        rs.getInt("id"), rs.getString("code")
                ), trainSetId, status);
    }

    public boolean existsTrainSet(int trainSetId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM train_sets WHERE id = ?)",
                Boolean.class, trainSetId));
    }

    private static TrainSetResponse mapTrainSet(ResultSet rs) throws SQLException {
        return new TrainSetResponse(
                rs.getInt("id"),
                rs.getString("code"),
                rs.getInt("build_number"),
                rs.getString("name"),
                rs.getString("technical_name"),
                rs.getString("description"),
                rs.getInt("train_set_lifecycle_status_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
