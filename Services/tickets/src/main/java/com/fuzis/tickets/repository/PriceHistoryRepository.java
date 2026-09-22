package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.PriceHistoryResponse;
import com.fuzis.tickets.util.Sql;
import com.fuzis.tickets.util.SortParser.SortPart;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Dependent
public class PriceHistoryRepository {
    private final DataSource dataSource;

    @Inject
    public PriceHistoryRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public PriceHistoryResponse insert(Connection c, long ticketId, BigDecimal basePrice, int discount) throws SQLException {
        String sql = "INSERT INTO price_histories (ticket_id, base_price, discount) VALUES (?, ?, ?) RETURNING id, ticket_id, changed_date, base_price, discount";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            if (basePrice == null) ps.setObject(2, null); else ps.setBigDecimal(2, basePrice);
            ps.setInt(3, discount);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return map(rs); }
        }
    }

    public PriceHistoryResponse findById(long id) throws SQLException {
        String sql = "SELECT id, ticket_id, changed_date, base_price, discount FROM price_histories WHERE id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    public Optional<PriceHistoryResponse> latestForTicket(Connection c, long ticketId) throws SQLException {
        String sql = "SELECT id, ticket_id, changed_date, base_price, discount FROM price_histories WHERE ticket_id = ? ORDER BY changed_date DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        }
    }

    public long count(Long id, Long ticketId, OffsetDateTime from, OffsetDateTime to, BigDecimal basePrice, Integer discount) throws SQLException {
        try (Connection c = dataSource.getConnection()) { return count(c,id,ticketId,from,to,basePrice,discount); }
    }

    public long count(Connection c, Long id, Long ticketId, OffsetDateTime from, OffsetDateTime to, BigDecimal basePrice, Integer discount) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM price_histories WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, id,ticketId,from,to,basePrice,discount);
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) { Sql.bind(ps,params); try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);} }
    }

    public List<PriceHistoryResponse> findPage(int size, long offset, Long id, Long ticketId, OffsetDateTime from, OffsetDateTime to,
                                                 BigDecimal basePrice, Integer discount, List<SortPart> sorts) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, ticket_id, changed_date, base_price, discount FROM price_histories WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql,params,id,ticketId,from,to,basePrice,discount);
        sql.append(" ORDER BY ").append(sorts.stream().map(s -> s.expression()+" "+s.direction()).reduce((a,b)->a+", "+b).orElse("changed_date DESC, id DESC"));
        sql.append(" LIMIT ? OFFSET ?"); params.add(size); params.add(offset);
        try(Connection c=dataSource.getConnection(); PreparedStatement ps=c.prepareStatement(sql.toString())){
            Sql.bind(ps,params); try(ResultSet rs=ps.executeQuery()) { List<PriceHistoryResponse> out=new ArrayList<>(); while(rs.next()) out.add(map(rs)); return out; }
        }
    }

    private static void appendFilters(StringBuilder sql,List<Object> p,Long id,Long ticketId,OffsetDateTime from,OffsetDateTime to,BigDecimal basePrice,Integer discount){
        if(id!=null){sql.append(" AND id = ?");p.add(id);} if(ticketId!=null){sql.append(" AND ticket_id = ?");p.add(ticketId);}
        if(from!=null){sql.append(" AND changed_date >= ?");p.add(from);} if(to!=null){sql.append(" AND changed_date <= ?");p.add(to);}
        if(basePrice!=null){sql.append(" AND base_price = ?");p.add(basePrice);} if(discount!=null){sql.append(" AND discount = ?");p.add(discount);}
    }

    private static PriceHistoryResponse map(ResultSet rs)throws SQLException{
        PriceHistoryResponse r=new PriceHistoryResponse(); r.setId(rs.getLong("id")); r.setTicketId(rs.getLong("ticket_id"));
        r.setChangedDate(rs.getObject("changed_date", OffsetDateTime.class)); r.setBasePrice(rs.getBigDecimal("base_price")); r.setDiscount(rs.getInt("discount")); return r;
    }
}
