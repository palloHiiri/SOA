package com.fuzis.booking.controller;

import com.fuzis.booking.dto.BookResponse;
import com.fuzis.booking.service.BookingService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/tickets/{ticketId}/sell")
    public BookResponse bookTicket(
            @PathVariable("ticketId") Long ticketId,
            @RequestParam("passengerId") UUID passengerId,
            @CookieValue("SESSION") String sessionToken
    ) {
        return bookingService.bookTicket(
                ticketId,
                passengerId,
                sessionToken
        );
    }

    @PostMapping("/tickets/{ticketId}/sell-with-discount")
    public BookResponse bookTicketWithDiscount(
            @PathVariable("ticketId") Long ticketId,
            @RequestParam("passengerId") UUID passengerId,
            @RequestParam("discount") Integer discount,
            @CookieValue("SESSION") String sessionToken
    ) {
        return bookingService.bookTicketWithDiscount(
                ticketId,
                passengerId,
                discount,
                sessionToken
        );
    }
}