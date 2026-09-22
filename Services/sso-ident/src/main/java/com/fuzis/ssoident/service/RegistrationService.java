package com.fuzis.ssoident.service;

import com.fuzis.ssoident.dto.RegisterRequest;
import com.fuzis.ssoident.dto.RegisterResponse;
import com.fuzis.ssoident.repository.RoleRepository;
import com.fuzis.ssoident.repository.UserRepository;
import com.fuzis.ssoident.repository.UserWriteRepository;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationService {
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

        return users.existsByLogin(email)
        .flatMap(emailExists -> emailExists
        ? Mono.error(new IllegalArgumentException("Login is already in use"))
        : username == null ? Mono.just(false) : users.existsByLogin(username))
        .flatMap(usernameExists -> usernameExists
        ? Mono.error(new IllegalArgumentException("Login is already in use"))
        : create(request, email, username, ip, userAgent));
    }

    private Mono<RegisterResponse> create(RegisterRequest request, String email, String username,
    String ip, String userAgent) {
        UUID userId = UUID.randomUUID();
        String hash = passwordEncoder.encode(request.password());

        Mono<Void> dbTx = writes.createUser(userId, hash, email, username, request.firstName(), request.lastName())
        .then(audit.write(userId, "REGISTER", userId, ip, userAgent, true,
        Map.of("status", "PENDING")));

        return tx.transactional(dbTx)
        .then(publishRegistration(userId, email, username, request.firstName(), request.lastName())
        .materialize())
        .flatMap(signal -> {
            boolean kafkaOk = signal.isOnComplete();
            Throwable kafkaFailure = signal.getThrowable();
            return roles.findDefault()
            .switchIfEmpty(Mono.error(new IllegalStateException("Default role is not configured")))
            .flatMap(defaultRole -> keto.addUserToGroup(userId, defaultRole.name()))
            .materialize()
            .flatMap(ketoSignal -> {
                boolean ketoOk = ketoSignal.isOnComplete();
                Throwable ketoFailure = ketoSignal.getThrowable();
                if (kafkaOk && ketoOk) {
                    return activate(userId, ip, userAgent)
                    .thenReturn(new RegisterResponse(userId, "ACTIVE"));
                }
                return recordIntegrationFailure(userId, ip, userAgent, kafkaFailure, ketoFailure)
                .thenReturn(new RegisterResponse(userId, "PENDING"));
            }
            );
        }
        );
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
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public static String canonicalEmail(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
    public static String canonicalUsername(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
