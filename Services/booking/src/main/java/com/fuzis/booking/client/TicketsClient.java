package com.fuzis.booking.client;

import com.fuzis.booking.client.dto.SeatPageResponse;
import com.fuzis.booking.client.dto.TicketCreateRequest;
import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.exception.TicketNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class TicketsClient {
    private final RestClient restClient;

    public TicketsClient(){
        this.restClient = RestClient.builder()
                .baseUrl("http://157.22.189.188:8082/api/v1/tickets/")
                .build();
    }

    public TicketResponse getTicket(Long ticketId){
        try {
            return restClient.get()
                    .uri("tickets/{ticketId}", ticketId)
                    .retrieve()
                    .body(TicketResponse.class);
        }catch (HttpClientErrorException.NotFound exception){
            throw new TicketNotFoundException(ticketId);
        }
    }

    public SeatPageResponse getSeats(
            Integer trainSetId,
            String carriageNumber
    ) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "train-sets/{trainSetId}/carriages/{carriageNumber}/seats"
                        )
                        .queryParam("page", 1)
                        .queryParam("size", 100)
                        .build(trainSetId, carriageNumber)
                )
                .retrieve()
                .body(SeatPageResponse.class);
    }
    public Optional<TicketResponse> tryCreateTicket(
            TicketCreateRequest request
    ) {
        try {
            TicketResponse ticket = restClient.post()
                    .uri("tickets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TicketResponse.class);

            return Optional.ofNullable(ticket);

        } catch (HttpClientErrorException.Conflict exception) {
            return Optional.empty();
        }
    }

    public void deleteTicket(Long ticketId) {
        restClient.delete()
                .uri("tickets/{ticketId}", ticketId)
                .retrieve()
                .toBodilessEntity();
    }
}
