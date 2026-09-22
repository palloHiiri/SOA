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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

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
    }

    public BookResponse bookTicket(
            Long ticketId,
            UUID passengerId,
            String sessionToken
    ) {
        TicketResponse ticketResponse =
                ticketsClient.getTicket(ticketId);

        PassengerResponse passengerResponse =
                clientsClient.getPassenger(
                        passengerId,
                        sessionToken
                );

        if (ticketResponse.basePrice() == null) {
            throw new TicketWithoutPriceException(ticketId);
        }

        try {
            Book book = bookRepository.save(
                    ticketResponse.id(),
                    passengerResponse.id(),
                    ticketResponse.basePrice(),
                    null
            );

            return new BookResponse(
                    book.ticketId(),
                    book.passengerId(),
                    book.price()
            );

        } catch (DuplicateKeyException exception) {
            throw new TicketAlreadyBookedException(ticketId);
        }
    }

    public BookResponse bookTicketWithDiscount(
            Long sourceTicketId,
            UUID passengerId,
            Integer discount,
            String sessionToken
    ) {

        if (discount == null
                || discount < 1
                || discount > 100) {

            throw new InvalidDiscountException(discount);
        }

        TicketResponse sourceTicket = ticketsClient.getTicket(sourceTicketId);

        if (sourceTicket.basePrice() == null) {
            throw new TicketWithoutPriceException(sourceTicketId);
        }

        PassengerResponse passenger =
                clientsClient.getPassenger(passengerId, sessionToken);

        BigDecimal newBasePrice = increasePrice(sourceTicket.basePrice(), discount);

        SeatPageResponse seats =
                ticketsClient.getSeats(sourceTicket.venue().trainSetId(), sourceTicket.carriageNumber());

        if (seats == null || seats.content() == null) {
            throw new NoAvailableSeatException(sourceTicketId);
        }

        TicketResponse newTicket = null;

        for (SeatResponse seat : seats.content()) {
            if (seat.seatNumber().equals(
                    sourceTicket.seatNumber()
            )) {
                continue;
            }

            TicketCreateRequest request =
                    new TicketCreateRequest(
                            sourceTicket.name(),
                            sourceTicket.venue().id(),
                            sourceTicket.carriageNumber(),
                            seat.seatNumber(),
                            newBasePrice,
                            discount,
                            sourceTicket.refundable(),
                            sourceTicket.type()
                    );

            var created = ticketsClient.tryCreateTicket(request);

            if (created.isPresent()) {
                newTicket = created.get();
                break;
            }
        }

        if (newTicket == null) {
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
            try {
                ticketsClient.deleteTicket(newTicket.id());
            } catch (RuntimeException compensationException) {
                bookingException.addSuppressed(compensationException);
            }

            throw bookingException;
        }

        return new BookResponse(
                book.ticketId(),
                book.passengerId(),
                book.price()
        );
    }

    public List<BookResponse> getPassengerTickets(
            UUID passengerId,
            String sessionToken
    ) {
        PassengerResponse passenger = clientsClient.getPassenger(
                passengerId,
                sessionToken
        );

        return bookRepository.findByPassengerId(passenger.id()).stream()
                .map(book -> new BookResponse(
                        book.ticketId(),
                        book.passengerId(),
                        book.price()
                ))
                .toList();
    }

    public boolean isTicketSold(Long ticketId) {
        return bookRepository.findByTicketId(ticketId).isPresent();
    }

    private BigDecimal increasePrice(
            BigDecimal basePrice,
            Integer percent
    ) {
        return basePrice
                .multiply(BigDecimal.valueOf(100L + percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}