package com.fuzis.tickets.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "price_histories")
public class PriceHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "ticket_id", nullable = false)
    public Long ticketId;

    @Column(name = "changed_date", insertable = false, updatable = false)
    public OffsetDateTime changedDate;

    @Column(name = "base_price", precision = 12, scale = 2)
    public BigDecimal basePrice;

    @Column(nullable = false)
    public Integer discount;
}
