package com.busgo.payment.repository;

import com.busgo.payment.entity.*;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.booking.id=:bookingId and p.status=:status and p.purpose='BOOKING'")
    Optional<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.booking.id=:bookingId and p.status=:status and p.purpose='BOOKING'")
    Optional<Payment> findLockedByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
