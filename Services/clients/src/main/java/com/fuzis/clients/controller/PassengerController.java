package com.fuzis.clients.controller;

import com.fuzis.clients.dto.PassengerCreateRequest;
import com.fuzis.clients.dto.PassengerResponse;
import com.fuzis.clients.service.PassengerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients-srv/client/passengers")
public class PassengerController {
    private final PassengerService service;
    public PassengerController(PassengerService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<PassengerResponse> list(@RequestHeader("X-User-Id") String userId) {
        return service.list(ClientController.parseUserId(userId));
    }

    @GetMapping("/{id}")
    public Mono<PassengerResponse> get(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id) {
        return service.get(ClientController.parseUserId(userId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PassengerResponse> create(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody PassengerCreateRequest request) {
        return service.create(ClientController.parseUserId(userId), request);
    }

    @PutMapping("/{id}")
    public Mono<PassengerResponse> update(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody PassengerCreateRequest request) {
        return service.update(ClientController.parseUserId(userId), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id) {
        return service.delete(ClientController.parseUserId(userId), id);
    }
}
