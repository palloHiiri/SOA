package com.fuzis.ssoident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuzis.ssoident.repository.HistoryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final HistoryRepository history;
    private final ObjectMapper mapper;

    public AuditService(HistoryRepository history, ObjectMapper mapper) {
        this.history = history;
        this.mapper = mapper;
    }

    public Mono<Void> write(UUID userId, String action, UUID actorUserId, String ip,
                            String userAgent, boolean success, Map<String, Object> metadata) {
        try {
            return history.write(userId, action, actorUserId, ip, userAgent, success,
                    mapper.writeValueAsString(metadata == null ? Map.of() : metadata));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
