package com.fuzis.tickets.entity;

import com.fuzis.tickets.dto.TicketType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "tickets")
public class TicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, columnDefinition = "text")
    public String name;

    @Column(name = "creation_date", insertable = false, updatable = false)
    public LocalDate creationDate;

    @Column(name = "venue_id", nullable = false)
    public Long venueId;

    @Column(name = "carriage_number", nullable = false, length = 16)
    public String carriageNumber;

    @Column(name = "seat_number", nullable = false, length = 3)
    public String seatNumber;

    @Column public Boolean refundable;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public TicketType type;
}
