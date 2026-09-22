package com.fuzis.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuzis.inventory.dto.TrainSetLifecycleRequest;
import com.fuzis.inventory.dto.TrainSetLifecycleResponse;
import com.fuzis.inventory.dto.TrainSetResponse;
import com.fuzis.inventory.repository.InventoryRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class InventoryService {
    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public TrainSetResponse importTrainSet(JsonNode payload) {
        validateImportPayload(payload);
        try {
            return repository.importTrainSet(payload);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Train set import conflicts with existing inventory data", e);
        } catch (DataAccessException e) {
            if (sqlState(e).equals("P0001") || sqlState(e).equals("22023")) {
                throw new BadRequestException(message(e, "Invalid train set import"), e);
            }
            throw e;
        }
    }

    public TrainSetLifecycleResponse changeLifecycle(int trainSetId, TrainSetLifecycleRequest request) {
        String status = request.status().trim().toUpperCase(java.util.Locale.ROOT);
        try {
            if (!repository.existsTrainSet(trainSetId)) {
                throw new NotFoundException("Train set not found");
            }
            if (!repository.lifecycleStatusExists(status)) {
                throw new BadRequestException("Unknown lifecycle status: " + status);
            }
            return repository.changeLifecycle(trainSetId, status);
        } catch (DataAccessException e) {
            if (sqlState(e).equals("P0001") || sqlState(e).equals("22023")) {
                throw new BadRequestException(message(e, "Invalid train set lifecycle transition"), e);
            }
            throw e;
        }
    }

    private static void validateImportPayload(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new BadRequestException("Import payload must be a JSON object");
        }
        if (payload.has("id") || payload.has("lifecycleStatus")) {
            throw new BadRequestException("Import payload must not contain database identifiers or lifecycle status");
        }
        for (String field : new String[]{"code", "buildNumber", "name", "technicalName", "carriages"}) {
            if (!payload.has(field)) {
                throw new BadRequestException("Import payload is missing " + field);
            }
        }
        if (!payload.path("carriages").isArray()) {
            throw new BadRequestException("carriages must be an array");
        }
    }

    private static String sqlState(DataAccessException e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            current = current.getCause();
        }
        return "";
    }

    private static String message(DataAccessException e, String fallback) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getMessage() != null && !sql.getMessage().isBlank()) {
                return sql.getMessage();
            }
            current = current.getCause();
        }
        return fallback;
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message, Throwable cause) { super(message, cause); }
    }
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
        public BadRequestException(String message, Throwable cause) { super(message, cause); }
    }
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }
}
