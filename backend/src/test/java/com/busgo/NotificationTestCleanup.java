package com.busgo;

import org.springframework.jdbc.core.JdbcTemplate;

/** Removes only notification children of the explicitly owned test bookings. */
final class NotificationTestCleanup {
    private NotificationTestCleanup() {}
    static void bookings(JdbcTemplate db,String bookingIds) {
        db.update("DELETE d FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.booking_id IN ("+bookingIds+")");
        db.update("DELETE FROM notifications WHERE booking_id IN ("+bookingIds+")");
    }
    static void fixture(JdbcTemplate db,M8BookingTestSupport.Fixture f) {
        long op=f.operator().getId();
        String ids="SELECT b.id FROM bookings b JOIN trips t ON t.id=b.trip_id JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id="+op;
        bookings(db,ids);
        db.update("DELETE FROM refunds WHERE payment_id IN (SELECT id FROM payments WHERE booking_id IN ("+ids+"))");
        db.update("DELETE FROM partial_cancellation_items WHERE cancellation_id IN (SELECT id FROM partial_cancellations WHERE booking_id IN ("+ids+"))");
        db.update("DELETE FROM partial_cancellations WHERE booking_id IN ("+ids+")");
        db.update("DELETE FROM booking_modification_items WHERE modification_id IN (SELECT id FROM booking_modifications WHERE booking_id IN ("+ids+"))");
        db.update("DELETE a FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.booking_id IN ("+ids+")");
        db.update("DELETE FROM tickets WHERE booking_id IN ("+ids+")");
        db.update("DELETE FROM booking_status_history WHERE booking_id IN ("+ids+")");
        db.update("DELETE FROM payments WHERE booking_id IN ("+ids+")");
        db.update("DELETE FROM booking_modifications WHERE booking_id IN ("+ids+")");
        db.update("UPDATE trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id SET v.booking_item_id=NULL,v.status='AVAILABLE' WHERE i.booking_id IN ("+ids+")");
        db.update("DELETE FROM booking_items WHERE booking_id IN ("+ids+")");
        db.update("DELETE FROM bookings WHERE id IN (SELECT id FROM ("+ids+") x)");
        String tripIds="SELECT t.id FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id="+op;
        db.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id IN ("+tripIds+"))");
        db.update("DELETE FROM trip_seats WHERE trip_id IN ("+tripIds+")");
        db.update("DELETE FROM trip_segments WHERE trip_id IN ("+tripIds+")");
        db.update("DELETE FROM trip_stops WHERE trip_id IN ("+tripIds+")");
        db.update("DELETE FROM trips WHERE operator_route_id=?",f.operatorRoute().getId());
        db.update("DELETE FROM operator_route_fares WHERE operator_route_id=?",f.operatorRoute().getId());
        db.update("DELETE FROM buses WHERE id=?",f.bus().getId());
        db.update("DELETE FROM seat_templates WHERE bus_type_id=?",f.busType().getId());
        db.update("DELETE FROM bus_types WHERE id=?",f.busType().getId());
        db.update("DELETE FROM operator_routes WHERE id=?",f.operatorRoute().getId());
        db.update("DELETE FROM route_stops WHERE route_id=?",f.route().getId());
        db.update("DELETE FROM routes WHERE id=?",f.route().getId());
        db.update("DELETE FROM operator_staff WHERE operator_id=?",op);
        db.update("DELETE FROM transport_operators WHERE id=?",op);
        for(var l:f.locations()) db.update("DELETE FROM locations WHERE id=?",l.getId());
    }
}
