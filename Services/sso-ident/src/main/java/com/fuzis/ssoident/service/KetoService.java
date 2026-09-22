package com.fuzis.ssoident.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KetoService {
    private final WebClient writeClient;
    private final WebClient readClient;
    private final String roleNamespace;

    public KetoService(WebClient.Builder builder,
                       @Value("${sso.keto.base-url}") String baseUrl,
                       @Value("${sso.keto.read-base-url}") String readBaseUrl,
                       @Value("${sso.keto.role-namespace}") String roleNamespace) {
        this.writeClient = builder.baseUrl(baseUrl).build();
        this.readClient = builder.baseUrl(readBaseUrl).build();
        this.roleNamespace = roleNamespace;
    }

    public Mono<Boolean> isUserInGroup(UUID userId, String groupName) {
        Map<String, Object> body = Map.of(
                "namespace", roleNamespace,
                "object", groupName,
                "relation", "members",
                "subject_id", userId.toString()
        );

        return readClient.post()
                .uri("/relation-tuples/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(com.fasterxml.jackson.databind.node.NullNode.getInstance())
                        .map(node -> response.statusCode().is2xxSuccessful()
                                && node.path("allowed").asBoolean(false)));
    }

    public Mono<Void> addUserToGroup(UUID userId, String groupName) {
        Map<String, Object> body = Map.of(
                "namespace", roleNamespace,
                "object", groupName,
                "relation", "members",
                "subject_id", userId.toString()
        );

        return writeClient.put()
                .uri("/admin/relation-tuples")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 409) {
                        return Mono.error(new ConflictException("User is already a member of role: " + groupName));
                    }
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.<Void>empty();
                    }
                    return response.createException().flatMap(error -> Mono.<Void>error(error));
                });
    }

    public Mono<Void> removeUserFromGroup(UUID userId, String groupName) {
        return writeClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin/relation-tuples")
                        .queryParam("namespace", roleNamespace)
                        .queryParam("object", groupName)
                        .queryParam("relation", "members")
                        .queryParam("subject_id", userId)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.error(new NotFoundException("User is not a member of role: " + groupName));
                    }
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.<Void>empty();
                    }
                    return response.createException().flatMap(error -> Mono.<Void>error(error));
                });
    }

    public Flux<String> listUserGroups(UUID userId) {
        return fetchUserGroupsPage(userId, null, new ArrayList<>());
    }

    public Flux<UUID> listGroupMembers(String groupName) {
        return fetchGroupMembersPage(groupName, null, new ArrayList<>());
    }

    public Mono<Boolean> checkMeAccess(UUID userId) {
        Map<String, Object> body = Map.of(
                "namespace", "Service",
                "object", "sso-ident",
                "relation", "access",
                "subject_id", userId.toString()
        );

        return readClient.post()
                .uri("/relation-tuples/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(com.fasterxml.jackson.databind.node.NullNode.getInstance())
                        .map(node -> response.statusCode().is2xxSuccessful() && node.path("allowed").asBoolean(false)));
    }

    private Flux<String> fetchUserGroupsPage(UUID userId, String pageToken, List<String> acc) {
        return readClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/relation-tuples")
                            .queryParam("namespace", roleNamespace)
                            .queryParam("relation", "members")
                            .queryParam("subject_id", userId.toString())
                            .queryParam("page_size", 1000);
                    if (pageToken != null && !pageToken.isBlank()) {
                        builder.queryParam("page_token", pageToken);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(node -> {
                    List<String> page = new ArrayList<>();
                    JsonNode tuples = node.path("relation_tuples");
                    if (tuples.isArray()) {
                        tuples.forEach(tuple -> {
                            String object = tuple.path("object").asText(null);
                            if (object != null && !object.isBlank()) {
                                page.add(object);
                            }
                        });
                    }
                    acc.addAll(page);
                    String next = node.path("next_page_token").asText("");
                    if (next.isBlank()) {
                        return Flux.fromIterable(acc);
                    }
                    return fetchUserGroupsPage(userId, next, acc);
                });
    }

    private Flux<UUID> fetchGroupMembersPage(String groupName, String pageToken, List<UUID> acc) {
        return readClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/relation-tuples")
                            .queryParam("namespace", roleNamespace)
                            .queryParam("object", groupName)
                            .queryParam("relation", "members")
                            .queryParam("page_size", 1000);
                    if (pageToken != null && !pageToken.isBlank()) {
                        builder.queryParam("page_token", pageToken);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(node -> {
                    JsonNode tuples = node.path("relation_tuples");
                    if (tuples.isArray()) {
                        tuples.forEach(tuple -> {
                            String subjectId = tuple.path("subject_id").asText(null);
                            if (subjectId != null && !subjectId.isBlank()) {
                                acc.add(UUID.fromString(subjectId));
                            }
                        });
                    }
                    String next = node.path("next_page_token").asText("");
                    if (next.isBlank()) {
                        return Flux.fromIterable(acc);
                    }
                    return fetchGroupMembersPage(groupName, next, acc);
                });
    }
}
