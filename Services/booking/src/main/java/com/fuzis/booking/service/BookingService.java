package com.fuzis.booking.service;

import com.fuzis.booking.client.ClientsClient;
import com.fuzis.booking.client.TicketsClient;
import com.fuzis.booking.client.dto.PassengerResponse;
import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.dto.BookResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class BookingService {
    private final TicketsClient ticketsClient;
    private final ClientsClient clientsClient;

    public BookingService(TicketsClient ticketsClient, ClientsClient clientsClient) {
        this.ticketsClient = ticketsClient;
        this.clientsClient = clientsClient;
    }
    public BookResponse bookTicket(
            Long ticketId,
            UUID passengerId,
            BigDecimal price,
            String sessionToken
    ) {

        TicketResponse ticketResponse = ticketsClient.getTicket(ticketId);
        PassengerResponse passengerResponse = clientsClient.getPassenger(passengerId, sessionToken);

        System.out.println(
                "Ticket: " + ticketResponse.name()
        );

        System.out.println(
                "Passenger: "
                        + passengerResponse.firstName()
                        + " "
                        + passengerResponse.lastName()
        );

        return new BookResponse(ticketId, passengerId, price);
    }
}
