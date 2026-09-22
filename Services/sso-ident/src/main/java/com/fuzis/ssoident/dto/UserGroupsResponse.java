package com.fuzis.ssoident.dto;

import java.util.List;
import java.util.UUID;

public record UserGroupsResponse(
UUID userId,
List<GroupResponse> groups
) {
}
