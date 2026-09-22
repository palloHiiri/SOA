package com.fuzis.ssoident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuzis.ssoident.dto.*;
import com.fuzis.ssoident.repository.HistoryRepository;
import com.fuzis.ssoident.repository.SessionRepository;
import com.fuzis.ssoident.repository.UserRepository;
import com.fuzis.ssoident.repository.UserWriteRepository;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserWriteRepository writes;
    private final AuditService audit;
    private final SessionRepository sessions;
    private final SessionService sessionService;
    private final KafkaEventPublisher kafkaPublisher;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final TransactionalOperator tx;
    private final Argon2PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public AuthService(UserRepository users, UserWriteRepository writes, AuditService audit,
                       SessionRepository sessions, SessionService sessionService, KafkaEventPublisher kafkaPublisher,
                       ReactiveStringRedisTemplate redis, ObjectMapper mapper, TransactionalOperator tx) {
        this.users = users;
        this.writes = writes;
        this.audit = audit;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.kafkaPublisher = kafkaPublisher;
        this.redis = redis;
        this.mapper = mapper;
        this.tx = tx;
    }

    public Mono<AuthResult> login(LoginRequest request, String ip, String userAgent) {
        String login = request.login().trim().toLowerCase(java.util.Locale.ROOT);
        return users.findUserIdByLogin(login)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid credentials")))
                .flatMap(userId -> users.findPasswordHash(userId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid credentials")))
                        .flatMap(hash -> passwordEncoder.matches(request.password(), hash)
                                ? authenticateUser(userId, ip, userAgent)
                                : Mono.error(new IllegalArgumentException("Invalid credentials"))));
    }

    private Mono<AuthResult> authenticateUser(UUID userId, String ip, String userAgent) {
        return users.isEmail2faEnabled(userId)
                .flatMap(enabled -> enabled ? createMfaChallenge(userId, ip, userAgent)
                        .map(r -> AuthResult.mfa(r)) : createSession(userId, ip, userAgent).map(AuthResult::authenticated));
    }

    public Mono<VerificationResult> requestMfa(UUID challengeId, String ip, String userAgent) {
        return getChallenge(challengeId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown MFA challenge")))
                .flatMap(challenge -> users.findAttribute(challenge.userId(), "email")
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Email not found")))
                        .flatMap(email -> createAndSendVerification(challenge.userId(), email, challengeId, "EMAIL_2FA", ip, userAgent))
                        .flatMap(result -> redis.opsForValue()
                                .set(challengeKey(challengeId), json(new Challenge(challenge.userId(), result.codeId(), result.expiresAt())), Duration.ofMinutes(5))
                                .then(redis.opsForValue().set(attemptsKey(challengeId), "0", Duration.ofMinutes(5)))
                                .thenReturn(result)));
    }

    public Mono<AuthResult> verifyMfa(UUID challengeId, String code, String ip, String userAgent) {
        return getChallenge(challengeId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid MFA challenge")))
                .flatMap(challenge -> incrementAttempts(challengeId)
                        .flatMap(attempts -> {
                            if (attempts > 5) return Mono.error(new IllegalArgumentException("Too many attempts"));
                            return writes.findActiveVerificationCode(challenge.codeId())
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid MFA code")))
                                    .flatMap(row -> {
                                        if (!row.status().equals("ACTIVE") || row.expiresAt().isBefore(Instant.now()))
                                            return Mono.error(new IllegalArgumentException("Invalid or expired MFA code"));
                                        if (!sha256(code).equals(row.codeHash()))
                                            return Mono.error(new IllegalArgumentException("Invalid MFA code"));
                                        return tx.transactional(writes.useVerificationCode(row.id())
                                                        .then(audit.write(row.userId(), "LOGIN", row.userId(), ip, userAgent, true,
                                                                Map.of("mfa", "EMAIL"))))
                                                .then(redis.delete(challengeKey(challengeId)))
                                                .then(redis.delete(attemptsKey(challengeId)))
                                                .then(createSession(row.userId(), ip, userAgent))
                                                .map(AuthResult::authenticated);
                                    });
                        }));
    }

    private Mono<MfaChallenge> createMfaChallenge(UUID userId, String ip, String userAgent) {
        UUID challengeId = UUID.randomUUID();
        return users.findAttribute(userId, "email")
                .flatMap(email -> createAndSendVerification(userId, email, challengeId, "EMAIL_2FA", ip, userAgent))
                .map(r -> new MfaChallenge(userId, challengeId, r.codeId()));
    }

    private Mono<VerificationResult> createAndSendVerification(UUID userId, String email, UUID challengeId,
                                                                 String type, String ip, String userAgent) {
        String code = "%06d".formatted(ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expires = Instant.now().plus(Duration.ofMinutes(type.equals("EMAIL_2FA") ? 5 : 15));
        return tx.transactional(writes.createVerificationCode(userId, type, email, sha256(code), expires)
                        .flatMap(codeId -> audit.write(userId, "LOGIN", userId, ip, userAgent, true,
                                Map.of("verificationCodeType", type, "status", "CODE_CREATED"))
                                .thenReturn(codeId)))
                .flatMap(codeId -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("event", "SSO_IDENT_VERIFICATION_CODE_CREATED");
                    event.put("userId", userId.toString());
                    event.put("codeId", codeId.toString());
                    event.put("challengeId", challengeId.toString());
                    event.put("type", type);
                    event.put("channel", "EMAIL");
                    event.put("destination", email);
                    event.put("code", code);
                    event.put("expiresAt", expires.toString());
                    return kafkaPublisher.publishVerificationCode(event)
                            .then(redis.opsForValue().set(challengeKey(challengeId), json(new Challenge(userId, codeId, expires)), Duration.ofMinutes(5)))
                            .then(redis.opsForValue().set(attemptsKey(challengeId), "0", Duration.ofMinutes(5)))
                            .thenReturn(new VerificationResult(challengeId, codeId, expires));
                })
                .onErrorResume(ex -> tx.transactional(audit.write(userId, "LOGIN", userId, ip, userAgent, false,
                        Map.of("verificationCodeType", type, "reason", rootMessage(ex)))).then(Mono.error(ex)));
    }

    /**
     * Starts a password reset flow. The public endpoint deliberately returns the same
     * successful result for both existing and unknown email addresses.
     */
    public Mono<Void> requestPasswordReset(String rawEmail, String ip, String userAgent) {
        String email = RegistrationService.canonicalEmail(rawEmail);
        return users.findActiveUserIdByEmail(email)
                .flatMap(userId -> createPasswordResetCode(userId, email, ip, userAgent))
                .then();
    }

    private Mono<UUID> createPasswordResetCode(UUID userId, String email, String ip, String userAgent) {
        UUID code = UUID.randomUUID();
        Instant expires = Instant.now().plus(Duration.ofMinutes(15));

        return tx.transactional(
                        writes.createVerificationCode(
                                        code, userId, "PASSWORD_RESET", email, sha256(code.toString()), expires)
                                .flatMap(codeId -> audit.write(
                                                userId,
                                                "REQUEST_PASSWORD_RESET",
                                                null,
                                                ip,
                                                userAgent,
                                                true,
                                                Map.of("verificationCodeType", "PASSWORD_RESET", "status", "CODE_CREATED"))
                                        .thenReturn(codeId))
                )
                .flatMap(codeId -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("event", "SSO_IDENT_VERIFICATION_CODE_CREATED");
                    event.put("userId", userId.toString());
                    event.put("codeId", codeId.toString());
                    event.put("challengeId", codeId.toString());
                    event.put("type", "PASSWORD_RESET");
                    event.put("channel", "EMAIL");
                    event.put("destination", email);
                    event.put("code", code.toString());
                    event.put("expiresAt", expires.toString());

                    PasswordResetChallenge challenge =
                            new PasswordResetChallenge(userId, codeId, expires);
                    return kafkaPublisher.publishVerificationCode(event)
                            .then(redis.opsForValue().set(
                                    resetKey(code), json(challenge), Duration.ofMinutes(15)))
                            .then(redis.opsForValue().set(
                                    resetAttemptsKey(code), "0", Duration.ofMinutes(15)))
                            .thenReturn(codeId);
                })
                .onErrorResume(ex ->
                        tx.transactional(audit.write(
                                        userId,
                                        "REQUEST_PASSWORD_RESET",
                                        null,
                                        ip,
                                        userAgent,
                                        false,
                                        Map.of("reason", rootMessage(ex))))
                                .then(Mono.error(ex)));
    }

    public Mono<Void> confirmPasswordReset(UUID code, String newPassword, String ip, String userAgent) {
        if (code == null) {
            return Mono.error(new IllegalArgumentException("Reset code is required"));
        }

        return incrementResetAttempts(code)
                .flatMap(attempts -> {
                    if (attempts > 5) {
                        return Mono.error(new IllegalArgumentException("Too many reset attempts"));
                    }

                    return getResetChallenge(code)
                            .flatMap(challenge -> writes.findActiveVerificationCode(challenge.codeId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid password reset code")))
                            .flatMap(row -> confirmResetCode(row, code, newPassword, ip, userAgent));
                });
    }

    private Mono<Void> confirmResetCode(UserWriteRepository.VerificationRow row, UUID code,
                                        String newPassword, String ip, String userAgent) {
        if (!"ACTIVE".equals(row.status()) || row.expiresAt().isBefore(Instant.now())) {
            return Mono.error(new IllegalArgumentException("Invalid or expired password reset code"));
        }
        if (!sha256(code.toString()).equals(row.codeHash())) {
            return Mono.error(new IllegalArgumentException("Invalid password reset code"));
        }

        String passwordHash = passwordEncoder.encode(newPassword);
        return tx.transactional(
                        writes.useVerificationCode(row.id())
                                .flatMap(updated -> updated == 1
                                        ? writes.updatePasswordHash(row.userId(), passwordHash)
                                                .flatMap(passwordUpdated -> passwordUpdated == 1
                                                        ? audit.write(
                                                                row.userId(),
                                                                "CONFIRM_PASSWORD_RESET",
                                                                null,
                                                                ip,
                                                                userAgent,
                                                                true,
                                                                Map.of("verificationCodeType", "PASSWORD_RESET"))
                                                        : Mono.error(new IllegalStateException("Password credential not found")))
                                        : Mono.error(new IllegalArgumentException("Invalid or already used password reset code")))
                )
                .then(redis.delete(resetKey(code)))
                .then(redis.delete(resetAttemptsKey(code)))
                .then();
    }

    public Mono<Void> changePassword(UUID userId, String currentPassword, String newPassword,
                                      String ip, String userAgent) {
        return users.findPasswordHash(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Password credential not found")))
                .flatMap(hash -> passwordEncoder.matches(currentPassword, hash)
                        ? Mono.just(hash)
                        : Mono.error(new IllegalStateException("Current password is incorrect")))
                .flatMap(ignored -> {
                    String newHash = passwordEncoder.encode(newPassword);
                    return tx.transactional(
                                    writes.updatePasswordHash(userId, newHash)
                                            .flatMap(updated -> updated == 1
                                                    ? audit.write(
                                                            userId,
                                                            "CHANGE_PASSWORD",
                                                            userId,
                                                            ip,
                                                            userAgent,
                                                            true,
                                                            Map.of())
                                                    : Mono.error(new IllegalStateException("Password credential not found")))
                            )
                            .then();
                });
    }

    public Mono<MeResponse> updateProfile(UUID userId, ProfileUpdateRequest request,
                                           String ip, String userAgent) {
        if (request.isEmpty()) {
            return Mono.error(new IllegalArgumentException("At least one profile field is required"));
        }

        return users.findStatus(userId)
                .filter("ACTIVE"::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("User is not active")))
                .then(validateProfileUpdate(userId, request))
                .then(applyProfileUpdate(userId, request))
                .then(users.findAttributes(userId))
                .map(attrs -> new MeResponse(userId, userId.toString(), "ACTIVE", attrs))
                .flatMap(response -> auditProfileChange(userId, request, ip, userAgent).thenReturn(response));
    }

    private Mono<Void> validateProfileUpdate(UUID userId, ProfileUpdateRequest request) {
        Mono<Void> emailCheck = request.email() == null
                ? Mono.empty()
                : users.existsAttributeValueForAnotherUser(
                                userId, "email", RegistrationService.canonicalEmail(request.email()))
                        .flatMap(exists -> exists
                                ? Mono.error(new IllegalArgumentException("Email is already in use"))
                                : Mono.empty());

        Mono<Void> usernameCheck = request.username() == null
                ? Mono.empty()
                : users.existsAttributeValueForAnotherUser(
                                userId, "username", RegistrationService.canonicalUsername(request.username()))
                        .flatMap(exists -> exists
                                ? Mono.error(new IllegalArgumentException("Username is already in use"))
                                : Mono.empty());

        return emailCheck.then(usernameCheck);
    }

    private Mono<Void> applyProfileUpdate(UUID userId, ProfileUpdateRequest request) {
        Mono<Void> email = request.email() == null
                ? Mono.empty()
                : writes.upsertAttribute(userId, "email", RegistrationService.canonicalEmail(request.email())).then();
        Mono<Void> username = request.username() == null
                ? Mono.empty()
                : writes.upsertAttribute(userId, "username", RegistrationService.canonicalUsername(request.username())).then();
        Mono<Void> firstName = request.firstName() == null
                ? Mono.empty()
                : writes.upsertAttribute(userId, "first_name", request.firstName().trim()).then();
        Mono<Void> lastName = request.lastName() == null
                ? Mono.empty()
                : writes.upsertAttribute(userId, "last_name", request.lastName().trim()).then();

        return tx.transactional(
                email.then(username).then(firstName).then(lastName)
                        .then(writes.insertIdentityCdc(userId))
                        .then()
        );
    }

    private Mono<Void> auditProfileChange(UUID userId, ProfileUpdateRequest request, String ip, String userAgent) {
        if (request.email() == null) {
            return Mono.empty();
        }
        return audit.write(
                userId,
                "CHANGE_EMAIL",
                userId,
                ip,
                userAgent,
                true,
                Map.of("changed", "email"));
    }

    private Mono<AuthSession> createSession(UUID userId, String ip, String userAgent) {
        return sessionService.create(userId)
                .flatMap(session -> tx.transactional(
                        sessions.create(
                                        session.sessionId(),
                                        userId,
                                        sessionService.hashToken(session.token()),
                                        session.expiresAt(),
                                        ip,
                                        userAgent
                                )
                                .then(audit.write(
                                        userId,
                                        "CREATE_SESSION",
                                        userId,
                                        ip,
                                        userAgent,
                                        true,
                                        Map.of("sessionId", session.sessionId().toString())
                                ))
                                .thenReturn(
                                        new AuthSession(
                                                session.sessionId(),
                                                userId,
                                                session.token(),
                                                session.expiresAt()
                                        )
                                )
                ));
    }

    private Mono<PasswordResetChallenge> getResetChallenge(UUID code) {
        return redis.opsForValue().get(resetKey(code)).flatMap(value -> {
            try {
                return Mono.just(mapper.readValue(value, PasswordResetChallenge.class));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }).switchIfEmpty(writes.findActiveVerificationCode(code)
                .map(row -> new PasswordResetChallenge(row.userId(), row.id(), row.expiresAt())));
    }

    private Mono<Challenge> getChallenge(UUID challengeId) {
        return redis.opsForValue().get(challengeKey(challengeId)).flatMap(value -> {
            try { return Mono.just(mapper.readValue(value, Challenge.class)); }
            catch (Exception e) { return Mono.error(e); }
        });
    }

    private Mono<Long> incrementAttempts(UUID challengeId) {
        return redis.opsForValue().increment(attemptsKey(challengeId));
    }

    private Mono<Long> incrementResetAttempts(UUID code) {
        return redis.opsForValue().increment(resetAttemptsKey(code))
                .flatMap(attempts -> redis.expire(resetAttemptsKey(code), Duration.ofMinutes(15))
                        .thenReturn(attempts));
    }

    public record AuthResult(LoginResponse response, String sessionToken, Duration maxAge) {
        static AuthResult authenticated(AuthSession s) {
            return new AuthResult(LoginResponse.authenticated(s.userId()), s.token(), Duration.between(Instant.now(), s.expiresAt()));
        }
        static AuthResult mfa(MfaChallenge c) {
            return new AuthResult(LoginResponse.mfaRequired(c.userId(), c.challengeId()), null, Duration.ZERO);
        }
    }
    public record AuthSession(UUID sessionId, UUID userId, String token, Instant expiresAt) {}
    public record MfaChallenge(UUID userId, UUID challengeId, UUID codeId) {}
    public record VerificationResult(UUID challengeId, UUID codeId, Instant expiresAt) {}
    public record Challenge(UUID userId, UUID codeId, Instant expiresAt) {}
    public record PasswordResetChallenge(UUID userId, UUID codeId, Instant expiresAt) {}

    private String challengeKey(UUID id) { return "sso:mfa:challenge:" + id; }
    private String attemptsKey(UUID id) { return "sso:mfa:attempts:" + id; }
    private String resetKey(UUID id) { return "sso:password-reset:code:" + id; }
    private String resetAttemptsKey(UUID id) { return "sso:password-reset:attempts:" + id; }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String rootMessage(Throwable t) {
        Throwable c = t; while (c.getCause() != null) c = c.getCause();
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }
}
