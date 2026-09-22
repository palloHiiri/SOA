package com.fuzis.booking.repository;

import com.fuzis.booking.model.Book;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class BookRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Book save(
            Long ticketId,
            UUID passengerId,
            BigDecimal price,
            Long sourceTicketId
    ) {

        String sql = """
                INSERT INTO ticket_sales (
                    ticket_id,
                    passenger_id,
                    sale_price,
                    source_ticket_id
                )
                VALUES (?, ?, ?, ?)
                RETURNING
                    id,
                    ticket_id,
                    passenger_id,
                    sale_price,
                    source_ticket_id,
                    sale_date
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    Long sourceId = rs.getObject("source_ticket_id") == null
                            ? null
                            : rs.getLong("source_ticket_id");

                    return new Book(
                            rs.getLong("id"),
                            rs.getLong("ticket_id"),
                            rs.getObject("passenger_id", UUID.class),
                            rs.getBigDecimal("sale_price"),
                            sourceId,
                            rs.getObject(
                                    "sale_date",
                                    OffsetDateTime.class
                            )
                    );
                },
                ticketId,
                passengerId,
                price,
                sourceTicketId
        );
    }
}