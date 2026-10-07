package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.VenueResponse;
import com.fuzis.tickets.entity.VenueEntity;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.util.SortParser.SortPart;

import jakarta.enterprise.context.Dependent;
import jakarta.persistence.LockModeType;

import java.util.ArrayList;
import java.util.List;

@Dependent
public class VenueRepository extends JpaRepository {

    public VenueRecord findById(long id) {
        return findById(id, false);
    }

    public VenueRecord findById(long id, boolean forUpdate) {
        VenueEntity venue =
                forUpdate
                        ? entityManager.find(VenueEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                        : entityManager.find(VenueEntity.class, id);
        return venue == null ? null : new VenueRecord(venue.id, venue.name, venue.trainSetId);
    }

    public VenueRecord findForUpdate(long id) {
        return findById(id, true);
    }

    public long insert(String name, int trainSetId) {
        VenueEntity venue = new VenueEntity();
        venue.name = name;
        venue.trainSetId = trainSetId;
        entityManager.persist(venue);
        entityManager.flush();
        return venue.id;
    }

    public void update(long id, String name, int trainSetId) {
        VenueEntity venue =
                entityManager.find(VenueEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (venue == null) throw ApiException.notFound("Venue not found");
        venue.name = name;
        venue.trainSetId = trainSetId;
        entityManager.flush();
    }

    public void delete(long id) {
        VenueEntity venue =
                entityManager.find(VenueEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (venue == null) throw ApiException.notFound("Venue not found");
        entityManager.remove(venue);
        entityManager.flush();
    }

    public long count(String name, Integer trainSetId, Long id) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM venues WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, name, trainSetId, id);

        JpaRows rs = rows(sql.toString(), params);
        rs.next();
        return rs.getLong(1);
    }

    public List<VenueResponse> findPage(
            int page,
            int size,
            long offset,
            String name,
            Integer trainSetId,
            Long id,
            List<SortPart> sorts) {
        StringBuilder sql =
                new StringBuilder("SELECT id, name, train_set_id FROM venues WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, name, trainSetId, id);
        sql.append(" ORDER BY ")
                .append(
                        sorts.stream()
                                .map(s -> s.expression() + " " + s.direction())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("id ASC"));
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        JpaRows rs = rows(sql.toString(), params);
        List<VenueResponse> result = new ArrayList<>();
        while (rs.next()) result.add(mapResponse(rs));
        return result;
    }

    private static void appendFilters(
            StringBuilder sql, List<Object> params, String name, Integer trainSetId, Long id) {
        if (id != null) {
            sql.append(" AND id = ?");
            params.add(id);
        }
        if (name != null) {
            sql.append(" AND name = ?");
            params.add(name);
        }
        if (trainSetId != null) {
            sql.append(" AND train_set_id = ?");
            params.add(trainSetId);
        }
    }

    private static VenueRecord mapRecord(JpaRows rs) {
        return new VenueRecord(rs.getLong("id"), rs.getString("name"), rs.getInt("train_set_id"));
    }

    private static VenueResponse mapResponse(JpaRows rs) {
        VenueResponse r = new VenueResponse();
        r.setId(rs.getLong("id"));
        r.setName(rs.getString("name"));
        r.setTrainSetId(rs.getInt("train_set_id"));
        return r;
    }

    public record VenueRecord(long id, String name, int trainSetId) {}
}
