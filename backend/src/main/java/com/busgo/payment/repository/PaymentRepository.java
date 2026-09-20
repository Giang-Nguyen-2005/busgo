package com.busgo.payment.repository;

import com.busgo.payment.entity.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
