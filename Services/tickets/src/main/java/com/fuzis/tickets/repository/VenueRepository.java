package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.VenueResponse;
import com.fuzis.tickets.util.Sql;
import com.fuzis.tickets.util.SortParser.SortPart;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Dependent
public class VenueRepository {
    private final DataSource dataSource;

    @Inject
    public VenueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    public VenueRecord findById(long id) throws SQLException {
        try (Connection c = dataSource.getConnection()) { return findById(c, id, false); }
    }

    public VenueRecord findById(Connection c, long id, boolean forUpdate) throws SQLException {
        String sql = "SELECT id, name, train_set_id FROM venues WHERE id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRecord(rs) : null;
            }
        }
    }

    public VenueRecord findForUpdate(Connection c, long id) throws SQLException { return findById(c, id, true); }

    public long insert(Connection c, String name, int trainSetId) throws SQLException {
        String sql = "INSERT INTO venues (name, train_set_id) VALUES (?, ?) RETURNING id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, trainSetId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public void update(Connection c, long id, String name, int trainSetId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE venues SET name = ?, train_set_id = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, trainSetId);
            ps.setLong(3, id);
            if (ps.executeUpdate() != 1) throw new SQLException("Venue disappeared during update");
        }
    }

    public void delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM venues WHERE id = ?")) {
            ps.setLong(1, id);
            if (ps.executeUpdate() != 1) throw new SQLException("Venue disappeared during delete");
        }
    }

    public long count(String name, Integer trainSetId, Long id) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return count(c, name, trainSetId, id);
        }
    }

    public long count(Connection c, String name, Integer trainSetId, Long id) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM venues WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, name, trainSetId, id);
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            Sql.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    public List<VenueResponse> findPage(int page, int size, long offset, String name, Integer trainSetId, Long id,
                                        List<SortPart> sorts) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, name, train_set_id FROM venues WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, name, trainSetId, id);
        sql.append(" ORDER BY ").append(sorts.stream().map(s -> s.expression()+" "+s.direction()).reduce((a,b)->a+", "+b).orElse("id ASC"));
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size); params.add(offset);
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            Sql.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<VenueResponse> result = new ArrayList<>();
                while (rs.next()) result.add(mapResponse(rs));
                return result;
            }
        }
    }

    private static void appendFilters(StringBuilder sql, List<Object> params, String name, Integer trainSetId, Long id) {
        if (id != null) { sql.append(" AND id = ?"); params.add(id); }
        if (name != null) { sql.append(" AND name = ?"); params.add(name); }
        if (trainSetId != null) { sql.append(" AND train_set_id = ?"); params.add(trainSetId); }
    }

    private static VenueRecord mapRecord(ResultSet rs) throws SQLException {
        return new VenueRecord(rs.getLong("id"), rs.getString("name"), rs.getInt("train_set_id"));
    }

    private static VenueResponse mapResponse(ResultSet rs) throws SQLException {
        VenueResponse r = new VenueResponse();
        r.setId(rs.getLong("id")); r.setName(rs.getString("name")); r.setTrainSetId(rs.getInt("train_set_id"));
        return r;
    }

    public record VenueRecord(long id, String name, int trainSetId) { }
}
