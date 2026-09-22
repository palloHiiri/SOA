package com.fuzis.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class TicketUpdateRequest {
    @Size(max = 500)
    private String name;

    @NotNull
    @Positive
    private Long venueId;

    @NotBlank
    @Size(max = 16)
    private String carriageNumber;

    @NotBlank
    @Size(max = 3)
    private String seatNumber;

    private Boolean refundable;
    private TicketType type;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Long getVenueId() {
        return venueId;
    }
    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }
    public String getCarriageNumber() {
        return carriageNumber;
    }
    public void setCarriageNumber(String carriageNumber) {
        this.carriageNumber = carriageNumber;
    }
    public String getSeatNumber() {
        return seatNumber;
    }
    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }
    public Boolean getRefundable() {
        return refundable;
    }
    public void setRefundable(Boolean refundable) {
        this.refundable = refundable;
    }
    public TicketType getType() {
        return type;
    }
    public void setType(TicketType type) {
        this.type = type;
    }
}
