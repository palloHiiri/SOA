package com.fuzis.booking.controller;

import com.fuzis.booking.dto.BookResponse;
import com.fuzis.booking.service.BookingService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/sell/{ticketId}/{passengerId}/{price}")
    public BookResponse bookTicket(
            @PathVariable("ticketId") Long ticketId,
            @PathVariable("passengerId") UUID passengerId,
            @PathVariable("price") BigDecimal price,

            @CookieValue("SESSION") String sessionToken
    ) {
        return bookingService.bookTicket(ticketId, passengerId, price, sessionToken);
    }
}
