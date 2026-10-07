package com.fuzis.tickets.service;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.SeatResponse;
import com.fuzis.tickets.dto.TicketCreateRequest;
import com.fuzis.tickets.dto.TicketResponse;
import com.fuzis.tickets.dto.TicketSearchRequest;
import com.fuzis.tickets.dto.TicketType;
import com.fuzis.tickets.dto.TicketUpdateRequest;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.repository.CdcRepository;
import com.fuzis.tickets.repository.CdcRepository.CdcSnapshot;
import com.fuzis.tickets.repository.InventoryProjectionRepository;
import com.fuzis.tickets.repository.PriceHistoryRepository;
import com.fuzis.tickets.repository.TicketRepository;
import com.fuzis.tickets.repository.VenueRepository;
import com.fuzis.tickets.repository.VenueRepository.VenueRecord;
import com.fuzis.tickets.util.InventoryJson;
import com.fuzis.tickets.util.Pagination;
import com.fuzis.tickets.util.SortParser;
import com.fuzis.tickets.util.Sorts;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Dependent
@Transactional
public class TicketService {

    private final InventoryProjectionRepository inventoryProjectionRepository;
    private final TicketRepository ticketRepository;
    private final VenueRepository venueRepository;
    private final CdcRepository cdcRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    @Inject
    public TicketService(
            InventoryProjectionRepository inventoryProjectionRepository,
            TicketRepository ticketRepository,
            VenueRepository venueRepository,
            CdcRepository cdcRepository,
            PriceHistoryRepository priceHistoryRepository) {
        this.inventoryProjectionRepository = inventoryProjectionRepository;
        this.ticketRepository = ticketRepository;
        this.venueRepository = venueRepository;
        this.cdcRepository = cdcRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public PageResponse<TicketResponse> list(
            int page,
            int size,
            List<String> sort,
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

        Pagination pagination = Pagination.of(page, size, maxSize());
        var sorts = SortParser.parse(sort, Sorts.TICKETS, "id");

        long total =
                ticketRepository.count(
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

        List<TicketResponse> content =
                ticketRepository.findPage(
                        pagination.size(),
                        pagination.offset(),
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
                        discount,
                        sorts);

        return new PageResponse<>(content, page, size, total);
    }

    public PageResponse<TicketResponse> search(TicketSearchRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Request body is required");
        }

        return list(
                request.getPage(),
                request.getSize(),
                request.getSort(),
                request.getId(),
                request.getName(),
                parseCreationDate(request.getCreationDate()),
                request.getVenueId(),
                request.getTrainSetId(),
                request.getCarriageNumber(),
                request.getSeatNumber(),
                request.getRefundable(),
                request.getType(),
                request.getBasePrice(),
                request.getDiscount());
    }

    private LocalDate parseCreationDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Field 'creationDate' must be a valid ISO-8601 date");
        }
    }

    public TicketResponse get(long id) {
        validatePositive(id, "id");

        TicketResponse result = ticketRepository.findById(id);
        if (result == null) {
            throw ApiException.notFound("Ticket " + id + " not found");
        }
        return result;
    }

    public TicketResponse create(TicketCreateRequest request) {

        VenueRecord venue = venueRepository.findForUpdate(request.getVenueId());
        if (venue == null) {
            throw ApiException.notFound("Venue " + request.getVenueId() + " not found");
        }

        CdcSnapshot snapshot =
                cdcRepository
                        .findLatest(venue.trainSetId())
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "Inventory snapshot for train set "
                                                        + venue.trainSetId()
                                                        + " is not available"));

        validateSeat(snapshot.data(), request.getCarriageNumber(), request.getSeatNumber());

        String name =
                request.getName() == null
                        ? generateName(
                                request.getSeatNumber(), request.getCarriageNumber(), venue.name())
                        : requireNonBlank(request.getName(), "name");

        long ticketId =
                ticketRepository.insert(
                        name,
                        venue.id(),
                        request.getCarriageNumber(),
                        request.getSeatNumber(),
                        request.getRefundable(),
                        request.getType());

        priceHistoryRepository.insert(ticketId, request.getBasePrice(), request.getDiscount());

        TicketResponse result = ticketRepository.findById(ticketId);
        return result;
    }

    public TicketResponse update(long id, TicketUpdateRequest request) {
        validatePositive(id, "id");

        TicketResponse current = ticketRepository.findById(id);
        if (current == null) {
            throw ApiException.notFound("Ticket " + id + " not found");
        }

        VenueRecord venue = venueRepository.findForUpdate(request.getVenueId());
        if (venue == null) {
            throw ApiException.notFound("Venue " + request.getVenueId() + " not found");
        }

        CdcSnapshot snapshot =
                cdcRepository
                        .findLatest(venue.trainSetId())
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "Inventory snapshot for train set "
                                                        + venue.trainSetId()
                                                        + " is not available"));

        validateSeat(snapshot.data(), request.getCarriageNumber(), request.getSeatNumber());

        String name =
                request.getName() == null
                        ? generateName(
                                request.getSeatNumber(), request.getCarriageNumber(), venue.name())
                        : requireNonBlank(request.getName(), "name");

        ticketRepository.update(
                id,
                name,
                venue.id(),
                request.getCarriageNumber(),
                request.getSeatNumber(),
                request.getRefundable(),
                request.getType());

        TicketResponse result = ticketRepository.findById(id);
        return result;
    }

    public void delete(long id) {
        validatePositive(id, "id");

        TicketResponse current = ticketRepository.findById(id);
        if (current == null) {
            throw ApiException.notFound("Ticket " + id + " not found");
        }

        ticketRepository.delete(id);
    }

    public Optional<SeatResponse> availableSeat(long sourceTicketId) {
        validatePositive(sourceTicketId, "sourceTicketId");
        return inventoryProjectionRepository.findAvailableSeat(sourceTicketId);
    }

    public TicketResponse latest() {

        TicketResponse result = ticketRepository.findLatest();
        if (result == null) {
            throw ApiException.notFound("Ticket collection is empty");
        }
        return result;
    }

    public BigDecimal averageDiscount() {

        if (ticketRepository.countTickets() == 0) {
            throw ApiException.notFound("Ticket collection is empty");
        }
        return ticketRepository.averageCurrentDiscount();
    }

    public PageResponse<TicketResponse> belowDiscount(
            int threshold,
            int page,
            int size,
            List<String> sort,
            Long venueId,
            Integer trainSetId,
            String carriageNumber,
            String seatNumber,
            Boolean refundable,
            TicketType type) {

        if (threshold < 1 || threshold > 100) {
            throw ApiException.badRequest("discount must be between 1 and 100");
        }

        Pagination pagination = Pagination.of(page, size, maxSize());
        var sorts = SortParser.parse(sort, Sorts.TICKETS, "id");

        long total =
                ticketRepository.countBelowDiscount(
                        threshold,
                        venueId,
                        trainSetId,
                        carriageNumber,
                        seatNumber,
                        refundable,
                        type);

        List<TicketResponse> content =
                ticketRepository.findBelowDiscount(
                        pagination.size(),
                        pagination.offset(),
                        threshold,
                        venueId,
                        trainSetId,
                        carriageNumber,
                        seatNumber,
                        refundable,
                        type,
                        sorts);

        return new PageResponse<>(content, page, size, total);
    }

    private void validateSeat(String data, String carriageNumber, String seatNumber) {
        JsonObject trainSet = InventoryJson.object(data);

        Optional<JsonObject> carriage =
                InventoryJson.carriages(trainSet).stream()
                        .filter(
                                c ->
                                        carriageNumber.equals(
                                                InventoryJson.string(c, "carriageNumber")))
                        .findFirst();

        if (carriage.isEmpty()) {
            throw ApiException.badRequest(
                    "Carriage " + carriageNumber + " does not exist in the train set");
        }

        boolean exists =
                InventoryJson.seats(carriage.get()).stream()
                        .anyMatch(s -> seatNumber.equals(InventoryJson.string(s, "seatNumber")));

        if (!exists) {
            throw ApiException.badRequest(
                    "Seat "
                            + seatNumber
                            + " in carriage "
                            + carriageNumber
                            + " does not exist in the train set");
        }
    }

    private static String generateName(String seat, String carriage, String venue) {
        return "Билет: " + seat + ", вагон: " + carriage + " рейс: " + venue;
    }

    private static String requireNonBlank(String value, String field) {
        if (value.isBlank()) {
            throw ApiException.badRequest(field + " must not be blank");
        }
        return value;
    }

    private static void validatePositive(long value, String field) {
        if (value < 1) {
            throw ApiException.badRequest(field + " must be greater than or equal to 1");
        }
    }

    private static int maxSize() {
        return intEnv("TICKETS_MAX_PAGE_SIZE", 100);
    }

    private static int intEnv(String key, int fallback) {
        try {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
