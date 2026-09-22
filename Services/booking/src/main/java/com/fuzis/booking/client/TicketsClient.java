package com.fuzis.booking.client;

import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.exception.TicketNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

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
}
