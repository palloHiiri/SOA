package com.fuzis.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record TrainSetLifecycleRequest(
@NotBlank String status
) {
}
