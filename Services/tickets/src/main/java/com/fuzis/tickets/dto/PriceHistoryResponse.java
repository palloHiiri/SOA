package com.fuzis.tickets.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class PriceHistoryResponse {
    private long id;
    private long ticketId;
    private OffsetDateTime changedDate;
    private BigDecimal basePrice;
    private int discount;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getTicketId() { return ticketId; }
    public void setTicketId(long ticketId) { this.ticketId = ticketId; }
    public OffsetDateTime getChangedDate() { return changedDate; }
    public void setChangedDate(OffsetDateTime changedDate) { this.changedDate = changedDate; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public int getDiscount() { return discount; }
    public void setDiscount(int discount) { this.discount = discount; }
}
