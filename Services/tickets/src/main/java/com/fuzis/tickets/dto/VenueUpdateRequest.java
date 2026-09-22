package com.fuzis.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class VenueUpdateRequest {
    @NotBlank @Size(max = 255)
    private String name;
    @NotNull @Positive
    private Integer trainSetId;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getTrainSetId() { return trainSetId; }
    public void setTrainSetId(Integer trainSetId) { this.trainSetId = trainSetId; }
}
