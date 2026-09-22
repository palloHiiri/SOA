package com.fuzis.clients.service;

import com.fuzis.clients.dto.ClientProfileResponse;
import com.fuzis.clients.dto.ClientProfileUpdateRequest;
import com.fuzis.clients.repository.ClientRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ClientService {
    private final ClientRepository clients;
    public ClientService(ClientRepository clients) {
        this.clients = clients;
    }
    public Mono<ClientProfileResponse> profile(UUID userId) {
        return clients.findProfileByUserId(userId)
        .switchIfEmpty(Mono.error(new IllegalStateException("Client not found")));
    }

    public Mono<ClientProfileResponse> updateProfile(UUID userId, ClientProfileUpdateRequest request) {
        if (!request.extraFields().isObject()) {
            return Mono.error(new IllegalArgumentException("extraFields must be a JSON object"));
        }
        return clients.updateProfile(userId, request)
        .flatMap(ignored -> profile(userId));
    }
}
