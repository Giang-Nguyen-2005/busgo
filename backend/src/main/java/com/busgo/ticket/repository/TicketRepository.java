package com.busgo.ticket.repository;

import com.busgo.ticket.entity.Ticket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query("""
            select ticket from Ticket ticket
            join fetch ticket.bookingItem item
            where ticket.booking.id = :bookingId
            order by item.id
            """)
    List<Ticket> findDetailedByBookingId(@Param("bookingId") Long bookingId);
}
