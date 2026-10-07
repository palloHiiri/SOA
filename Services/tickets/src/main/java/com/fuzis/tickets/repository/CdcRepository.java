package com.fuzis.tickets.repository;

import jakarta.enterprise.context.Dependent;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Dependent
public class CdcRepository extends JpaRepository {

    @Transactional(TxType.REQUIRES_NEW)
    public void insertSnapshot(int trainSetId, String data, long version) {
        String sql =
                "INSERT INTO train_set_cdc (train_set_id, data, version) VALUES (?, ?::jsonb, ?) ON"
                        + " CONFLICT DO NOTHING";

        String querySql = sql;
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, trainSetId);
        bindings.put(2, data);
        bindings.put(3, version);
        execute(querySql, bindings);
    }

    public Optional<CdcSnapshot> findLatest(int trainSetId) {
        String sql =
                "SELECT train_set_id, data::text AS data, version FROM train_set_cdc WHERE"
                        + " train_set_id = ? ORDER BY version DESC LIMIT 1";

        String querySql = sql;
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, trainSetId);

        JpaRows rs = rows(querySql, bindings);
        return rs.next()
                ? Optional.of(
                        new CdcSnapshot(
                                rs.getInt("train_set_id"),
                                rs.getString("data"),
                                rs.getLong("version")))
                : Optional.empty();
    }

    public record CdcSnapshot(int trainSetId, String data, long version) {}
}
