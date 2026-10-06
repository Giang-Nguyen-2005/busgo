package com.busgo;

import org.springframework.jdbc.core.JdbcTemplate;

final class M16BFixtures {
    static void crew(JdbcTemplate db,long operator,long trip,long actor) {
        db.update("INSERT INTO operator_employees(operator_id,employee_code,full_name,phone,status,created_at,updated_at) VALUES(?,'TEST-DRIVER','Test Driver','0900000000','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",operator);
        long employee=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        db.update("INSERT INTO employee_capabilities VALUES(?,'DRIVER')",employee);
        db.update("INSERT INTO driver_profiles VALUES(?,'TEST','DEMO','2099-12-31')",employee);
        db.update("INSERT INTO trip_crew_assignments(trip_id,employee_id,duty,assigned_at,assigned_by) VALUES(?,?,'DRIVER',UTC_TIMESTAMP(6),?)",trip,employee,actor);
    }
    static void clear(JdbcTemplate db,long operator,long trip) {
        db.update("DELETE FROM trip_operational_updates WHERE trip_id=?",trip);
        db.update("DELETE tb FROM ticket_boarding tb JOIN booking_items bi ON bi.id=tb.booking_item_id JOIN bookings b ON b.id=bi.booking_id WHERE b.trip_id=?",trip);
        db.update("DELETE FROM operational_history WHERE trip_id=?",trip);
        db.update("DELETE FROM trip_stop_operations WHERE trip_id=?",trip);
        db.update("DELETE FROM trip_crew_assignments WHERE trip_id=?",trip);
        db.update("DELETE p FROM driver_profiles p JOIN operator_employees e ON e.id=p.employee_id WHERE e.operator_id=?",operator);
        db.update("DELETE c FROM employee_capabilities c JOIN operator_employees e ON e.id=c.employee_id WHERE e.operator_id=?",operator);
        db.update("DELETE FROM operator_employees WHERE operator_id=?",operator);
    }
}
