package com.fuzis.booking.client;

import com.fuzis.booking.client.dto.SeatResponse;
import com.fuzis.booking.client.dto.TicketCreateRequest;
import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.exception.TicketNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Objects;
import java.util.Optional;

@Component
public class TicketsClient {
    private static final Logger log = LoggerFactory.getLogger(TicketsClient.class);

    private final RestClient restClient;

    public TicketsClient() {
        this.restClient =
                RestClient.builder().baseUrl("http://sso-oathkeeper:4455/api/v1/tickets/").build();

        log.info("TicketsClient initialized with internal Oathkeeper endpoint");
    }

    public TicketResponse getTicket(Long ticketId) {
        long startedAt = System.nanoTime();
        log.debug("Calling tickets service: getTicket ticketId={}", ticketId);

        try {
            TicketResponse response =
                    restClient
                            .get()
                            .uri("tickets/{ticketId}", ticketId)
                            .retrieve()
                            .body(TicketResponse.class);

            log.info(
                    "Tickets service returned ticket: ticketId={}, elapsedMs={}",
                    ticketId,
                    elapsedMs(startedAt));
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            log.warn(
                    "Ticket not found: ticketId={}, status={}",
                    ticketId,
                    exception.getStatusCode());
            throw new TicketNotFoundException(ticketId);
        } catch (RestClientResponseException exception) {
            log.error(
                    "Tickets service returned unexpected HTTP error: ticketId={}, status={},"
                        + " elapsedMs={}, body={}",
                    ticketId,
                    exception.getStatusCode(),
                    elapsedMs(startedAt),
                    truncate(exception.getResponseBodyAsString()),
                    exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "Tickets service call failed: ticketId={}, elapsedMs={}, exception={}",
                    ticketId,
                    elapsedMs(startedAt),
                    exception.toString(),
                    exception);
            throw exception;
        }
    }

    public Optional<SeatResponse> findAvailableSeat(Long sourceTicketId) {
        SeatResponse seat =
                restClient
                        .get()
                        .uri("tickets/{ticketId}/available-seat", sourceTicketId)
                        .retrieve()
                        .body(SeatResponse.class);
        return Optional.ofNullable(seat);
    }

    public Optional<TicketResponse> tryCreateTicket(TicketCreateRequest request) {
        long startedAt = System.nanoTime();
        log.debug(
                "Calling tickets service: tryCreateTicket name={}, carriage={}, seat={}",
                request.name(),
                request.carriageNumber(),
                request.seatNumber());

        try {
            TicketResponse ticket =
                    restClient
                            .post()
                            .uri("tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(TicketResponse.class);

            log.info(
                    "Tickets service created ticket: ticketId={}, elapsedMs={}",
                    ticket == null ? null : ticket.id(),
                    elapsedMs(startedAt));
            return Optional.of(
                    Objects.requireNonNull(ticket, "Ticket creation returned an empty response"));

        } catch (HttpClientErrorException.Conflict exception) {
            log.debug(
                    "Ticket creation conflict (seat already occupied): carriage={}, seat={}",
                    request.carriageNumber(),
                    request.seatNumber());
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            log.error(
                    "Tickets service returned unexpected ticket-creation error: status={},"
                        + " elapsedMs={}, body={}",
                    exception.getStatusCode(),
                    elapsedMs(startedAt),
                    truncate(exception.getResponseBodyAsString()),
                    exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "Ticket creation call failed: carriage={}, seat={}, elapsedMs={}, exception={}",
                    request.carriageNumber(),
                    request.seatNumber(),
                    elapsedMs(startedAt),
                    exception.toString(),
                    exception);
            throw exception;
        }
    }

    public void deleteTicket(Long ticketId) {
        long startedAt = System.nanoTime();
        log.warn("Deleting compensating ticket: ticketId={}", ticketId);

        try {
            restClient.delete().uri("tickets/{ticketId}", ticketId).retrieve().toBodilessEntity();

            log.info(
                    "Compensating ticket deleted: ticketId={}, elapsedMs={}",
                    ticketId,
                    elapsedMs(startedAt));
        } catch (RuntimeException exception) {
            log.error(
                    "Compensating ticket deletion failed: ticketId={}, elapsedMs={}, exception={}",
                    ticketId,
                    elapsedMs(startedAt),
                    exception.toString(),
                    exception);
            throw exception;
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000) + "...";
    }
}
