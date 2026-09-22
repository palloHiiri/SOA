package com.fuzis.tickets.repository;

import com.fuzis.tickets.dto.TicketResponse;
import com.fuzis.tickets.dto.TicketType;
import com.fuzis.tickets.dto.VenueReference;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Dependent
public class TicketRepository {
    private final DataSource dataSource;

    @Inject
    public TicketRepository(DataSource dataSource) { this.dataSource = dataSource; }

    private static final String LATEST_PRICE = "LEFT JOIN LATERAL (SELECT ph.base_price, ph.discount FROM price_histories ph WHERE ph.ticket_id = t.id ORDER BY ph.changed_date DESC, ph.id DESC LIMIT 1) ph ON TRUE";
    private static final String BASE_SELECT = "SELECT t.id, t.name, t.creation_date, t.venue_id, t.carriage_number, t.seat_number, t.refundable, t.type, v.name AS venue_name, v.train_set_id, ph.base_price, ph.discount FROM tickets t JOIN venues v ON v.id=t.venue_id " + LATEST_PRICE;

    public long insert(Connection c, String name, long venueId, String carriageNumber, String seatNumber, Boolean refundable, TicketType type) throws SQLException {
        String sql="INSERT INTO tickets(name,venue_id,carriage_number,seat_number,refundable,type) VALUES(?,?,?,?,?,?) RETURNING id";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,name);ps.setLong(2,venueId);ps.setString(3,carriageNumber);ps.setString(4,seatNumber);if(refundable==null)ps.setObject(5,null);else ps.setBoolean(5,refundable);if(type==null)ps.setObject(6,null);else ps.setString(6,type.name());try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    }

    public TicketResponse findById(long id) throws SQLException {
        try(Connection c=dataSource.getConnection()){return findById(c,id);}
    }

    public TicketResponse findById(Connection c,long id) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(BASE_SELECT+" WHERE t.id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?map(rs):null;}}
    }

    public void update(Connection c,long id,String name,long venueId,String carriageNumber,String seatNumber,Boolean refundable,TicketType type)throws SQLException{
        String sql="UPDATE tickets SET name=?, venue_id=?, carriage_number=?, seat_number=?, refundable=?, type=? WHERE id=?";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,name);ps.setLong(2,venueId);ps.setString(3,carriageNumber);ps.setString(4,seatNumber);if(refundable==null)ps.setObject(5,null);else ps.setBoolean(5,refundable);if(type==null)ps.setObject(6,null);else ps.setString(6,type.name());ps.setLong(7,id);if(ps.executeUpdate()!=1)throw new SQLException("Ticket disappeared during update");}
    }

    public void delete(Connection c,long id)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("DELETE FROM tickets WHERE id=?")){ps.setLong(1,id);if(ps.executeUpdate()!=1)throw new SQLException("Ticket disappeared during delete");}
    }

    public boolean exists(Connection c,long id)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM tickets WHERE id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next();}}
    }

    public long countByVenue(Connection c,long venueId)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM tickets WHERE venue_id=?")){ps.setLong(1,venueId);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    }

    public long count(Long id,String name,LocalDate creationDate,Long venueId,Integer trainSetId,String carriageNumber,String seatNumber,Boolean refundable,TicketType type,BigDecimal basePrice,Integer discount)throws SQLException{
        try(Connection c=dataSource.getConnection()){return count(c,id,name,creationDate,venueId,trainSetId,carriageNumber,seatNumber,refundable,type,basePrice,discount);}
    }

    public long count(Connection c,Long id,String name,LocalDate creationDate,Long venueId,Integer trainSetId,String carriageNumber,String seatNumber,Boolean refundable,TicketType type,BigDecimal basePrice,Integer discount)throws SQLException{
        StringBuilder sql=new StringBuilder("SELECT COUNT(*) FROM tickets t JOIN venues v ON v.id=t.venue_id "+LATEST_PRICE+" WHERE 1=1");
        List<Object> p=new ArrayList<>(); appendFilters(sql,p,id,name,creationDate,venueId,trainSetId,carriageNumber,seatNumber,refundable,type,basePrice,discount);
        try(PreparedStatement ps=c.prepareStatement(sql.toString())){Sql.bind(ps,p);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    }

    public List<TicketResponse> findPage(int size,long offset,Long id,String name,LocalDate creationDate,Long venueId,Integer trainSetId,String carriageNumber,String seatNumber,Boolean refundable,TicketType type,BigDecimal basePrice,Integer discount,List<SortPart> sorts)throws SQLException{
        StringBuilder sql=new StringBuilder(BASE_SELECT+" WHERE 1=1"); List<Object> p=new ArrayList<>(); appendFilters(sql,p,id,name,creationDate,venueId,trainSetId,carriageNumber,seatNumber,refundable,type,basePrice,discount);
        sql.append(" ORDER BY ").append(sorts.stream().map(s->s.expression()+" "+s.direction()).reduce((a,b)->a+", "+b).orElse("t.id ASC")); sql.append(" LIMIT ? OFFSET ?"); p.add(size);p.add(offset);
        try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement(sql.toString())){Sql.bind(ps,p);try(ResultSet rs=ps.executeQuery()){List<TicketResponse> out=new ArrayList<>();while(rs.next())out.add(map(rs));return out;}}
    }

    public BigDecimal averageCurrentDiscount() throws SQLException{
        String sql="SELECT AVG(ph.discount) FROM tickets t LEFT JOIN LATERAL (SELECT discount FROM price_histories ph WHERE ph.ticket_id=t.id ORDER BY ph.changed_date DESC, ph.id DESC LIMIT 1) ph ON TRUE";
        try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){rs.next();return rs.getBigDecimal(1);}
    }

    public long countTickets() throws SQLException{try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM tickets");ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}

    public TicketResponse findLatest() throws SQLException{
        String sql=BASE_SELECT+" ORDER BY t.creation_date DESC, t.id DESC LIMIT 1";
        try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){return rs.next()?map(rs):null;}
    }


    public long countBelowDiscount(int threshold, Long venueId, Integer trainSetId, String carriageNumber, String seatNumber,
                                   Boolean refundable, TicketType type) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tickets t JOIN venues v ON v.id=t.venue_id " + LATEST_PRICE + " WHERE ph.discount < ?");
        List<Object> p = new ArrayList<>(); p.add(threshold);
        if(venueId!=null){sql.append(" AND t.venue_id=?");p.add(venueId);} if(trainSetId!=null){sql.append(" AND v.train_set_id=?");p.add(trainSetId);}
        if(carriageNumber!=null){sql.append(" AND t.carriage_number=?");p.add(carriageNumber);} if(seatNumber!=null){sql.append(" AND t.seat_number=?");p.add(seatNumber);}
        if(refundable!=null){sql.append(" AND t.refundable=?");p.add(refundable);} if(type!=null){sql.append(" AND t.type=?");p.add(type.name());}
        try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement(sql.toString())){Sql.bind(ps,p);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    }

    public List<TicketResponse> findBelowDiscount(int size,long offset,int threshold, Long venueId, Integer trainSetId, String carriageNumber,
                                                   String seatNumber, Boolean refundable, TicketType type, List<SortPart> sorts) throws SQLException {
        StringBuilder sql=new StringBuilder(BASE_SELECT+" WHERE ph.discount < ?"); List<Object>p=new ArrayList<>();p.add(threshold);
        if(venueId!=null){sql.append(" AND t.venue_id=?");p.add(venueId);} if(trainSetId!=null){sql.append(" AND v.train_set_id=?");p.add(trainSetId);} if(carriageNumber!=null){sql.append(" AND t.carriage_number=?");p.add(carriageNumber);} if(seatNumber!=null){sql.append(" AND t.seat_number=?");p.add(seatNumber);} if(refundable!=null){sql.append(" AND t.refundable=?");p.add(refundable);} if(type!=null){sql.append(" AND t.type=?");p.add(type.name());}
        sql.append(" ORDER BY ").append(sorts.stream().map(s->s.expression()+" "+s.direction()).reduce((a,b)->a+", "+b).orElse("t.id ASC")).append(" LIMIT ? OFFSET ?");p.add(size);p.add(offset);
        try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement(sql.toString())){Sql.bind(ps,p);try(ResultSet rs=ps.executeQuery()){List<TicketResponse>out=new ArrayList<>();while(rs.next())out.add(map(rs));return out;}}
    }

    private static void appendFilters(StringBuilder sql,List<Object> p,Long id,String name,LocalDate creationDate,Long venueId,Integer trainSetId,String carriageNumber,String seatNumber,Boolean refundable,TicketType type,BigDecimal basePrice,Integer discount){
        if(id!=null){sql.append(" AND t.id=?");p.add(id);} if(name!=null){sql.append(" AND t.name=?");p.add(name);} if(creationDate!=null){sql.append(" AND t.creation_date=?");p.add(creationDate);}
        if(venueId!=null){sql.append(" AND t.venue_id=?");p.add(venueId);} if(trainSetId!=null){sql.append(" AND v.train_set_id=?");p.add(trainSetId);} if(carriageNumber!=null){sql.append(" AND t.carriage_number=?");p.add(carriageNumber);} if(seatNumber!=null){sql.append(" AND t.seat_number=?");p.add(seatNumber);} if(refundable!=null){sql.append(" AND t.refundable=?");p.add(refundable);} if(type!=null){sql.append(" AND t.type=?");p.add(type.name());}
        if(basePrice!=null){sql.append(" AND ph.base_price=?");p.add(basePrice);} if(discount!=null){sql.append(" AND ph.discount=?");p.add(discount);}
    }

    private static TicketResponse map(ResultSet rs)throws SQLException{
        TicketResponse r=new TicketResponse();r.setId(rs.getLong("id"));r.setName(rs.getString("name"));r.setCreationDate(rs.getObject("creation_date",LocalDate.class));r.setBasePrice(rs.getBigDecimal("base_price"));r.setDiscount(rs.getInt("discount"));r.setRefundable((Boolean)rs.getObject("refundable"));String type=rs.getString("type");r.setType(type==null?null:TicketType.valueOf(type));r.setVenue(new VenueReference(rs.getLong("venue_id"),rs.getString("venue_name"),rs.getInt("train_set_id")));r.setCarriageNumber(rs.getString("carriage_number"));r.setSeatNumber(rs.getString("seat_number"));return r;
    }
}
