package com.fuzis.clients.controller;

import com.fuzis.clients.dto.ClientProfileResponse;
import com.fuzis.clients.dto.ClientProfileUpdateRequest;
import com.fuzis.clients.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients-srv/client")
public class ClientController {
    private final ClientService service;
    public ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public Mono<ClientProfileResponse> profile(@RequestHeader("X-User-Id") String userId) {
        return service.profile(parseUserId(userId));
    }

    @PutMapping("/profile")
    public Mono<ClientProfileResponse> updateProfile(
    @RequestHeader("X-User-Id") String userId,
    @Valid
    @RequestBody
    ClientProfileUpdateRequest request) {
        return service.updateProfile(parseUserId(userId), request);
    }

    static UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Invalid X-User-Id");
        }
    }
}
