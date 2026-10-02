package com.busgo.operator;

import static com.busgo.operator.OperatorStaffDtos.*;
import static com.busgo.common.time.BusGoTime.api;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.user.entity.UserStatus;
import com.busgo.common.time.JpaJdbcTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class OperatorStaffQueryRepository {
    private static final String FROM = """
            FROM operator_staff s
            JOIN users u ON u.id = s.user_id
            WHERE s.operator_id = :operatorId
              AND (:status IS NULL OR s.status = :status)
              AND (:role IS NULL OR EXISTS (
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                    WHERE ur.user_id = u.id AND r.code = :role))
              AND (:q IS NULL OR LOWER(s.staff_code) LIKE :q OR LOWER(u.full_name) LIKE :q
                    OR LOWER(u.email) LIKE :q OR LOWER(u.phone) LIKE :q)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public OperatorStaffQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageRows search(Long operatorId, String q, ActiveStatus status, OperatorRole role,
            int page, int size) {
        var params = new MapSqlParameterSource("operatorId", operatorId)
                .addValue("q", normalizeQuery(q)).addValue("status", name(status))
                .addValue("role", name(role)).addValue("limit", size)
                .addValue("offset", Math.multiplyExact(page, size));
        long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM, params, Long.class);
        var rows = jdbc.query("""
                SELECT s.id, s.staff_code, s.status, s.created_at,
                       u.id AS user_id, u.full_name, u.email, u.phone, u.status AS user_status,
                       CASE WHEN EXISTS (SELECT 1 FROM user_roles ura JOIN roles ra ON ra.id=ura.role_id
                                         WHERE ura.user_id=u.id AND ra.code='OPERATOR_ADMIN')
                            THEN 'OPERATOR_ADMIN' ELSE 'OPERATOR_STAFF' END AS operator_role
                """ + FROM + " ORDER BY s.staff_code ASC, s.id ASC LIMIT :limit OFFSET :offset",
                params, (rs, n) -> new OperatorStaffResponse(rs.getLong("id"),
                        rs.getString("staff_code"), ActiveStatus.valueOf(rs.getString("status")),
                        OperatorRole.valueOf(rs.getString("operator_role")),
                        new StaffUser(rs.getLong("user_id"), rs.getString("full_name"),
                                rs.getString("email"), rs.getString("phone"),
                                UserStatus.valueOf(rs.getString("user_status"))),
                        api(JpaJdbcTime.read(rs, "created_at"))));
        return new PageRows(rows, total);
    }

    public List<Long> lockActiveAdminIds(Long operatorId) {
        return jdbc.queryForList("""
                SELECT s.id FROM operator_staff s
                JOIN users u ON u.id=s.user_id
                WHERE s.operator_id=:operatorId AND s.status='ACTIVE'
                  AND u.status='ACTIVE' AND u.deleted_at IS NULL
                  AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                              WHERE ur.user_id=u.id AND r.code='OPERATOR_ADMIN')
                ORDER BY s.id FOR UPDATE
                """, Map.of("operatorId", operatorId), Long.class);
    }

    public void lockOperator(Long operatorId) {
        jdbc.queryForObject("SELECT id FROM transport_operators WHERE id=:id FOR UPDATE",
                Map.of("id", operatorId), Long.class);
    }

    private static String normalizeQuery(String value) {
        return value == null || value.isBlank() ? null
                : "%" + value.strip().toLowerCase(Locale.ROOT) + "%";
    }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    public record PageRows(List<OperatorStaffResponse> rows, long total) {}
}
