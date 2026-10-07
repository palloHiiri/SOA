package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.TicketResponse;
import com.fuzis.tickets.dto.TicketType;
import com.fuzis.tickets.dto.VenueReference;
import com.fuzis.tickets.entity.TicketEntity;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.util.SortParser.SortPart;

import jakarta.enterprise.context.Dependent;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Dependent
public class TicketRepository extends JpaRepository {

    private static final String LATEST_PRICE =
            "LEFT JOIN LATERAL (SELECT ph.base_price, ph.discount "
                    + "FROM price_histories ph WHERE ph.ticket_id = t.id "
                    + "ORDER BY ph.changed_date DESC, ph.id DESC LIMIT 1) ph ON TRUE";
    private static final String BASE_SELECT =
            "SELECT t.id, t.name, t.creation_date, t.venue_id, t.carriage_number, "
                    + "t.seat_number, t.refundable, t.type, v.name AS venue_name, "
                    + "v.train_set_id, ph.base_price, ph.discount "
                    + "FROM tickets t JOIN venues v ON v.id=t.venue_id "
                    + LATEST_PRICE;

    public long insert(
            String name,
            long venueId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type) {
        TicketEntity ticket = new TicketEntity();
        ticket.name = name;
        ticket.venueId = venueId;
        ticket.carriageNumber = carriageNumber;
        ticket.seatNumber = seatNumber;
        ticket.refundable = refundable;
        ticket.type = type;
        entityManager.persist(ticket);
        entityManager.flush();
        return ticket.id;
    }

    public TicketResponse findById(long id) {

        String querySql = BASE_SELECT + " WHERE t.id=?";
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, id);

        JpaRows rs = rows(querySql, bindings);
        return rs.next() ? map(rs) : null;
    }

    public void update(
            long id,
            String name,
            long venueId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type) {
        TicketEntity ticket =
                entityManager.find(TicketEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (ticket == null) throw ApiException.notFound("Ticket not found");
        ticket.name = name;
        ticket.venueId = venueId;
        ticket.carriageNumber = carriageNumber;
        ticket.seatNumber = seatNumber;
        ticket.refundable = refundable;
        ticket.type = type;
        entityManager.flush();
    }

    public void delete(long id) {
        TicketEntity ticket =
                entityManager.find(TicketEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (ticket == null) throw ApiException.notFound("Ticket not found");
        entityManager.remove(ticket);
        entityManager.flush();
    }

    public boolean existsForUpdate(long id) {
        return entityManager.find(TicketEntity.class, id, LockModeType.PESSIMISTIC_WRITE) != null;
    }

    public long countByVenue(long venueId) {

        String querySql = "SELECT COUNT(*) FROM tickets WHERE venue_id=?";
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, venueId);

        JpaRows rs = rows(querySql, bindings);
        rs.next();
        return rs.getLong(1);
    }

    public long count(
            Long id,
            String name,
            LocalDate creationDate,
            Long venueId,
            Integer trainSetId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type,
            BigDecimal basePrice,
            Integer discount) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT COUNT(*) FROM tickets t JOIN venues v ON v.id=t.venue_id "
                                + LATEST_PRICE
                                + " WHERE 1=1");
        List<Object> p = new ArrayList<>();
        appendFilters(
                sql,
                p,
                id,
                name,
                creationDate,
                venueId,
                trainSetId,
                carriageNumber,
                seatNumber,
                refundable,
                type,
                basePrice,
                discount);

        JpaRows rs = rows(sql.toString(), p);
        rs.next();
        return rs.getLong(1);
    }

    public List<TicketResponse> findPage(
            int size,
            long offset,
            Long id,
            String name,
            LocalDate creationDate,
            Long venueId,
            Integer trainSetId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type,
            BigDecimal basePrice,
            Integer discount,
            List<SortPart> sorts) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> p = new ArrayList<>();
        appendFilters(
                sql,
                p,
                id,
                name,
                creationDate,
                venueId,
                trainSetId,
                carriageNumber,
                seatNumber,
                refundable,
                type,
                basePrice,
                discount);
        sql.append(" ORDER BY ")
                .append(
                        sorts.stream()
                                .map(s -> s.expression() + " " + s.direction())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("t.id ASC"));
        sql.append(" LIMIT ? OFFSET ?");
        p.add(size);
        p.add(offset);

        JpaRows rs = rows(sql.toString(), p);
        List<TicketResponse> out = new ArrayList<>();
        while (rs.next()) out.add(map(rs));
        return out;
    }

    public long countSearch(String expression) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT COUNT(*) FROM tickets t JOIN venues v ON v.id=t.venue_id "
                                + LATEST_PRICE
                                + " WHERE "
                                + searchPredicate());
        List<Object> p = new ArrayList<>();
        bindSearchPattern(p, expression);

        JpaRows rs = rows(sql.toString(), p);
        rs.next();
        return rs.getLong(1);
    }

    public List<TicketResponse> findSearch(
            int size, long offset, String expression, List<SortPart> sorts) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE " + searchPredicate());
        List<Object> p = new ArrayList<>();
        bindSearchPattern(p, expression);
        sql.append(" ORDER BY ")
                .append(
                        sorts.stream()
                                .map(s -> s.expression() + " " + s.direction())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("t.id ASC"));
        sql.append(" LIMIT ? OFFSET ?");
        p.add(size);
        p.add(offset);

        JpaRows rs = rows(sql.toString(), p);
        List<TicketResponse> out = new ArrayList<>();
        while (rs.next()) out.add(map(rs));
        return out;
    }

    private static String searchPredicate() {
        return "CAST(t.id AS text) ILIKE ?"
                + " OR COALESCE(t.name, '') ILIKE ?"
                + " OR CAST(t.creation_date AS text) ILIKE ?"
                + " OR CAST(t.venue_id AS text) ILIKE ?"
                + " OR COALESCE(v.name, '') ILIKE ?"
                + " OR CAST(v.train_set_id AS text) ILIKE ?"
                + " OR COALESCE(t.carriage_number, '') ILIKE ?"
                + " OR COALESCE(t.seat_number, '') ILIKE ?"
                + " OR COALESCE(t.refundable::text, '') ILIKE ?"
                + " OR COALESCE(t.type::text, '') ILIKE ?"
                + " OR COALESCE(ph.base_price::text, '') ILIKE ?"
                + " OR COALESCE(ph.discount::text, '') ILIKE ?";
    }

    private static void bindSearchPattern(List<Object> parameters, String expression) {
        String pattern = "%" + (expression == null ? "" : expression) + "%";
        for (int i = 0; i < 12; i++) {
            parameters.add(pattern);
        }
    }

    public BigDecimal averageCurrentDiscount() {
        String sql =
                "SELECT AVG(ph.discount) FROM tickets t "
                        + "LEFT JOIN LATERAL (SELECT discount FROM price_histories ph "
                        + "WHERE ph.ticket_id=t.id ORDER BY ph.changed_date DESC, "
                        + "ph.id DESC LIMIT 1) ph ON TRUE";

        JpaRows rs = rows(sql, Map.of());
        rs.next();
        return rs.getBigDecimal(1);
    }

    public long countTickets() {

        JpaRows rs = rows("SELECT COUNT(*) FROM tickets", Map.of());
        rs.next();
        return rs.getLong(1);
    }

    public TicketResponse findLatest() {
        String sql = BASE_SELECT + " ORDER BY t.creation_date DESC, t.id DESC LIMIT 1";

        JpaRows rs = rows(sql, Map.of());
        return rs.next() ? map(rs) : null;
    }

    public long countBelowDiscount(
            int threshold,
            Long venueId,
            Integer trainSetId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT COUNT(*) FROM tickets t JOIN venues v ON v.id=t.venue_id "
                                + LATEST_PRICE
                                + " WHERE ph.discount < ?");
        List<Object> p = new ArrayList<>();
        p.add(threshold);
        if (venueId != null) {
            sql.append(" AND t.venue_id=?");
            p.add(venueId);
        }
        if (trainSetId != null) {
            sql.append(" AND v.train_set_id=?");
            p.add(trainSetId);
        }
        if (carriageNumber != null) {
            sql.append(" AND t.carriage_number=?");
            p.add(carriageNumber);
        }
        if (seatNumber != null) {
            sql.append(" AND t.seat_number=?");
            p.add(seatNumber);
        }
        if (refundable != null) {
            sql.append(" AND t.refundable=?");
            p.add(refundable);
        }
        if (type != null) {
            sql.append(" AND t.type=?");
            p.add(type.name());
        }

        JpaRows rs = rows(sql.toString(), p);
        rs.next();
        return rs.getLong(1);
    }

    public List<TicketResponse> findBelowDiscount(
            int size,
            long offset,
            int threshold,
            Long venueId,
            Integer trainSetId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type,
            List<SortPart> sorts) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE ph.discount < ?");
        List<Object> p = new ArrayList<>();
        p.add(threshold);
        if (venueId != null) {
            sql.append(" AND t.venue_id=?");
            p.add(venueId);
        }
        if (trainSetId != null) {
            sql.append(" AND v.train_set_id=?");
            p.add(trainSetId);
        }
        if (carriageNumber != null) {
            sql.append(" AND t.carriage_number=?");
            p.add(carriageNumber);
        }
        if (seatNumber != null) {
            sql.append(" AND t.seat_number=?");
            p.add(seatNumber);
        }
        if (refundable != null) {
            sql.append(" AND t.refundable=?");
            p.add(refundable);
        }
        if (type != null) {
            sql.append(" AND t.type=?");
            p.add(type.name());
        }
        sql.append(" ORDER BY ")
                .append(
                        sorts.stream()
                                .map(s -> s.expression() + " " + s.direction())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("t.id ASC"))
                .append(" LIMIT ? OFFSET ?");
        p.add(size);
        p.add(offset);

        JpaRows rs = rows(sql.toString(), p);
        List<TicketResponse> out = new ArrayList<>();
        while (rs.next()) out.add(map(rs));
        return out;
    }

    private static void appendFilters(
            StringBuilder sql,
            List<Object> p,
            Long id,
            String name,
            LocalDate creationDate,
            Long venueId,
            Integer trainSetId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type,
            BigDecimal basePrice,
            Integer discount) {
        if (id != null) {
            sql.append(" AND t.id=?");
            p.add(id);
        }
        if (name != null && !name.isBlank()) {
            sql.append(" AND COALESCE(t.name, '') ILIKE ?");
            p.add("%" + name + "%");
        }
        if (creationDate != null) {
            sql.append(" AND t.creation_date=?");
            p.add(creationDate);
        }
        if (venueId != null) {
            sql.append(" AND t.venue_id=?");
            p.add(venueId);
        }
        if (trainSetId != null) {
            sql.append(" AND v.train_set_id=?");
            p.add(trainSetId);
        }
        if (carriageNumber != null && !carriageNumber.isBlank()) {
            sql.append(" AND COALESCE(t.carriage_number, '') ILIKE ?");
            p.add("%" + carriageNumber + "%");
        }
        if (seatNumber != null && !seatNumber.isBlank()) {
            sql.append(" AND COALESCE(t.seat_number, '') ILIKE ?");
            p.add("%" + seatNumber + "%");
        }
        if (refundable != null) {
            sql.append(" AND t.refundable=?");
            p.add(refundable);
        }
        if (type != null) {
            sql.append(" AND t.type=?");
            p.add(type.name());
        }
        if (basePrice != null) {
            sql.append(" AND COALESCE(ph.base_price::text, '') ILIKE ?");
            p.add("%" + basePrice.toPlainString() + "%");
        }
        if (discount != null) {
            sql.append(" AND COALESCE(ph.discount::text, '') ILIKE ?");
            p.add("%" + discount + "%");
        }
    }

    private static TicketResponse map(JpaRows rs) {
        TicketResponse r = new TicketResponse();
        r.setId(rs.getLong("id"));
        r.setName(rs.getString("name"));
        r.setCreationDate(rs.getObject("creation_date", LocalDate.class));
        r.setBasePrice(rs.getBigDecimal("base_price"));
        r.setDiscount(rs.getInt("discount"));
        r.setRefundable((Boolean) rs.getObject("refundable"));
        String type = rs.getString("type");
        r.setType(type == null ? null : TicketType.valueOf(type));
        r.setVenue(
                new VenueReference(
                        rs.getLong("venue_id"),
                        rs.getString("venue_name"),
                        rs.getInt("train_set_id")));
        r.setCarriageNumber(rs.getString("carriage_number"));
        r.setSeatNumber(rs.getString("seat_number"));
        return r;
    }
}
