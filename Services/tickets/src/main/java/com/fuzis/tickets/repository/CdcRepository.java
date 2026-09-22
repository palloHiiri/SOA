package com.fuzis.tickets.repository;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Dependent
public class CdcRepository {
    private final DataSource dataSource;

    @Inject
    public CdcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertSnapshot(int trainSetId, String data, long version) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            insertSnapshot(c, trainSetId, data, version);
        }
    }

    public void insertSnapshot(Connection c, int trainSetId, String data, long version) throws SQLException {
        String sql = "INSERT INTO train_set_cdc (train_set_id, data, version) VALUES (?, ?::jsonb, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, trainSetId);
            ps.setString(2, data);
            ps.setLong(3, version);
            ps.executeUpdate();
        }
    }

    public Optional<CdcSnapshot> findLatest(int trainSetId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return findLatest(c, trainSetId);
        }
    }

    public Optional<CdcSnapshot> findLatest(Connection c, int trainSetId) throws SQLException {
        String sql = "SELECT train_set_id, data::text AS data, version FROM train_set_cdc WHERE train_set_id = ? ORDER BY version DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, trainSetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new CdcSnapshot(rs.getInt("train_set_id"), rs.getString("data"), rs.getLong("version"))) : Optional.empty();
            }
        }
    }

    public record CdcSnapshot(int trainSetId, String data, long version) {
    }
}
