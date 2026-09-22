package com.fuzis.ssoident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionService {
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final String cookieName;

    public SessionService(ReactiveStringRedisTemplate redis,
                          ObjectMapper mapper,
                          @Value("${sso.session.ttl}") Duration ttl,
                          @Value("${sso.session.cookie-name}") String cookieName) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
        this.cookieName = cookieName;
    }

    public Mono<SessionData> create(UUID userId) {
        String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(ttl);
        String key = redisKey(token);
        String value = userId + ":" + sessionId;

        return redis.opsForValue().set(key, value, ttl)
                .flatMap(ok -> ok
                        ? Mono.just(new SessionData(sessionId, userId, token, expiresAt))
                        : Mono.error(new IllegalStateException("Unable to store session in Redis")));
    }

    public Mono<SessionData> resolve(String token) {
        if (token == null || token.isBlank()) return Mono.empty();
        return redis.opsForValue().get(redisKey(token))
                .flatMap(value -> {
                    String[] parts = value.split(":", 2);
                    if (parts.length != 2) return Mono.empty();
                    return Mono.just(new SessionData(
                            UUID.fromString(parts[1]), UUID.fromString(parts[0]), token, Instant.now().plus(ttl)
                    ));
                });
    }

    public Mono<Boolean> revoke(String token) {
        if (token == null || token.isBlank()) return Mono.just(false);
        return redis.delete(redisKey(token)).map(v -> v > 0);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public ResponseCookie cookie(String token, Duration maxAge) {
        return ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public String cookieName() { return cookieName; }

    private String redisKey(String token) { return "sso:session:" + token; }

    public record SessionData(UUID sessionId, UUID userId, String token, Instant expiresAt) {}
}
