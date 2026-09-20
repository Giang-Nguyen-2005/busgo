package com.busgo.booking.repository;

import com.busgo.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {}
