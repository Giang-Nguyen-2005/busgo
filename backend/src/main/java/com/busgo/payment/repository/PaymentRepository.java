package com.busgo.payment.repository;

import com.busgo.payment.entity.*;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findLockedByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
