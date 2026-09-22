package com.fuzis.clients.service;

import com.fuzis.clients.dto.PassengerCreateRequest;
import com.fuzis.clients.dto.PassengerResponse;
import com.fuzis.clients.repository.ClientRepository;
import com.fuzis.clients.repository.PassengerRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class PassengerService {
    private final ClientRepository clients;
    private final PassengerRepository passengers;

    public PassengerService(ClientRepository clients, PassengerRepository passengers) {
        this.clients = clients;
        this.passengers = passengers;
    }

    private Mono<UUID> clientId(UUID userId) {
        return clients.findClientIdByUserId(userId).switchIfEmpty(Mono.error(new IllegalStateException("Client not found")));
    }

    public Flux<PassengerResponse> list(UUID userId) {
        return clientId(userId).flatMapMany(passengers::findAllByClientId);
    }
    public Mono<PassengerResponse> get(UUID userId, UUID id) {
        return clientId(userId).flatMap(cid -> passengers.findByIdAndClientId(id, cid)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Passenger not found"))));
    }
    public Mono<PassengerResponse> create(UUID userId, PassengerCreateRequest request) {
        return clientId(userId).flatMap(cid -> passengers.create(cid, request).flatMap(id -> passengers.findByIdAndClientId(id, cid)));
    }
    public Mono<PassengerResponse> update(UUID userId, UUID id, PassengerCreateRequest request) {
        return clientId(userId).flatMap(cid -> passengers.update(id, cid, request)
        .flatMap(rows -> rows == 1 ? passengers.findByIdAndClientId(id, cid) : Mono.error(new IllegalArgumentException("Passenger not found"))));
    }
    public Mono<Void> delete(UUID userId, UUID id) {
        return clientId(userId).flatMap(cid -> passengers.delete(id, cid)
        .flatMap(rows -> rows == 1 ? Mono.empty() : Mono.error(new IllegalArgumentException("Passenger not found"))));
    }
}
