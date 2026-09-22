package com.fuzis.booking.client;

import com.fuzis.booking.client.dto.PassengerResponse;
import com.fuzis.booking.client.dto.TicketResponse;
import com.fuzis.booking.exception.TicketNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ClientsClient {
    private final RestClient restClient;

    public ClientsClient(){
        this.restClient = RestClient.builder()
                .baseUrl("http://157.22.189.188:8082/api/v1/clients-srv/")
                .build();
    }

    public PassengerResponse getPassenger(
            UUID passengerId,
            String sessionToken
    ) {
        return restClient
                .get()
                .uri("client/passengers/{passengerId}", passengerId)
                .header("Cookie", "SESSION=" + sessionToken)
                .retrieve()
                .body(PassengerResponse.class);
    }
}
