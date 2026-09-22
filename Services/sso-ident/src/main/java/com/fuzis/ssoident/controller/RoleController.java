package com.fuzis.ssoident.controller;

import com.fuzis.ssoident.dto.AddGroupRequest;
import com.fuzis.ssoident.dto.GroupMembersResponse;
import com.fuzis.ssoident.dto.GroupResponse;
import com.fuzis.ssoident.dto.UserGroupsResponse;
import com.fuzis.ssoident.service.RoleService;
import com.fuzis.ssoident.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sso-ident")
public class RoleController {
    private final RoleService roles;
    private final SessionService sessions;

    public RoleController(RoleService roles, SessionService sessions) {
        this.roles = roles;
        this.sessions = sessions;
    }

    @GetMapping("/me/groups")
    public Mono<UserGroupsResponse> getOwnGroups(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(sessions.cookieName());
        return sessions.resolve(cookie == null ? null : cookie.getValue())
        .switchIfEmpty(Mono.error(new IllegalStateException("No active session")))
        .flatMap(session -> roles.getUserGroups(session.userId()));
    }

    @PostMapping("/admin/users/{userId}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<GroupResponse> addGroup(@PathVariable UUID userId,
    @Valid
    @RequestBody
    AddGroupRequest request) {
        return roles.addUserToGroup(userId, request.group());
    }

    @DeleteMapping("/admin/users/{userId}/groups/{group}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeGroup(@PathVariable UUID userId, @PathVariable String group) {
        return roles.removeUserFromGroup(userId, group);
    }

    @GetMapping("/admin/users/{userId}/groups")
    public Mono<UserGroupsResponse> getUserGroups(@PathVariable UUID userId) {
        return roles.getUserGroups(userId);
    }

    @GetMapping("/admin/groups/{group}/members")
    public Mono<GroupMembersResponse> getGroupMembers(@PathVariable String group) {
        return roles.getGroupMembers(group);
    }

    @GetMapping("/admin/groups")
    public Flux<GroupResponse> getAllGroups() {
        return roles.getAllGroups();
    }
}
