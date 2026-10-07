package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.CarriageResponse;
import com.fuzis.tickets.dto.SeatResponse;
import com.fuzis.tickets.dto.TrainSetResponse;
import com.fuzis.tickets.util.InventoryJson;
import com.fuzis.tickets.util.SortParser.SortPart;

import jakarta.enterprise.context.Dependent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Dependent
public class InventoryProjectionRepository extends JpaRepository {

    public List<TrainSetResponse> findTrainSets(
            int size,
            long offset,
            Integer id,
            String code,
            String name,
            Integer buildNumber,
            String technicalName,
            List<SortPart> sorts) {
        String base =
                "WITH latest AS (SELECT DISTINCT ON (train_set_id) train_set_id, data, version FROM"
                        + " train_set_cdc ORDER BY train_set_id, version DESC) SELECT train_set_id,"
                        + " CAST(data AS text) AS data, version FROM latest WHERE 1=1";
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

        JpaRows rs = rows(base, params);
        List<TrainSetResponse> out = new ArrayList<>();
        while (rs.next())
            out.add(
                    InventoryJson.trainSet(
                            InventoryJson.object(rs.getString("data")), rs.getLong("version")));
        return out;
    }

    public long countTrainSets(
            Integer id, String code, String name, Integer buildNumber, String technicalName) {
        String base =
                "WITH latest AS (SELECT DISTINCT ON (train_set_id) train_set_id, data, version "
                        + "FROM train_set_cdc ORDER BY train_set_id, version DESC) "
                        + "SELECT COUNT(*) FROM latest WHERE 1=1";
        List<Object> params = new ArrayList<>();
        if (id != null) {
            base += " AND train_set_id=?";
            params.add(id);
        }
        if (code != null) {
            base += " AND data->>'code'=?";
            params.add(code);
        }
        if (name != null) {
            base += " AND data->>'name'=?";
            params.add(name);
        }
        if (buildNumber != null) {
            base += " AND (data->>'buildNumber')::int=?";
            params.add(buildNumber);
        }
        if (technicalName != null) {
            base += " AND data->>'technicalName'=?";
            params.add(technicalName);
        }

        JpaRows rs = rows(base, params);
        rs.next();
        return rs.getLong(1);
    }

    public Optional<TrainSetSnapshot> latestTrainSet(int trainSetId) {
        String sql =
                "SELECT train_set_id,data::text AS data,version FROM train_set_cdc WHERE"
                        + " train_set_id=? ORDER BY version DESC LIMIT 1";

        String querySql = sql;
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, trainSetId);

        JpaRows rs = rows(querySql, bindings);
        return rs.next()
                ? Optional.of(new TrainSetSnapshot(rs.getInt(1), rs.getString(2), rs.getLong(3)))
                : Optional.empty();
    }

    public boolean carriageExists(String data, String carriageNumber) {
        return InventoryJson.carriages(InventoryJson.object(data)).stream()
                .anyMatch(c -> carriageNumber.equals(InventoryJson.string(c, "carriageNumber")));
    }

    public List<CarriageResponse> findCarriages(
            int trainSetId,
            int size,
            long offset,
            String carriageNumber,
            Integer id,
            Integer position,
            String inventoryNumber,
            String serialNumber,
            String carriageTypeCode,
            List<SortPart> sorts) {
        String sql =
                "SELECT CAST(c AS text) AS c FROM (SELECT data FROM train_set_cdc WHERE"
                        + " train_set_id=? ORDER BY version DESC LIMIT 1) latest CROSS JOIN LATERAL"
                        + " jsonb_array_elements(latest.data->'carriages') c WHERE 1=1";
        List<Object> p = new ArrayList<>();
        p.add(trainSetId);
        if (id != null) {
            sql += " AND (c->>'id')::int=?";
            p.add(id);
        }
        if (carriageNumber != null) {
            sql += " AND c->>'carriageNumber'=?";
            p.add(carriageNumber);
        }
        if (position != null) {
            sql += " AND (c->>'position')::int=?";
            p.add(position);
        }
        if (inventoryNumber != null) {
            sql += " AND c->>'inventoryNumber'=?";
            p.add(inventoryNumber);
        }
        if (serialNumber != null) {
            sql += " AND c->>'serialNumber'=?";
            p.add(serialNumber);
        }
        if (carriageTypeCode != null) {
            sql += " AND c->'carriageType'->>'code'=?";
            p.add(carriageTypeCode);
        }
        sql += " ORDER BY " + orderBy(sorts, "(c->>'position')::int") + " LIMIT ? OFFSET ?";
        p.add(size);
        p.add(offset);

        JpaRows rs = rows(sql, p);
        List<CarriageResponse> out = new ArrayList<>();
        while (rs.next())
            out.add(
                    InventoryJson.carriage(
                            rs.getString("c") == null
                                    ? InventoryJson.object("{}")
                                    : InventoryJson.object(rs.getString("c"))));
        return out;
    }

    public long countCarriages(
            int trainSetId,
            String carriageNumber,
            Integer id,
            Integer position,
            String inventoryNumber,
            String serialNumber,
            String carriageTypeCode) {
        String sql =
                "SELECT COUNT(*) FROM (SELECT data FROM train_set_cdc WHERE train_set_id=? "
                        + "ORDER BY version DESC LIMIT 1) latest "
                        + "CROSS JOIN LATERAL jsonb_array_elements(latest.data->'carriages') c "
                        + "WHERE 1=1";
        List<Object> p = new ArrayList<>();
        p.add(trainSetId);
        if (id != null) {
            sql += " AND (c->>'id')::int=?";
            p.add(id);
        }
        if (carriageNumber != null) {
            sql += " AND c->>'carriageNumber'=?";
            p.add(carriageNumber);
        }
        if (position != null) {
            sql += " AND (c->>'position')::int=?";
            p.add(position);
        }
        if (inventoryNumber != null) {
            sql += " AND c->>'inventoryNumber'=?";
            p.add(inventoryNumber);
        }
        if (serialNumber != null) {
            sql += " AND c->>'serialNumber'=?";
            p.add(serialNumber);
        }
        if (carriageTypeCode != null) {
            sql += " AND c->'carriageType'->>'code'=?";
            p.add(carriageTypeCode);
        }

        JpaRows rs = rows(sql, p);
        rs.next();
        return rs.getLong(1);
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
            List<SortPart> sorts) {
        String sql =
                "SELECT CAST(s AS text) AS s FROM (SELECT data FROM train_set_cdc WHERE"
                    + " train_set_id=? ORDER BY version DESC LIMIT 1) latest CROSS JOIN LATERAL"
                    + " jsonb_array_elements(latest.data->'carriages') c CROSS JOIN LATERAL"
                    + " jsonb_array_elements( COALESCE(c->'scheme'->'seatPositions','[]'::jsonb)) s"
                    + " WHERE c->>'carriageNumber'=?";
        List<Object> p = new ArrayList<>();
        p.add(trainSetId);
        p.add(carriageNumber);
        if (seatNumber != null) {
            sql += " AND s->>'seatNumber'=?";
            p.add(seatNumber);
        }
        if (x != null) {
            sql += " AND (s->>'x')::double precision=?";
            p.add(x);
        }
        if (y != null) {
            sql += " AND (s->>'y')::double precision=?";
            p.add(y);
        }
        if (rotation != null) {
            sql += " AND (s->>'rotation')::double precision=?";
            p.add(rotation);
        }
        sql += " ORDER BY " + orderBy(sorts, "(s->>'seatNumber')") + " LIMIT ? OFFSET ?";
        p.add(size);
        p.add(offset);

        JpaRows rs = rows(sql, p);
        List<SeatResponse> out = new ArrayList<>();
        while (rs.next()) out.add(InventoryJson.seat(InventoryJson.object(rs.getString("s"))));
        return out;
    }

    public long countSeats(
            int trainSetId,
            String carriageNumber,
            String seatNumber,
            Double x,
            Double y,
            Double rotation) {
        String sql =
                "SELECT COUNT(*) FROM (SELECT data FROM train_set_cdc WHERE train_set_id=? "
                        + "ORDER BY version DESC LIMIT 1) latest "
                        + "CROSS JOIN LATERAL jsonb_array_elements(latest.data->'carriages') c "
                        + "CROSS JOIN LATERAL jsonb_array_elements( "
                        + "COALESCE(c->'scheme'->'seatPositions','[]'::jsonb)) s "
                        + "WHERE c->>'carriageNumber'=?";
        List<Object> p = new ArrayList<>();
        p.add(trainSetId);
        p.add(carriageNumber);
        if (seatNumber != null) {
            sql += " AND s->>'seatNumber'=?";
            p.add(seatNumber);
        }
        if (x != null) {
            sql += " AND (s->>'x')::double precision=?";
            p.add(x);
        }
        if (y != null) {
            sql += " AND (s->>'y')::double precision=?";
            p.add(y);
        }
        if (rotation != null) {
            sql += " AND (s->>'rotation')::double precision=?";
            p.add(rotation);
        }

        JpaRows rs = rows(sql, p);
        rs.next();
        return rs.getLong(1);
    }

    public Optional<SeatResponse> findAvailableSeat(long sourceTicketId) {
        String sql =
                """
                SELECT CAST(seat AS text) AS seat
                FROM tickets source
                JOIN venues venue ON venue.id = source.venue_id
                CROSS JOIN LATERAL (
                    SELECT data FROM train_set_cdc
                    WHERE train_set_id = venue.train_set_id
                    ORDER BY version DESC LIMIT 1
                ) snapshot
                CROSS JOIN LATERAL jsonb_array_elements(snapshot.data->'carriages') carriage
                CROSS JOIN LATERAL jsonb_array_elements(
                    COALESCE(carriage->'scheme'->'seatPositions', '[]'::jsonb)
                ) seat
                WHERE source.id = ?
                  AND carriage->>'carriageNumber' = source.carriage_number
                  AND seat->>'seatNumber' <> source.seat_number
                  AND NOT EXISTS (
                      SELECT 1 FROM tickets occupied
                      WHERE occupied.venue_id = source.venue_id
                        AND occupied.carriage_number = source.carriage_number
                        AND occupied.seat_number = seat->>'seatNumber'
                  )
                ORDER BY seat->>'seatNumber'
                LIMIT 1
                """;
        JpaRows result = rows(sql, List.of(sourceTicketId));
        return result.next()
                ? Optional.of(InventoryJson.seat(InventoryJson.object(result.getString("seat"))))
                : Optional.empty();
    }

    private static String orderBy(List<SortPart> parts, String defaultExpr) {
        return parts == null || parts.isEmpty()
                ? defaultExpr + " ASC"
                : parts.stream()
                        .map(s -> s.expression() + " " + s.direction())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(defaultExpr + " ASC");
    }

    public record TrainSetSnapshot(int trainSetId, String data, long version) {}
}
