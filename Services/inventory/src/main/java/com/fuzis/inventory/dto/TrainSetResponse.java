package com.fuzis.inventory.dto;

import java.time.OffsetDateTime;

public record TrainSetResponse(
        Integer id,
        String code,
        Integer buildNumber,
        String name,
        String technicalName,
        String description,
        Integer trainSetLifecycleStatusId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
