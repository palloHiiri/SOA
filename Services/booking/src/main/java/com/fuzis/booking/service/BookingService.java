package com.fuzis.booking.service;

import com.fuzis.booking.client.ClientsClient;
import com.fuzis.booking.client.TicketsClient;
import com.fuzis.booking.client.dto.PassengerResponse;
import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.dto.BookResponse;
import com.fuzis.booking.exception.TicketAlreadyBookedException;
import com.fuzis.booking.model.Book;
import com.fuzis.booking.repository.BookRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            throw new IllegalStateException(
                    "Ticket " + ticketId + " has no price"
            );
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
}