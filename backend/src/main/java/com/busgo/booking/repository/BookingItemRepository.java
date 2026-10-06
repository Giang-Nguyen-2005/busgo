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
    @Query("select i from BookingItem i join fetch i.tripSeat where i.booking.id=:bookingId and i.cancelled=false order by i.id")
    List<BookingItem> findActiveByBookingId(@Param("bookingId") Long bookingId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BookingItem i join fetch i.tripSeat where i.booking.id=:bookingId order by i.id")
    List<BookingItem> lockByBookingId(@Param("bookingId") Long bookingId);
}
