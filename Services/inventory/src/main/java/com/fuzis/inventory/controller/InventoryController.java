package com.fuzis.inventory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuzis.inventory.dto.ErrorResponse;
import com.fuzis.inventory.dto.TrainSetLifecycleRequest;
import com.fuzis.inventory.dto.TrainSetLifecycleResponse;
import com.fuzis.inventory.dto.TrainSetResponse;
import com.fuzis.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping(value = "/train-sets/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrainSetResponse importTrainSet(@RequestBody JsonNode payload) {
        return service.importTrainSet(payload);
    }

    @PostMapping("/train-sets/{trainSetId}/lifecycle")
    public TrainSetLifecycleResponse changeLifecycle(
    @PathVariable int trainSetId,
    @Valid
    @RequestBody
    TrainSetLifecycleRequest request) {
        return service.changeLifecycle(trainSetId, request);
    }

    @RestControllerAdvice
    static class ErrorHandler {
        @ExceptionHandler(InventoryService.BadRequestException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        ErrorResponse badRequest(InventoryService.BadRequestException e) {
            return new ErrorResponse("BAD_REQUEST", e.getMessage());
        }

        @ExceptionHandler(InventoryService.ConflictException.class)
        @ResponseStatus(HttpStatus.CONFLICT)
        ErrorResponse conflict(InventoryService.ConflictException e) {
            return new ErrorResponse("CONFLICT", e.getMessage());
        }

        @ExceptionHandler(InventoryService.NotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        ErrorResponse notFound(InventoryService.NotFoundException e) {
            return new ErrorResponse("NOT_FOUND", e.getMessage());
        }
    }
}
