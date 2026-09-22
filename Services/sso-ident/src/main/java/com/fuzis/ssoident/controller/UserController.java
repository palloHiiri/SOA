package com.fuzis.ssoident.controller;

import com.fuzis.ssoident.dto.ChangePasswordRequest;
import com.fuzis.ssoident.dto.MeResponse;
import com.fuzis.ssoident.dto.ProfileUpdateRequest;
import com.fuzis.ssoident.repository.SessionRepository;
import com.fuzis.ssoident.repository.UserRepository;
import com.fuzis.ssoident.service.AuditService;
import com.fuzis.ssoident.service.AuthService;
import com.fuzis.ssoident.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class UserController {
    private final SessionService sessions;
    private final SessionRepository sessionRepository;
    private final UserRepository users;
    private final AuditService audit;
    private final AuthService auth;

    public UserController(SessionService sessions, SessionRepository sessionRepository, UserRepository users,
    AuditService audit, AuthService auth) {
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
        this.users = users;
        this.audit = audit;
        this.auth = auth;
    }

    @RequestMapping("/api/v1/sso-ident/me")
    public Mono<MeResponse> me(ServerHttpRequest request) {
        return currentSession(request)
        .switchIfEmpty(Mono.error(new IllegalStateException("No active session")))
        .flatMap(s -> users.findStatus(s.userId())
        .filter("ACTIVE"::equals)
        .switchIfEmpty(Mono.error(new IllegalStateException("User is not active")))
        .flatMap(status -> sessionRepository.touch(s.sessionId())
        .then(users.findAttributes(s.userId()))
        .map(attrs -> new MeResponse(
        s.userId(),
        s.userId().toString(),
        status,
        attrs
        ))));
    }

    @PostMapping("/api/v1/sso-ident/me/password")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public Mono<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
    ServerHttpRequest http) {
        return currentSession(http)
        .switchIfEmpty(Mono.error(new IllegalStateException("No active session")))
        .flatMap(s -> auth.changePassword(
        s.userId(), request.currentPassword(), request.newPassword(),
        remoteIp(http), userAgent(http)));
    }

    @PostMapping("/api/v1/sso-ident/me/profile")
    public Mono<MeResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
    ServerHttpRequest http) {
        return currentSession(http)
        .switchIfEmpty(Mono.error(new IllegalStateException("No active session")))
        .flatMap(s -> auth.updateProfile(
        s.userId(), request, remoteIp(http), userAgent(http)));
    }

    @PostMapping("/api/v1/sso-ident/auth/logout")
    public Mono<Void> logout(ServerHttpRequest request, ServerHttpResponse response) {
        return currentSession(request)
        .flatMap(s -> sessions.revoke(s.token())
        .then(sessionRepository.revoke(s.sessionId(), s.userId()))
        .then(audit.write(s.userId(), "LOGOUT", s.userId(), remoteIp(request), userAgent(request), true,
        Map.of("sessionId", s.sessionId().toString())))
        .then())
        .then(Mono.fromRunnable(() -> response.addCookie(
        sessions.cookie("", java.time.Duration.ZERO)
        )));
    }

    private Mono<SessionService.SessionData> currentSession(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(sessions.cookieName());
        return sessions.resolve(cookie == null ? null : cookie.getValue());
    }

    private static String remoteIp(ServerHttpRequest request) {
        var a = request.getRemoteAddress();
        return a == null || a.getAddress() == null ? null : a.getAddress().getHostAddress();
    }
    private static String userAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }
}
