package com.fuzis.tickets.service;

import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.TicketCreateRequest;
import com.fuzis.tickets.dto.TicketResponse;
import com.fuzis.tickets.dto.TicketType;
import com.fuzis.tickets.dto.TicketUpdateRequest;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.repository.CdcRepository;
import com.fuzis.tickets.repository.CdcRepository.CdcSnapshot;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Dependent
public class TicketService {

    private final DataSource dataSource;
    private final TicketRepository ticketRepository;
    private final VenueRepository venueRepository;
    private final CdcRepository cdcRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    @Inject
    public TicketService(
    DataSource dataSource,
    TicketRepository ticketRepository,
    VenueRepository venueRepository,
    CdcRepository cdcRepository,
    PriceHistoryRepository priceHistoryRepository) {
        this.dataSource = dataSource;
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

        try {
            long total = ticketRepository.count(
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

            List<TicketResponse> content = ticketRepository.findPage(
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
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    public TicketResponse get(long id) {
        validatePositive(id, "id");

        try {
            TicketResponse result = ticketRepository.findById(id);
            if (result == null) {
                throw ApiException.notFound("Ticket " + id + " not found");
            }
            return result;
        }
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    public TicketResponse create(TicketCreateRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                VenueRecord venue = venueRepository.findForUpdate(connection, request.getVenueId());
                if (venue == null) {
                    throw ApiException.notFound("Venue " + request.getVenueId() + " not found");
                }

                CdcSnapshot snapshot = cdcRepository
                .findLatest(connection, venue.trainSetId())
                .orElseThrow(() -> ApiException.notFound(
                "Inventory snapshot for train set "
                + venue.trainSetId()
                + " is not available"));

                validateSeat(
                snapshot.data(),
                request.getCarriageNumber(),
                request.getSeatNumber());

                String name = request.getName() == null
                ? generateName(
                request.getSeatNumber(),
                request.getCarriageNumber(),
                venue.name())
                : requireNonBlank(request.getName(), "name");

                long ticketId = ticketRepository.insert(
                connection,
                name,
                venue.id(),
                request.getCarriageNumber(),
                request.getSeatNumber(),
                request.getRefundable(),
                request.getType());

                priceHistoryRepository.insert(
                connection,
                ticketId,
                request.getBasePrice(),
                request.getDiscount());

                TicketResponse result = ticketRepository.findById(connection, ticketId);
                connection.commit();
                return result;
            }
            catch (SQLException e) {
                rollbackQuietly(connection);
                throw databaseError(e);
            }
            catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
            finally {
                restoreAutoCommit(connection);
            }
        }
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    public TicketResponse update(long id, TicketUpdateRequest request) {
        validatePositive(id, "id");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                TicketResponse current = ticketRepository.findById(connection, id);
                if (current == null) {
                    throw ApiException.notFound("Ticket " + id + " not found");
                }

                VenueRecord venue = venueRepository.findForUpdate(connection, request.getVenueId());
                if (venue == null) {
                    throw ApiException.notFound("Venue " + request.getVenueId() + " not found");
                }

                CdcSnapshot snapshot = cdcRepository
                .findLatest(connection, venue.trainSetId())
                .orElseThrow(() -> ApiException.notFound(
                "Inventory snapshot for train set "
                + venue.trainSetId()
                + " is not available"));

                validateSeat(
                snapshot.data(),
                request.getCarriageNumber(),
                request.getSeatNumber());

                String name = request.getName() == null
                ? generateName(
                request.getSeatNumber(),
                request.getCarriageNumber(),
                venue.name())
                : requireNonBlank(request.getName(), "name");

                ticketRepository.update(
                connection,
                id,
                name,
                venue.id(),
                request.getCarriageNumber(),
                request.getSeatNumber(),
                request.getRefundable(),
                request.getType());

                TicketResponse result = ticketRepository.findById(connection, id);
                connection.commit();
                return result;
            }
            catch (SQLException e) {
                rollbackQuietly(connection);
                throw databaseError(e);
            }
            catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
            finally {
                restoreAutoCommit(connection);
            }
        }
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    public void delete(long id) {
        validatePositive(id, "id");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                TicketResponse current = ticketRepository.findById(connection, id);
                if (current == null) {
                    throw ApiException.notFound("Ticket " + id + " not found");
                }

                ticketRepository.delete(connection, id);
                connection.commit();
            }
            catch (SQLException e) {
                rollbackQuietly(connection);
                throw databaseError(e);
            }
            catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
            finally {
                restoreAutoCommit(connection);
            }
        }
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    public TicketResponse latest() {
        try {
            TicketResponse result = ticketRepository.findLatest();
            if (result == null) {
                throw ApiException.notFound("Ticket collection is empty");
            }
            return result;
        }
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    public BigDecimal averageDiscount() {
        try {
            if (ticketRepository.countTickets() == 0) {
                throw ApiException.notFound("Ticket collection is empty");
            }
            return ticketRepository.averageCurrentDiscount();
        }
        catch (SQLException e) {
            throw databaseError(e);
        }
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

        try {
            long total = ticketRepository.countBelowDiscount(
            threshold,
            venueId,
            trainSetId,
            carriageNumber,
            seatNumber,
            refundable,
            type);

            List<TicketResponse> content = ticketRepository.findBelowDiscount(
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
        catch (SQLException e) {
            throw databaseError(e);
        }
    }

    private void validateSeat(String data, String carriageNumber, String seatNumber) {
        JsonObject trainSet = InventoryJson.object(data);

        Optional<JsonObject> carriage = InventoryJson.carriages(trainSet).stream()
        .filter(c -> carriageNumber.equals(InventoryJson.string(c, "carriageNumber")))
        .findFirst();

        if (carriage.isEmpty()) {
            throw ApiException.badRequest(
            "Carriage " + carriageNumber + " does not exist in the train set");
        }

        boolean exists = InventoryJson.seats(carriage.get()).stream()
        .anyMatch(s -> seatNumber.equals(InventoryJson.string(s, "seatNumber")));

        if (!exists) {
            throw ApiException.badRequest(
            "Seat " + seatNumber
            + " in carriage " + carriageNumber
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
            return value == null || value.isBlank()
            ? fallback
            : Integer.parseInt(value);
        }
        catch (Exception e) {
            return fallback;
        }
    }

    private static ApiException databaseError(SQLException e) {
        if ("23505".equals(e.getSQLState())) {
            return ApiException.conflict("The requested unique value is already in use");
        }
        return ApiException.internal("Database operation failed");
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        }
        catch (SQLException ignored) {

        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        }
        catch (SQLException ignored) {

        }
    }
}
