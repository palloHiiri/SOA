package com.fuzis.booking.service;

import com.fuzis.booking.client.ClientsClient;
import com.fuzis.booking.client.TicketsClient;
import com.fuzis.booking.client.dto.*;
import com.fuzis.booking.dto.BookResponse;
import com.fuzis.booking.exception.InvalidDiscountException;
import com.fuzis.booking.exception.NoAvailableSeatException;
import com.fuzis.booking.exception.TicketAlreadyBookedException;
import com.fuzis.booking.exception.TicketWithoutPriceException;
import com.fuzis.booking.model.Book;
import com.fuzis.booking.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final TicketsClient ticketsClient;
    private final ClientsClient clientsClient;
    private final BookRepository bookRepository;

    public BookingService(
            TicketsClient ticketsClient,
            ClientsClient clientsClient,
            BookRepository bookRepository
    ) {
        this.ticketsClient = ticketsClient;
        this.clientsClient = clientsClient;
        this.bookRepository = bookRepository;
        log.info("BookingService initialized");
    }

    public BookResponse bookTicket(
            Long ticketId,
            UUID passengerId,
            String sessionToken
    ) {
        long startedAt = System.nanoTime();
        log.info("Booking request started: ticketId={}, passengerId={}", ticketId, passengerId);

        try {
            TicketResponse ticketResponse = ticketsClient.getTicket(ticketId);
            log.debug("Source ticket loaded: ticketId={}, basePrice={}, seat={}, carriage={}",
                    ticketResponse.id(), ticketResponse.basePrice(),
                    ticketResponse.seatNumber(), ticketResponse.carriageNumber());

            PassengerResponse passengerResponse = clientsClient.getPassenger(
                    passengerId,
                    sessionToken
            );
            log.debug("Passenger loaded: requestedPassengerId={}, resolvedPassengerId={}",
                    passengerId, passengerResponse.id());

            if (ticketResponse.basePrice() == null) {
                log.warn("Booking rejected because ticket has no price: ticketId={}", ticketId);
                throw new TicketWithoutPriceException(ticketId);
            }

            try {
                Book book = bookRepository.save(
                        ticketResponse.id(),
                        passengerResponse.id(),
                        ticketResponse.basePrice(),
                        null
                );

                log.info("Booking completed: ticketId={}, passengerId={}, price={}, elapsedMs={}",
                        book.ticketId(), book.passengerId(), book.price(), elapsedMs(startedAt));

                return new BookResponse(
                        book.ticketId(),
                        book.passengerId(),
                        book.price()
                );

            } catch (DuplicateKeyException exception) {
                log.warn("Booking rejected because ticket is already booked: ticketId={}, passengerId={}",
                        ticketId, passengerId, exception);
                throw new TicketAlreadyBookedException(ticketId);
            }
        } catch (RuntimeException exception) {
            if (!(exception instanceof TicketAlreadyBookedException)) {
                log.error("Booking request failed: ticketId={}, passengerId={}, elapsedMs={}, exception={}",
                        ticketId, passengerId, elapsedMs(startedAt), exception.toString(), exception);
            }
            throw exception;
        }
    }

    public BookResponse bookTicketWithDiscount(
            Long sourceTicketId,
            UUID passengerId,
            Integer discount,
            String sessionToken
    ) {
        long startedAt = System.nanoTime();
        log.info("Discounted booking started: sourceTicketId={}, passengerId={}, discount={}",
                sourceTicketId, passengerId, discount);

        try {
            if (discount == null || discount < 1 || discount > 100) {
                log.warn("Discounted booking rejected because discount is invalid: sourceTicketId={}, discount={}",
                        sourceTicketId, discount);
                throw new InvalidDiscountException(discount);
            }

            TicketResponse sourceTicket = ticketsClient.getTicket(sourceTicketId);
            log.debug("Source ticket loaded for discounted booking: ticketId={}, basePrice={}, seat={}, carriage={}",
                    sourceTicket.id(), sourceTicket.basePrice(),
                    sourceTicket.seatNumber(), sourceTicket.carriageNumber());

            if (sourceTicket.basePrice() == null) {
                log.warn("Discounted booking rejected because source ticket has no price: ticketId={}",
                        sourceTicketId);
                throw new TicketWithoutPriceException(sourceTicketId);
            }

            PassengerResponse passenger = clientsClient.getPassenger(passengerId, sessionToken);
            BigDecimal newBasePrice = increasePrice(sourceTicket.basePrice(), discount);
            log.debug("Calculated discounted booking price: sourcePrice={}, discount={}, resultingPrice={}",
                    sourceTicket.basePrice(), discount, newBasePrice);

            SeatPageResponse seats = ticketsClient.getSeats(
                    sourceTicket.venue().trainSetId(),
                    sourceTicket.carriageNumber()
            );

            if (seats == null || seats.content() == null) {
                log.warn("No seat data returned while creating discounted ticket: sourceTicketId={}", sourceTicketId);
                throw new NoAvailableSeatException(sourceTicketId);
            }

            log.info("Trying available seats for discounted booking: sourceTicketId={}, seatCount={}",
                    sourceTicketId, seats.content().size());

            TicketResponse newTicket = null;

            for (SeatResponse seat : seats.content()) {
                if (seat.seatNumber().equals(sourceTicket.seatNumber())) {
                    log.debug("Skipping source ticket seat: seat={}", seat.seatNumber());
                    continue;
                }

                TicketCreateRequest request = new TicketCreateRequest(
                        sourceTicket.name(),
                        sourceTicket.venue().id(),
                        sourceTicket.carriageNumber(),
                        seat.seatNumber(),
                        newBasePrice,
                        discount,
                        sourceTicket.refundable(),
                        sourceTicket.type()
                );

                log.debug("Trying to reserve candidate seat: sourceTicketId={}, seat={}",
                        sourceTicketId, seat.seatNumber());

                var created = ticketsClient.tryCreateTicket(request);

                if (created.isPresent()) {
                    newTicket = created.get();
                    log.info("Replacement ticket created: sourceTicketId={}, newTicketId={}, seat={}",
                            sourceTicketId, newTicket.id(), seat.seatNumber());
                    break;
                }
            }

            if (newTicket == null) {
                log.warn("No available replacement seat: sourceTicketId={}", sourceTicketId);
                throw new NoAvailableSeatException(sourceTicketId);
            }

            Book book;
            try {
                book = bookRepository.save(
                        newTicket.id(),
                        passenger.id(),
                        newTicket.basePrice(),
                        sourceTicket.id()
                );
            } catch (RuntimeException bookingException) {
                log.error("Database booking failed after replacement ticket creation; starting compensation: "
                                + "sourceTicketId={}, newTicketId={}, passengerId={}, exception={}",
                        sourceTicketId, newTicket.id(), passenger.id(), bookingException.toString(), bookingException);
                try {
                    ticketsClient.deleteTicket(newTicket.id());
                } catch (RuntimeException compensationException) {
                    log.error("Compensation failed after booking database failure: newTicketId={}, exception={}",
                            newTicket.id(), compensationException.toString(), compensationException);
                    bookingException.addSuppressed(compensationException);
                }

                throw bookingException;
            }

            log.info("Discounted booking completed: sourceTicketId={}, newTicketId={}, passengerId={}, price={}, elapsedMs={}",
                    sourceTicketId, book.ticketId(), book.passengerId(), book.price(), elapsedMs(startedAt));

            return new BookResponse(
                    book.ticketId(),
                    book.passengerId(),
                    book.price()
            );
        } catch (RuntimeException exception) {
            log.error("Discounted booking failed: sourceTicketId={}, passengerId={}, discount={}, elapsedMs={}, exception={}",
                    sourceTicketId, passengerId, discount, elapsedMs(startedAt), exception.toString(), exception);
            throw exception;
        }
    }

    public List<BookResponse> getPassengerTickets(
            UUID passengerId,
            String sessionToken
    ) {
        long startedAt = System.nanoTime();
        log.info("Passenger bookings query started: passengerId={}", passengerId);

        try {
            PassengerResponse passenger = clientsClient.getPassenger(passengerId, sessionToken);
            List<Book> books = bookRepository.findByPassengerId(passenger.id());

            List<BookResponse> response = books.stream()
                    .map(book -> new BookResponse(
                            book.ticketId(),
                            book.passengerId(),
                            book.price()
                    ))
                    .toList();

            log.info("Passenger bookings query completed: passengerId={}, count={}, elapsedMs={}",
                    passengerId, response.size(), elapsedMs(startedAt));
            return response;
        } catch (RuntimeException exception) {
            log.error("Passenger bookings query failed: passengerId={}, elapsedMs={}, exception={}",
                    passengerId, elapsedMs(startedAt), exception.toString(), exception);
            throw exception;
        }
    }

    public boolean isTicketSold(Long ticketId) {
        log.debug("Ticket sale status query: ticketId={}", ticketId);
        boolean sold = bookRepository.findByTicketId(ticketId).isPresent();
        log.info("Ticket sale status resolved: ticketId={}, sold={}", ticketId, sold);
        return sold;
    }

    private BigDecimal increasePrice(BigDecimal basePrice, Integer percent) {
        return basePrice
                .multiply(BigDecimal.valueOf(100L + percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
