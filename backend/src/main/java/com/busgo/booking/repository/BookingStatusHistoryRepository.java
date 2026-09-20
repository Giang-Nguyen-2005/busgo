package com.busgo.booking.repository;

import com.busgo.booking.entity.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository
        extends JpaRepository<BookingStatusHistory, Long> {}
