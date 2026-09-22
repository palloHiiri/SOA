package com.fuzis.tickets.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TicketResponse {
    private long id;
    private String name;
    private LocalDate creationDate;
    private BigDecimal basePrice;
    private int discount;
    private Boolean refundable;
    private TicketType type;
    private VenueReference venue;
    private String carriageNumber;
    private String seatNumber;

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public LocalDate getCreationDate() {
        return creationDate;
    }
    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }
    public BigDecimal getBasePrice() {
        return basePrice;
    }
    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }
    public int getDiscount() {
        return discount;
    }
    public void setDiscount(int discount) {
        this.discount = discount;
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
    public VenueReference getVenue() {
        return venue;
    }
    public void setVenue(VenueReference venue) {
        this.venue = venue;
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
}
