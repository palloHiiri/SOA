package com.fuzis.tickets.service;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.PriceHistoryCreateRequest;
import com.fuzis.tickets.dto.PriceHistoryResponse;
import com.fuzis.tickets.repository.PriceHistoryRepository;
import com.fuzis.tickets.repository.TicketRepository;
import com.fuzis.tickets.util.Pagination;
import com.fuzis.tickets.util.SortParser;
import com.fuzis.tickets.util.Sorts;
import com.fuzis.tickets.exception.ApiException;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Dependent
public class PriceHistoryService {
    private final DataSource dataSource;
    private final PriceHistoryRepository repository;
    private final TicketRepository ticketRepository;
    @Inject
    public PriceHistoryService(DataSource dataSource, PriceHistoryRepository repository, TicketRepository ticketRepository) {
        this.dataSource = dataSource;
        this.repository = repository;
        this.ticketRepository = ticketRepository;
    }

    public PageResponse<PriceHistoryResponse> list(
    int page,
    int size,
    List<String> sort,
    Long id,
    Long ticketId,
    OffsetDateTime from,
    OffsetDateTime to,
    BigDecimal basePrice,
    Integer discount
    ) {
        if (from!=null&&to!=null&&from.isAfter(to)) throw ApiException.badRequest("changedDateFrom must be before or equal to changedDateTo");
        Pagination p = Pagination.of(page, size, maxSize());
        var sorts = SortParser.parse(sort, Sorts.PRICES,"changedDate");
        try {
            long total = repository.count(id, ticketId, from, to, basePrice, discount);
            return new PageResponse<>(repository.findPage(size, p.offset(), id, ticketId, from, to, basePrice, discount, sorts), page, size, total);
        }
        catch (SQLException e) {
            throw ApiException.internal("Database operation failed");
        }
    }

    public PriceHistoryResponse get(long id) {
        if (id<1) throw ApiException.badRequest("id must be greater than or equal to 1");
        try {
            PriceHistoryResponse r = repository.findById(id);
            if (r==null) throw ApiException.notFound("Price history "+id+" not found");
            return r;
        }
        catch (SQLException e) {
            throw ApiException.internal("Database operation failed");
        }
    }

    public PriceHistoryResponse create(PriceHistoryCreateRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!ticketRepository.exists(c, request.getTicketId())) throw ApiException.notFound("Ticket "+request.getTicketId()+" not found");
                PriceHistoryResponse result = repository.insert(c, request.getTicketId(), request.getBasePrice(), request.getDiscount());
                c.commit();
                return result;
            }
            catch (RuntimeException e) {
                try {
                    c.rollback();
                }
                catch (SQLException ignored) {
                }
                throw e;
            }
            catch (SQLException e) {
                try {
                    c.rollback();
                }
                catch (SQLException ignored) {
                }
                if ("23503".equals(e.getSQLState())) throw ApiException.notFound("Ticket " + request.getTicketId() + " not found");
                throw ApiException.internal("Database operation failed");
            }
            finally {
                try {
                    c.setAutoCommit(true);
                }
                catch (SQLException ignored) {
                }
            }
        }
        catch (SQLException e) {
            throw ApiException.internal("Database operation failed");
        }
    }

    private static int maxSize() {
        try {
            String v = System.getenv("TICKETS_MAX_PAGE_SIZE");
            return v==null||v.isBlank()?100:Integer.parseInt(v);
        }
        catch (Exception e) {
            return 100;
        }
    }
}
