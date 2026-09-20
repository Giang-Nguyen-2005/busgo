package com.busgo.payment.entity;

import com.busgo.booking.entity.Booking;
import com.busgo.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "method", length = 30, nullable = false)
    private PaymentMethod method;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_reference", length = 100, nullable = false, unique = true)
    private String transactionReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
