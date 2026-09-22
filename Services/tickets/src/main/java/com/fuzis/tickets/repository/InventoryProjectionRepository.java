package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.CarriageResponse;
import com.fuzis.tickets.dto.SeatResponse;
import com.fuzis.tickets.dto.TrainSetResponse;
import com.fuzis.tickets.util.InventoryJson;
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
import java.util.Optional;

@Dependent
public class InventoryProjectionRepository {
    private final DataSource dataSource;

    @Inject
    public InventoryProjectionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<TrainSetResponse> findTrainSets(int size, long offset, Integer id, String code, String name, Integer buildNumber,
    String technicalName, List<SortPart> sorts) throws SQLException {
        String base = "WITH latest AS (SELECT DISTINCT ON (train_set_id) train_set_id, data, version FROM train_set_cdc ORDER BY train_set_id, version DESC) " +
        "SELECT train_set_id, data, version FROM latest WHERE 1=1";
        List<Object> params = new ArrayList<>();
        if (id != null) {
            base += " AND train_set_id = ?";
            params.add(id);
        }
        if (code != null) {
            base += " AND data->>'code' = ?";
            params.add(code);
        }
        if (name != null) {
            base += " AND data->>'name' = ?";
            params.add(name);
        }
        if (buildNumber != null) {
            base += " AND (data->>'buildNumber')::int = ?";
            params.add(buildNumber);
        }
        if (technicalName != null) {
            base += " AND data->>'technicalName' = ?";
            params.add(technicalName);
        }
        base += " ORDER BY " + orderBy(sorts, "train_set_id") + " LIMIT ? OFFSET ?";
        params.add(size);
        params.add(offset);
        try (
            Connection c = dataSource.getConnection();
            PreparedStatement ps = c.prepareStatement(base)
        ) {
            Sql.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<TrainSetResponse> out = new ArrayList<>();
                while (rs.next()) out.add(InventoryJson.trainSet(InventoryJson.object(rs.getString("data")), rs.getLong("version")));
                return out;
            }
        }
    }

    public long countTrainSets(Integer id, String code, String name, Integer buildNumber, String technicalName) throws SQLException {
        String base =
                "WITH latest AS (SELECT DISTINCT ON (train_set_id) train_set_id, data, version " +
                "FROM train_set_cdc ORDER BY train_set_id, version DESC) " +
                "SELECT COUNT(*) FROM latest WHERE 1=1";
        List<Object> params = new ArrayList<>();
        if (id!=null) {
            base+=" AND train_set_id=?";
            params.add(id);
        }
        if (code!=null) {
            base+=" AND data->>'code'=?";
            params.add(code);
        }
        if (name!=null) {
            base+=" AND data->>'name'=?";
            params.add(name);
        }
        if (buildNumber!=null) {
            base+=" AND (data->>'buildNumber')::int=?";
            params.add(buildNumber);
        }
        if (technicalName!=null) {
            base+=" AND data->>'technicalName'=?";
            params.add(technicalName);
        }
        try (
            Connection c = dataSource.getConnection();
            PreparedStatement ps = c.prepareStatement(base)
        ) {
            Sql.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public Optional<TrainSetSnapshot> latestTrainSet(int trainSetId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return latestTrainSet(c, trainSetId);
        }
    }
    public Optional<TrainSetSnapshot> latestTrainSet(Connection c, int trainSetId) throws SQLException {
        String sql = "SELECT train_set_id,data::text AS data,version FROM train_set_cdc WHERE train_set_id=? ORDER BY version DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, trainSetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()?Optional.of(new TrainSetSnapshot(rs.getInt(1), rs.getString(2), rs.getLong(3))):Optional.empty();
            }
        }
    }

    public boolean carriageExists(String data, String carriageNumber) {
        return InventoryJson.carriages(InventoryJson.object(data)).stream().anyMatch(c -> carriageNumber.equals(InventoryJson.string(c,"carriageNumber")));
    }

    public List<CarriageResponse> findCarriages(int trainSetId, int size, long offset, String carriageNumber, Integer id, Integer position,
    String inventoryNumber, String serialNumber, String carriageTypeCode, List<SortPart> sorts) throws SQLException {
        String sql =
                "SELECT c FROM (SELECT data FROM train_set_cdc WHERE train_set_id=? " +
                "ORDER BY version DESC LIMIT 1) latest " +
                "CROSS JOIN LATERAL jsonb_array_elements(latest.data->'carriages') c " +
                "WHERE 1=1";
        List<Object> p = new ArrayList<>();
        p.add(trainSetId);
        if (id!=null) {
            sql+=" AND (c->>'id')::int=?";
            p.add(id);
        }
        if (carriageNumber!=null) {
            sql+=" AND c->>'carriageNumber'=?";
            p.add(carriageNumber);
        }
        if (position!=null) {
            sql+=" AND (c->>'position')::int=?";
            p.add(position);
        }
        if (inventoryNumber!=null) {
            sql+=" AND c->>'inventoryNumber'=?";
            p.add(inventoryNumber);
        }
        if (serialNumber!=null) {
            sql+=" AND c->>'serialNumber'=?";
            p.add(serialNumber);
        }
        if (carriageTypeCode!=null) {
            sql+=" AND c->'carriageType'->>'code'=?";
            p.add(carriageTypeCode);
        }
        sql+=" ORDER BY "+orderBy(sorts,"(c->>'position')::int")+" LIMIT ? OFFSET ?";
        p.add(size);
        p.add(offset);
        try (
            Connection c = dataSource.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)
        ) {
            Sql.bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                List<CarriageResponse> out = new ArrayList<>();
                while (rs.next()) out.add(InventoryJson.carriage(rs.getString("c")==null?InventoryJson.object("{}"):InventoryJson.object(rs.getString("c"))));
                return out;
            }
        }
    }

    public long countCarriages(
    int trainSetId,
    String carriageNumber,
    Integer id,
    Integer position,
    String inventoryNumber,
    String serialNumber,
    String carriageTypeCode
    ) throws SQLException {
        String sql =
                "SELECT COUNT(*) FROM (SELECT data FROM train_set_cdc WHERE train_set_id=? " +
                "ORDER BY version DESC LIMIT 1) latest " +
                "CROSS JOIN LATERAL jsonb_array_elements(latest.data->'carriages') c " +
                "WHERE 1=1";
        List<Object>p = new ArrayList<>();
        p.add(trainSetId);
        if (id!=null) {
            sql+=" AND (c->>'id')::int=?";
            p.add(id);
        }
        if (carriageNumber!=null) {
            sql+=" AND c->>'carriageNumber'=?";
            p.add(carriageNumber);
        }
        if (position!=null) {
            sql+=" AND (c->>'position')::int=?";
            p.add(position);
        }
        if (inventoryNumber!=null) {
            sql+=" AND c->>'inventoryNumber'=?";
            p.add(inventoryNumber);
        }
        if (serialNumber!=null) {
            sql+=" AND c->>'serialNumber'=?";
            p.add(serialNumber);
        }
        if (carriageTypeCode!=null) {
            sql+=" AND c->'carriageType'->>'code'=?";
            p.add(carriageTypeCode);
        }
        try (
            Connection c = dataSource.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)
        ) {
            Sql.bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public List<SeatResponse> findSeats(
    int trainSetId,
    String carriageNumber,
    int size,
    long offset,
    String seatNumber,
    Double x,
    Double y,
    Double rotation,
    List<SortPart> sorts
    ) throws SQLException {
        String sql =
                "SELECT s FROM (SELECT data FROM train_set_cdc WHERE train_set_id=? " +
                "ORDER BY version DESC LIMIT 1) latest " +
                "CROSS JOIN LATERAL jsonb_array_elements(latest.data->'carriages') c " +
                "CROSS JOIN LATERAL jsonb_array_elements( " +
                "COALESCE(c->'scheme'->'seatPositions','[]'::jsonb)) s " +
                "WHERE c->>'carriageNumber'=?";
        List<Object>p = new ArrayList<>();
        p.add(trainSetId);
        p.add(carriageNumber);
        if (seatNumber!=null) {
            sql+=" AND s->>'seatNumber'=?";
            p.add(seatNumber);
        }
        if (x!=null) {
            sql+=" AND (s->>'x')::double precision=?";
            p.add(x);
        }
        if (y!=null) {
            sql+=" AND (s->>'y')::double precision=?";
            p.add(y);
        }
        if (rotation!=null) {
            sql+=" AND (s->>'rotation')::double precision=?";
            p.add(rotation);
        }
        sql+=" ORDER BY "+orderBy(sorts,"(s->>'seatNumber')")+" LIMIT ? OFFSET ?";
        p.add(size);
        p.add(offset);
        try (
            Connection c = dataSource.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)
        ) {
            Sql.bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                List<SeatResponse>out = new ArrayList<>();
                while (rs.next()) out.add(InventoryJson.seat(InventoryJson.object(rs.getString("s"))));
                return out;
            }
        }
    }

    public long countSeats(int trainSetId, String carriageNumber, String seatNumber, Double x, Double y, Double rotation) throws SQLException {
        String sql =
                "SELECT COUNT(*) FROM (SELECT data FROM train_set_cdc WHERE train_set_id=? " +
                "ORDER BY version DESC LIMIT 1) latest " +
                "CROSS JOIN LATERAL jsonb_array_elements(latest.data->'carriages') c " +
                "CROSS JOIN LATERAL jsonb_array_elements( " +
                "COALESCE(c->'scheme'->'seatPositions','[]'::jsonb)) s " +
                "WHERE c->>'carriageNumber'=?";
        List<Object>p = new ArrayList<>();
        p.add(trainSetId);
        p.add(carriageNumber);
        if (seatNumber!=null) {
            sql+=" AND s->>'seatNumber'=?";
            p.add(seatNumber);
        }
        if (x!=null) {
            sql+=" AND (s->>'x')::double precision=?";
            p.add(x);
        }
        if (y!=null) {
            sql+=" AND (s->>'y')::double precision=?";
            p.add(y);
        }
        if (rotation!=null) {
            sql+=" AND (s->>'rotation')::double precision=?";
            p.add(rotation);
        }
        try (
            Connection c = dataSource.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)
        ) {
            Sql.bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String orderBy(List<SortPart> parts, String defaultExpr) {
        return parts==null||parts.isEmpty()?defaultExpr+" ASC":parts.stream().map(s->s.expression()+" "+s.direction()).reduce((a, b)->a+", "+b).orElse(defaultExpr+" ASC");
    }
    public record TrainSetSnapshot(int trainSetId, String data, long version) {
    }
}
