package com.fuzis.ssoident.dto;

import java.util.List;
import java.util.UUID;

public record GroupMembersResponse(
String group,
List<UUID> memberUserIds
) {
}
