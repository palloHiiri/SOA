package com.fuzis.booking.repository;

import com.fuzis.booking.model.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookRepository {

    private static final Logger log = LoggerFactory.getLogger(BookRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("BookRepository initialized");
    }

    public Book save(
            Long ticketId,
            UUID passengerId,
            BigDecimal price,
            Long sourceTicketId
    ) {
        long startedAt = System.nanoTime();
        log.debug("Saving ticket sale: ticketId={}, passengerId={}, price={}, sourceTicketId={}",
                ticketId, passengerId, price, sourceTicketId);

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

        try {
            Book result = jdbcTemplate.queryForObject(
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
                                rs.getObject("sale_date", OffsetDateTime.class)
                        );
                    },
                    ticketId,
                    passengerId,
                    price,
                    sourceTicketId
            );

            log.info("Ticket sale saved: ticketId={}, passengerId={}, saleId={}, elapsedMs={}",
                    result.ticketId(), result.passengerId(), result.id(), elapsedMs(startedAt));
            return result;
        } catch (RuntimeException exception) {
            log.error("Failed to save ticket sale: ticketId={}, passengerId={}, elapsedMs={}, exception={}",
                    ticketId, passengerId, elapsedMs(startedAt), exception.toString(), exception);
            throw exception;
        }
    }

    public List<Book> findByPassengerId(UUID passengerId) {
        long startedAt = System.nanoTime();
        log.debug("Loading ticket sales by passenger: passengerId={}", passengerId);

        String sql = """
                SELECT
                    id,
                    ticket_id,
                    passenger_id,
                    sale_price,
                    source_ticket_id,
                    sale_date
                FROM ticket_sales
                WHERE passenger_id = ?
                ORDER BY sale_date DESC, id DESC
                """;

        try {
            List<Book> result = jdbcTemplate.query(
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
                                rs.getObject("sale_date", OffsetDateTime.class)
                        );
                    },
                    passengerId
            );

            log.info("Loaded ticket sales by passenger: passengerId={}, count={}, elapsedMs={}",
                    passengerId, result.size(), elapsedMs(startedAt));
            return result;
        } catch (RuntimeException exception) {
            log.error("Failed to load ticket sales by passenger: passengerId={}, elapsedMs={}, exception={}",
                    passengerId, elapsedMs(startedAt), exception.toString(), exception);
            throw exception;
        }
    }

    public Optional<Book> findByTicketId(Long ticketId) {
        long startedAt = System.nanoTime();
        log.debug("Loading ticket sale by ticket: ticketId={}", ticketId);

        String sql = """
                SELECT
                    id,
                    ticket_id,
                    passenger_id,
                    sale_price,
                    source_ticket_id,
                    sale_date
                FROM ticket_sales
                WHERE ticket_id = ?
                """;

        try {
            Optional<Book> result = jdbcTemplate.query(
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
                                rs.getObject("sale_date", OffsetDateTime.class)
                        );
                    },
                    ticketId
            ).stream().findFirst();

            log.info("Ticket sale lookup completed: ticketId={}, found={}, elapsedMs={}",
                    ticketId, result.isPresent(), elapsedMs(startedAt));
            return result;
        } catch (RuntimeException exception) {
            log.error("Failed to load ticket sale: ticketId={}, elapsedMs={}, exception={}",
                    ticketId, elapsedMs(startedAt), exception.toString(), exception);
            throw exception;
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
