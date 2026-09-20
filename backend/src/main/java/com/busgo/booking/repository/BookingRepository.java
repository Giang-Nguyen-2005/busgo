package com.busgo.booking.repository;

import com.busgo.booking.entity.Booking;
import com.busgo.booking.entity.BookingStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    boolean existsByBookingCode(String bookingCode);

    @Query(value = """
            select b from Booking b
            join fetch b.trip t
            join fetch t.operatorRoute opr
            join fetch opr.operator
            join fetch opr.route
            join fetch b.pickupTripStop pickup
            join fetch pickup.location
            join fetch b.dropoffTripStop dropoff
            join fetch dropoff.location
            where b.customer.id = :customerId
              and (:status is null or b.status = :status)
            """, countQuery = """
            select count(b) from Booking b
            where b.customer.id = :customerId
              and (:status is null or b.status = :status)
            """)
    Page<Booking> findOwned(@Param("customerId") Long customerId,
            @Param("status") BookingStatus status, Pageable pageable);

    @Query("""
            select b from Booking b
            join fetch b.trip t
            join fetch t.operatorRoute opr
            join fetch opr.operator
            join fetch opr.route
            join fetch b.pickupTripStop pickup
            join fetch pickup.location
            join fetch b.dropoffTripStop dropoff
            join fetch dropoff.location
            where b.id = :id and b.customer.id = :customerId
            """)
    Optional<Booking> findOwnedById(@Param("id") Long id,
            @Param("customerId") Long customerId);
}
