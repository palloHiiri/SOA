package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.PriceHistoryResponse;
import com.fuzis.tickets.entity.PriceHistoryEntity;
import com.fuzis.tickets.util.SortParser.SortPart;

import jakarta.enterprise.context.Dependent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Dependent
public class PriceHistoryRepository extends JpaRepository {

    public PriceHistoryResponse insert(long ticketId, BigDecimal basePrice, int discount) {
        PriceHistoryEntity price = new PriceHistoryEntity();
        price.ticketId = ticketId;
        price.basePrice = basePrice;
        price.discount = discount;
        entityManager.persist(price);
        entityManager.flush();
        entityManager.refresh(price);
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setId(price.id);
        response.setTicketId(price.ticketId);
        response.setChangedDate(price.changedDate);
        response.setBasePrice(price.basePrice);
        response.setDiscount(price.discount);
        return response;
    }

    public PriceHistoryResponse findById(long id) {
        String sql =
                "SELECT id, ticket_id, changed_date, base_price, discount FROM price_histories"
                        + " WHERE id = ?";

        String querySql = sql;
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, id);

        JpaRows rs = rows(querySql, bindings);
        return rs.next() ? map(rs) : null;
    }

    public Optional<PriceHistoryResponse> latestForTicket(long ticketId) {
        String sql =
                "SELECT id, ticket_id, changed_date, base_price, discount FROM price_histories"
                        + " WHERE ticket_id = ? ORDER BY changed_date DESC, id DESC LIMIT 1";

        String querySql = sql;
        Map<Integer, Object> bindings = new HashMap<>();
        bindings.put(1, ticketId);

        JpaRows rs = rows(querySql, bindings);
        return rs.next() ? Optional.of(map(rs)) : Optional.empty();
    }

    public long count(
            Long id,
            Long ticketId,
            OffsetDateTime from,
            OffsetDateTime to,
            BigDecimal basePrice,
            Integer discount) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM price_histories WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, id, ticketId, from, to, basePrice, discount);

        JpaRows rs = rows(sql.toString(), params);
        rs.next();
        return rs.getLong(1);
    }

    public List<PriceHistoryResponse> findPage(
            int size,
            long offset,
            Long id,
            Long ticketId,
            OffsetDateTime from,
            OffsetDateTime to,
            BigDecimal basePrice,
            Integer discount,
            List<SortPart> sorts) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT id, ticket_id, changed_date, base_price, discount FROM"
                                + " price_histories WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, id, ticketId, from, to, basePrice, discount);
        sql.append(" ORDER BY ")
                .append(
                        sorts.stream()
                                .map(s -> s.expression() + " " + s.direction())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("changed_date DESC, id DESC"));
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        JpaRows rs = rows(sql.toString(), params);
        List<PriceHistoryResponse> out = new ArrayList<>();
        while (rs.next()) out.add(map(rs));
        return out;
    }

    private static void appendFilters(
            StringBuilder sql,
            List<Object> p,
            Long id,
            Long ticketId,
            OffsetDateTime from,
            OffsetDateTime to,
            BigDecimal basePrice,
            Integer discount) {
        if (id != null) {
            sql.append(" AND id = ?");
            p.add(id);
        }
        if (ticketId != null) {
            sql.append(" AND ticket_id = ?");
            p.add(ticketId);
        }
        if (from != null) {
            sql.append(" AND changed_date >= ?");
            p.add(from);
        }
        if (to != null) {
            sql.append(" AND changed_date <= ?");
            p.add(to);
        }
        if (basePrice != null) {
            sql.append(" AND base_price = ?");
            p.add(basePrice);
        }
        if (discount != null) {
            sql.append(" AND discount = ?");
            p.add(discount);
        }
    }

    private static PriceHistoryResponse map(JpaRows rs) {
        PriceHistoryResponse r = new PriceHistoryResponse();
        r.setId(rs.getLong("id"));
        r.setTicketId(rs.getLong("ticket_id"));
        r.setChangedDate(rs.getObject("changed_date", OffsetDateTime.class));
        r.setBasePrice(rs.getBigDecimal("base_price"));
        r.setDiscount(rs.getInt("discount"));
        return r;
    }
}
