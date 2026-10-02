package com.busgo.common.time;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.TimeZone;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.SqlTypeValue;

/** Matches Hibernate's configured UTC Calendar binding for JPA LocalDateTime columns.
 * Use only for JPA-written timestamps, not direct-JDBC hold/inventory timestamps.
 * This keeps existing data and JVM timezone unchanged while aligning both readers.
 */
public final class JpaJdbcTime {
    private JpaJdbcTime() {}

    public static LocalDateTime read(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column, utcCalendar());
        return value == null ? null : value.toLocalDateTime();
    }

    public static SqlParameterValue parameter(LocalDateTime value) {
        if (value == null) return new SqlParameterValue(Types.TIMESTAMP, null);
        SqlTypeValue binder = (statement, index, sqlType, typeName) ->
                statement.setTimestamp(index, Timestamp.valueOf(value), utcCalendar());
        return new SqlParameterValue(Types.TIMESTAMP, binder);
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
