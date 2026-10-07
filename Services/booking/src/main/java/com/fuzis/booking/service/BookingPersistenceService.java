package com.fuzis.booking.service;

import com.fuzis.booking.model.Book;
import com.fuzis.booking.repository.BookRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW)
public class BookingPersistenceService {
    private final BookRepository repository;

    public BookingPersistenceService(BookRepository repository) {
        this.repository = repository;
    }

    public Book save(Long ticketId, UUID passengerId, BigDecimal price, Long sourceTicketId) {
        return repository.save(ticketId, passengerId, price, sourceTicketId);
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public List<Book> findByPassengerId(UUID passengerId) {
        return repository.findByPassengerId(passengerId);
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public boolean isTicketSold(Long ticketId) {
        return repository.findByTicketId(ticketId).isPresent();
    }
}
