package com.fuzis.ssoident.service;

import com.fuzis.ssoident.dto.RegisterRequest;
import com.fuzis.ssoident.dto.RegisterResponse;
import com.fuzis.ssoident.repository.RoleRepository;
import com.fuzis.ssoident.repository.UserRepository;
import com.fuzis.ssoident.repository.UserWriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository users;
    private final UserWriteRepository writes;
    private final AuditService audit;
    private final KafkaEventPublisher kafkaPublisher;
    private final KetoService keto;
    private final RoleRepository roles;
    private final TransactionalOperator tx;
    private final Argon2PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public RegistrationService(UserRepository users, UserWriteRepository writes, AuditService audit,
    KafkaEventPublisher kafkaPublisher, KetoService keto, RoleRepository roles, TransactionalOperator tx) {
        this.users = users;
        this.writes = writes;
        this.audit = audit;
        this.kafkaPublisher = kafkaPublisher;
        this.keto = keto;
        this.roles = roles;
        this.tx = tx;
    }

    public Mono<RegisterResponse> register(RegisterRequest request, String ip, String userAgent) {
        String email = canonicalEmail(request.email());
        String username = canonicalUsername(request.username());

        return users.findRegistrationByLogin(email)
        .switchIfEmpty(username == null
            ? Mono.empty()
            : users.findRegistrationByLogin(username))
        .flatMap(existing -> resumeOrReject(existing, request, ip, userAgent))
        .switchIfEmpty(create(request, email, username, ip, userAgent));
    }

    private Mono<RegisterResponse> resumeOrReject(UserRepository.RegistrationData existing,
    RegisterRequest request, String ip, String userAgent) {
        if (!"PENDING".equals(existing.status())) {
            return Mono.error(new IllegalArgumentException("Login is already in use"));
        }

        return users.findPasswordHash(existing.userId())
        .switchIfEmpty(Mono.error(new IllegalStateException("Pending user has no password credential")))
        .flatMap(hash -> matchesPassword(request.password(), hash)
        .flatMap(matches -> {
            if (!matches) {
                return Mono.error(new IllegalArgumentException("Login is already in use"));
            }

            log.info("Resuming PENDING registration for existing user {}", existing.userId());
            return resumeExisting(existing, ip, userAgent);
        }));
    }

    private Mono<Boolean> matchesPassword(String rawPassword, String passwordHash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, passwordHash))
        .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<RegisterResponse> create(RegisterRequest request, String email, String username,
    String ip, String userAgent) {
        UUID userId = UUID.randomUUID();

        Mono<RegisterResponse> workflow = Mono.fromCallable(() -> passwordEncoder.encode(request.password()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(hash -> {
            Mono<Void> dbTx = writes.createUser(userId, hash, email, username, request.firstName(), request.lastName())
            .then(audit.write(userId, "REGISTER", userId, ip, userAgent, true,
            Map.of("status", "PENDING")));

            return tx.transactional(dbTx)
            .doOnSuccess(ignored -> log.info("Registration DB transaction committed for user {}", userId))
            .then(integrate(userId, email, username, request.firstName(), request.lastName(), ip, userAgent));
        });

        // Keep the registration workflow alive if the HTTP client disconnects while Argon2,
        // Kafka or Keto are still processing. A retry from bootstrap can then safely resume it.
        return workflow.cache();
    }

    private Mono<RegisterResponse> resumeExisting(UserRepository.RegistrationData existing,
    String ip, String userAgent) {
        Mono<Void> recoveredAudit = tx.transactional(
            audit.write(existing.userId(), "REGISTER", existing.userId(), ip, userAgent, true,
            Map.of("status", "PENDING", "recovered", true))
        );

        Mono<RegisterResponse> workflow = recoveredAudit
        .then(integrate(
            existing.userId(),
            existing.email(),
            existing.username(),
            existing.firstName(),
            existing.lastName(),
            ip,
            userAgent
        ));

        return workflow.cache();
    }

    private Mono<RegisterResponse> integrate(UUID userId, String email, String username,
    String firstName, String lastName, String ip, String userAgent) {
        return publishRegistration(userId, email, username, firstName, lastName)
        .materialize()
        .flatMap(kafkaSignal -> {
            boolean kafkaOk = kafkaSignal.isOnComplete();
            Throwable kafkaFailure = kafkaSignal.getThrowable();

            return roles.findDefault()
            .switchIfEmpty(Mono.error(new IllegalStateException("Default role is not configured")))
            .flatMap(defaultRole -> ensureUserInGroup(userId, defaultRole.name()))
            .materialize()
            .flatMap(ketoSignal -> {
                boolean ketoOk = ketoSignal.isOnComplete();
                Throwable ketoFailure = ketoSignal.getThrowable();

                if (kafkaOk && ketoOk) {
                    return activate(userId, ip, userAgent)
                    .doOnSuccess(ignored -> log.info("Registration completed for user {}", userId))
                    .thenReturn(new RegisterResponse(userId, "ACTIVE"));
                }

                log.warn("Registration integration incomplete for user {}: kafka={}, keto={}",
                    userId, errorMessage(kafkaFailure), errorMessage(ketoFailure));

                return recordIntegrationFailure(userId, ip, userAgent, kafkaFailure, ketoFailure)
                .thenReturn(new RegisterResponse(userId, "PENDING"));
            });
        });
    }

    private Mono<Void> ensureUserInGroup(UUID userId, String groupName) {
        // A duplicate Keto tuple is a successful idempotent outcome for registration recovery.
        return keto.addUserToGroup(userId, groupName)
        .onErrorResume(ConflictException.class, ignored -> Mono.empty());
    }

    private Mono<Void> publishRegistration(UUID userId, String email, String username,
    String firstName, String lastName) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "SSO_IDENT_USER_REGISTERED");
        event.put("userId", userId.toString());
        event.put("username", username);
        event.put("email", email);
        event.put("firstName", firstName);
        event.put("lastName", lastName);
        return kafkaPublisher.publishUserRegistration(event);
    }

    private Mono<Void> activate(UUID userId, String ip, String userAgent) {
        return tx.transactional(
        writes.updateStatus(userId, "ACTIVE")
        .then(audit.write(userId, "REGISTER", userId, ip, userAgent, true,
        Map.of("status", "ACTIVE", "integrations", "kafka+keto")))
        );
    }

    private Mono<Void> recordIntegrationFailure(UUID userId, String ip, String userAgent,
    Throwable kafkaFailure, Throwable ketoFailure) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "PENDING");
        if (kafkaFailure != null) metadata.put("kafkaError", rootMessage(kafkaFailure));
        if (ketoFailure != null) metadata.put("ketoError", rootMessage(ketoFailure));
        return tx.transactional(audit.write(userId, "REGISTER", userId, ip, userAgent, false, metadata));
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null
            ? "unknown"
            : current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String errorMessage(Throwable t) {
        return t == null ? "OK" : rootMessage(t);
    }

    public static String canonicalEmail(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static String canonicalUsername(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
