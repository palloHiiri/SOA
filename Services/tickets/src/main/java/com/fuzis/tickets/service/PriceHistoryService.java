package com.fuzis.tickets.service;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.PriceHistoryCreateRequest;
import com.fuzis.tickets.dto.PriceHistoryResponse;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.repository.PriceHistoryRepository;
import com.fuzis.tickets.repository.TicketRepository;
import com.fuzis.tickets.util.Pagination;
import com.fuzis.tickets.util.SortParser;
import com.fuzis.tickets.util.Sorts;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Dependent
@Transactional
public class PriceHistoryService {
    private final PriceHistoryRepository repository;
    private final TicketRepository ticketRepository;

    @Inject
    public PriceHistoryService(
            PriceHistoryRepository repository, TicketRepository ticketRepository) {
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
            Integer discount) {
        if (from != null && to != null && from.isAfter(to))
            throw ApiException.badRequest(
                    "changedDateFrom must be before or equal to changedDateTo");
        Pagination p = Pagination.of(page, size, maxSize());
        var sorts = SortParser.parse(sort, Sorts.PRICES, "changedDate");

        long total = repository.count(id, ticketId, from, to, basePrice, discount);
        return new PageResponse<>(
                repository.findPage(
                        size, p.offset(), id, ticketId, from, to, basePrice, discount, sorts),
                page,
                size,
                total);
    }

    public PriceHistoryResponse get(long id) {
        if (id < 1) throw ApiException.badRequest("id must be greater than or equal to 1");

        PriceHistoryResponse r = repository.findById(id);
        if (r == null) throw ApiException.notFound("Price history " + id + " not found");
        return r;
    }

    public PriceHistoryResponse create(PriceHistoryCreateRequest request) {

        if (!ticketRepository.existsForUpdate(request.getTicketId()))
            throw ApiException.notFound("Ticket " + request.getTicketId() + " not found");
        PriceHistoryResponse result =
                repository.insert(
                        request.getTicketId(), request.getBasePrice(), request.getDiscount());
        return result;
    }

    private static int maxSize() {
        try {
            String v = System.getenv("TICKETS_MAX_PAGE_SIZE");
            return v == null || v.isBlank() ? 100 : Integer.parseInt(v);
        } catch (Exception e) {
            return 100;
        }
    }
}
