package com.fuzis.booking.client;

import com.fuzis.booking.client.dto.PassengerResponse;
import com.fuzis.booking.exception.AccessDeniedException;
import com.fuzis.booking.exception.PassengerNotFoundException;
import com.fuzis.booking.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class ClientsClient {

    private static final Logger log = LoggerFactory.getLogger(ClientsClient.class);

    private final RestClient restClient;

    public ClientsClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://sso-oathkeeper:4455/api/v1/clients-srv/")
                .build();

        log.info("ClientsClient initialized with internal Oathkeeper endpoint");
    }

    public PassengerResponse getPassenger(
            UUID passengerId,
            String sessionToken
    ) {
        long startedAt = System.nanoTime();
        log.debug("Calling clients service through Oathkeeper: passengerId={}", passengerId);

        try {
            PassengerResponse response = restClient
                    .get()
                    .uri("client/passengers/{passengerId}", passengerId)
                    .header("Cookie", "SESSION=" + sessionToken)
                    .retrieve()
                    .body(PassengerResponse.class);

            log.info("Clients service returned passenger: passengerId={}, elapsedMs={}",
                    passengerId, elapsedMs(startedAt));
            return response;

        } catch (HttpClientErrorException.Unauthorized exception) {
            log.warn("Clients service rejected session: passengerId={}, status={}",
                    passengerId, exception.getStatusCode());
            throw new UnauthorizedException();

        } catch (HttpClientErrorException.Forbidden exception) {
            log.warn("Clients service denied passenger access: passengerId={}, status={}",
                    passengerId, exception.getStatusCode());
            throw new AccessDeniedException();

        } catch (HttpClientErrorException.NotFound exception) {
            log.warn("Passenger not found: passengerId={}, status={}",
                    passengerId, exception.getStatusCode());
            throw new PassengerNotFoundException(passengerId);

        } catch (RestClientResponseException exception) {
            log.error("Clients service returned unexpected HTTP error: passengerId={}, status={}, elapsedMs={}, body={}",
                    passengerId, exception.getStatusCode(), elapsedMs(startedAt),
                    truncate(exception.getResponseBodyAsString()), exception);
            throw exception;

        } catch (RuntimeException exception) {
            log.error("Clients service call failed: passengerId={}, elapsedMs={}, exception={}",
                    passengerId, elapsedMs(startedAt), exception.toString(), exception);
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
