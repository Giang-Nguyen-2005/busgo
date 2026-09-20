package com.busgo.booking.repository;

import com.busgo.booking.entity.BookingItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    @Query("""
            select item from BookingItem item
            join fetch item.tripSeat
            where item.booking.id = :bookingId
            order by item.id
            """)
    List<BookingItem> findDetailedByBookingId(@Param("bookingId") Long bookingId);
}
