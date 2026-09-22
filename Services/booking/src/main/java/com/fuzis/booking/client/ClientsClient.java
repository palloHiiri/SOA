package com.fuzis.booking.client;

import com.fuzis.booking.client.dto.PassengerResponse;
import com.fuzis.booking.exception.AccessDeniedException;
import com.fuzis.booking.exception.PassengerNotFoundException;
import com.fuzis.booking.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ClientsClient {

    private final RestClient restClient;

    public ClientsClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://sso-oathkeeper:4455/api/v1/clients-srv/")
                .build();
    }

    public PassengerResponse getPassenger(
            UUID passengerId,
            String sessionToken
    ) {
        try {
            return restClient
                    .get()
                    .uri("client/passengers/{passengerId}", passengerId)
                    .header("Cookie", "SESSION=" + sessionToken)
                    .retrieve()
                    .body(PassengerResponse.class);

        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new UnauthorizedException();

        } catch (HttpClientErrorException.Forbidden exception) {
            throw new AccessDeniedException();

        } catch (HttpClientErrorException.NotFound exception) {
            throw new PassengerNotFoundException(passengerId);
        }
    }
}