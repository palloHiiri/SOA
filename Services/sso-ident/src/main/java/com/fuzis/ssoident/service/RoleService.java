package com.fuzis.ssoident.service;

import com.fuzis.ssoident.dto.GroupMembersResponse;
import com.fuzis.ssoident.dto.GroupResponse;
import com.fuzis.ssoident.dto.UserGroupsResponse;
import com.fuzis.ssoident.repository.RoleRepository;
import com.fuzis.ssoident.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleService {
    private final RoleRepository roles;
    private final UserRepository users;
    private final KetoService keto;

    public RoleService(RoleRepository roles, UserRepository users, KetoService keto) {
        this.roles = roles;
        this.users = users;
        this.keto = keto;
    }

    public Mono<GroupResponse> addUserToGroup(UUID userId, String groupName) {
        return findUser(userId)
                .then(roles.findByName(groupName)
                        .switchIfEmpty(Mono.error(new NotFoundException("Role not found: " + groupName))))
                .flatMap(role -> keto.isUserInGroup(userId, role.name())
                        .flatMap(alreadyMember -> {
                            if (alreadyMember) {
                                return Mono.error(new ConflictException(
                                        "User is already a member of role: " + role.name()));
                            }
                            return keto.addUserToGroup(userId, role.name())
                                    .thenReturn(toResponse(role));
                        }));
    }

    public Mono<Void> removeUserFromGroup(UUID userId, String groupName) {
        return findUser(userId)
                .then(roles.findByName(groupName)
                        .switchIfEmpty(Mono.error(new NotFoundException("Role not found: " + groupName))))
                .flatMap(role -> {
                    if (Boolean.TRUE.equals(role.isDefault())) {
                        return Mono.error(new ConflictException("The default role cannot be removed from a user"));
                    }
                    return keto.removeUserFromGroup(userId, role.name());
                });
    }

    public Mono<UserGroupsResponse> getUserGroups(UUID userId) {
        return findUser(userId)
                .then(roles.findAll().collectMap(RoleRepository.RoleRecord::name))
                .zipWith(keto.listUserGroups(userId).collectList())
                .map(tuple -> {
                    Map<String, RoleRepository.RoleRecord> knownRoles = tuple.getT1();
                    List<GroupResponse> groups = tuple.getT2().stream()
                            .map(knownRoles::get)
                            .filter(java.util.Objects::nonNull)
                            .map(this::toResponse)
                            .toList();
                    return new UserGroupsResponse(userId, groups);
                });
    }

    public Mono<GroupMembersResponse> getGroupMembers(String groupName) {
        return roles.findByName(groupName)
                .switchIfEmpty(Mono.error(new NotFoundException("Role not found: " + groupName)))
                .then(keto.listGroupMembers(groupName).collectList())
                .map(memberIds -> new GroupMembersResponse(groupName, memberIds));
    }

    public Flux<GroupResponse> getAllGroups() {
        return roles.findAll().map(this::toResponse);
    }

    private Mono<Void> findUser(UUID userId) {
        return users.findStatus(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + userId)))
                .then();
    }

    private GroupResponse toResponse(RoleRepository.RoleRecord role) {
        return new GroupResponse(role.id(), role.name(), role.description(), Boolean.TRUE.equals(role.isDefault()));
    }
}
