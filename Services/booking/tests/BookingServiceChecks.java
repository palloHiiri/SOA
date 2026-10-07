import com.fuzis.booking.client.ClientsClient;
import com.fuzis.booking.client.TicketsClient;
import com.fuzis.booking.client.dto.PassengerResponse;
import com.fuzis.booking.client.dto.SeatResponse;
import com.fuzis.booking.client.dto.TicketCreateRequest;
import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.client.dto.VenueResponse;
import com.fuzis.booking.config.DatabaseConfig;
import com.fuzis.booking.exception.NoAvailableSeatException;
import com.fuzis.booking.exception.TicketCreationConflictException;
import com.fuzis.booking.model.Book;
import com.fuzis.booking.repository.BookRepository;
import com.fuzis.booking.service.BookingPersistenceService;
import com.fuzis.booking.service.BookingService;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BookingServiceChecks {
    static final UUID PASSENGER = UUID.randomUUID();
    static final TicketResponse SOURCE =
            new TicketResponse(
                    1L,
                    "test",
                    new BigDecimal("100.00"),
                    10,
                    false,
                    "USUAL",
                    new VenueResponse(2L, "test venue", 1),
                    "01",
                    "1A");

    public static void main(String[] args) {
        var manager = new DatabaseConfig().transactionManager(new DriverManagerDataSource());
        check(
                manager.isRollbackOnCommitFailure() && manager.isEnforceReadOnly(),
                "Production manager must roll back failed commits and enforce read-only"
                    + " transactions");
        Scenario retry = new Scenario();
        retry.tickets.conflicts = 1;
        check(
                retry.service.bookTicketWithDiscount(1L, PASSENGER, 10, "test").ticketId() == 20L,
                "Created ticket must be sold");
        check(
                retry.tickets.searches == 2 && retry.tickets.creates == 2,
                "Conflict must repeat search and create");
        check(
                retry.tickets.lastRequest.seatNumber().equals("3A"),
                "Retry must use fresh search result");
        check(
                retry.tx.isolation == TransactionDefinition.ISOLATION_SERIALIZABLE,
                "Write must be serializable");
        check(retry.tx.commits == 1, "Sale must commit");

        Scenario exhausted = new Scenario();
        exhausted.tickets.conflicts = 10;
        expect(
                TicketCreationConflictException.class,
                () -> exhausted.service.bookTicketWithDiscount(1L, PASSENGER, 10, "test"));
        check(
                exhausted.tickets.searches == 3 && exhausted.tickets.creates == 3,
                "Retries must be bounded");
        check(exhausted.tx.commits == 0, "Failed creation must not save a sale");

        Scenario empty = new Scenario();
        empty.tickets.empty = true;
        expect(
                NoAvailableSeatException.class,
                () -> empty.service.bookTicketWithDiscount(1L, PASSENGER, 10, "test"));
        check(
                empty.tickets.searches == 1 && empty.tickets.creates == 0,
                "No seat must not trigger create");

        Scenario failedHttp = new Scenario();
        failedHttp.tickets.failure = new IllegalStateException("HTTP failure");
        expect(
                IllegalStateException.class,
                () -> failedHttp.service.bookTicketWithDiscount(1L, PASSENGER, 10, "test"));
        check(
                failedHttp.tickets.creates == 1,
                "Unknown failure must not retry a possibly completed create");

        Scenario commitFailure = new Scenario();
        commitFailure.tx.failCommit = true;
        expect(
                ConcurrencyFailureException.class,
                () -> commitFailure.service.bookTicketWithDiscount(1L, PASSENGER, 10, "test"));
        check(
                commitFailure.tickets.deleted == 20L,
                "Commit failure must compensate the remote ticket");
        check(commitFailure.tx.rollbacks == 1, "Commit failure must roll back the sale");

        Scenario read = new Scenario();
        read.service.getPassengerTickets(PASSENGER, "test");
        check(
                read.tx.readOnly
                        && read.tx.isolation == TransactionDefinition.ISOLATION_REPEATABLE_READ,
                "Passenger lookup must use a read-only repeatable snapshot");
        read.service.isTicketSold(1L);
        check(read.tx.readOnly && read.tx.commits == 2, "Sale status must use a read transaction");
        System.out.println(
                "Booking retry, exhaustion, empty result, unknown failure, commit compensation and"
                        + " transaction isolation: OK");
    }

    static class Scenario {
        final FakeTickets tickets = new FakeTickets();
        final TestTransactionManager tx = new TestTransactionManager();
        final BookingService service;

        Scenario() {
            tx.setRollbackOnCommitFailure(true);
            ProxyFactory proxy =
                    new ProxyFactory(new BookingPersistenceService(new FakeRepository()));
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(
                    new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
            service =
                    new BookingService(
                            tickets,
                            new FakeClients(),
                            (BookingPersistenceService) proxy.getProxy());
        }
    }

    static class FakeTickets extends TicketsClient {
        int conflicts, searches, creates;
        boolean empty;
        long deleted;
        RuntimeException failure;
        TicketCreateRequest lastRequest;

        @Override
        public TicketResponse getTicket(Long id) {
            return SOURCE;
        }

        @Override
        public Optional<SeatResponse> findAvailableSeat(Long id) {
            check(
                    !TransactionSynchronizationManager.isActualTransactionActive(),
                    "Remote search must not hold a DB transaction");
            searches++;
            return empty
                    ? Optional.empty()
                    : Optional.of(new SeatResponse(searches, (searches + 1) + "A"));
        }

        @Override
        public Optional<TicketResponse> tryCreateTicket(TicketCreateRequest request) {
            creates++;
            lastRequest = request;
            if (failure != null) throw failure;
            if (creates <= conflicts) return Optional.empty();
            return Optional.of(
                    new TicketResponse(
                            20L,
                            request.name(),
                            request.basePrice(),
                            request.discount(),
                            request.refundable(),
                            request.type(),
                            SOURCE.venue(),
                            request.carriageNumber(),
                            request.seatNumber()));
        }

        @Override
        public void deleteTicket(Long id) {
            deleted = id;
        }
    }

    static class FakeClients extends ClientsClient {
        @Override
        public PassengerResponse getPassenger(UUID id, String token) {
            return new PassengerResponse(id, id, "test", "test");
        }
    }

    static class FakeRepository extends BookRepository {
        FakeRepository() {
            super(new JdbcTemplate());
        }

        @Override
        public Book save(Long ticket, UUID passenger, BigDecimal price, Long source) {
            check(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "Save must run in an actual transaction");
            return new Book(1L, ticket, passenger, price, source, OffsetDateTime.now());
        }

        @Override
        public List<Book> findByPassengerId(UUID id) {
            return List.of();
        }

        @Override
        public Optional<Book> findByTicketId(Long id) {
            return Optional.empty();
        }
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        int isolation, commits, rollbacks;
        boolean readOnly, failCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            isolation = definition.getIsolationLevel();
            readOnly = definition.isReadOnly();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new ConcurrencyFailureException("Simulated commit conflict");
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected failure", failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
