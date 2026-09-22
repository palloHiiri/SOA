package com.fuzis.ssoident.controller;

import com.fuzis.ssoident.dto.*;
import com.fuzis.ssoident.service.AuthService;
import com.fuzis.ssoident.service.RegistrationService;
import com.fuzis.ssoident.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sso-ident/auth")
public class AuthController {
    private final RegistrationService registration;
    private final AuthService auth;
    private final SessionService sessions;

    public AuthController(RegistrationService registration, AuthService auth, SessionService sessions) {
        this.registration = registration;
        this.auth = auth;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request, ServerHttpRequest http) {
        return registration.register(request, remoteIp(http), userAgent(http));
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request,
    ServerHttpRequest http,
    ServerHttpResponse response) {
        return auth.login(request, remoteIp(http), userAgent(http))
        .map(result -> {
            if (result.sessionToken() != null) {
                response.addCookie(sessions.cookie(result.sessionToken(), result.maxAge()));
            }
            return result.response();
        }
        );
    }

    @PostMapping(value = "/password/reset")
    public Mono<Map<String, String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
    ServerHttpRequest http) {
        return auth.requestPasswordReset(request.email(), remoteIp(http), userAgent(http))
        .thenReturn(Map.of("status", "OK"));
    }

    @PostMapping("/password/reset/confirm")
    public Mono<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request,
    ServerHttpRequest http) {
        return auth.confirmPasswordReset(
        request.code(), request.newPassword(), remoteIp(http), userAgent(http));
    }

    @PostMapping("/mfa/email/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<VerificationResponse> requestMfa(@RequestBody Map<String, String> body,
    ServerHttpRequest http) {
        return auth.requestMfa(UUID.fromString(body.get("challengeId")), remoteIp(http), userAgent(http))
        .map(r -> new VerificationResponse(r.challengeId(), r.expiresAt()));
    }

    @PostMapping("/mfa/email/verify")
    public Mono<Void> verifyMfa(@Valid @RequestBody MfaCodeVerifyRequest request,
    ServerHttpRequest http,
    ServerHttpResponse response) {
        return auth.verifyMfa(request.challengeId(), request.code(), remoteIp(http), userAgent(http))
        .doOnNext(result -> {
            if (result.sessionToken() != null) {
                response.addCookie(sessions.cookie(result.sessionToken(), result.maxAge()));
            }
        }
        ).then();
    }

    private static String remoteIp(ServerHttpRequest request) {
        var address = request.getRemoteAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    private static String userAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }
}
