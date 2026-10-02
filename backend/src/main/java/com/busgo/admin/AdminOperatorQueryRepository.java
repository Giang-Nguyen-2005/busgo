package com.busgo.admin;

import static com.busgo.admin.AdminOperatorDtos.*;
import static com.busgo.common.time.BusGoTime.api;
import com.busgo.operator.entity.OperatorStatus;
import com.busgo.common.time.JpaJdbcTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOperatorQueryRepository {
    private static final String COUNTS = """
            (SELECT COUNT(*) FROM operator_staff s WHERE s.operator_id=o.id AND s.status='ACTIVE') AS active_staff_count,
            (SELECT COUNT(*) FROM operator_staff s JOIN users u ON u.id=s.user_id
             WHERE s.operator_id=o.id AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL
               AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                           WHERE ur.user_id=u.id AND r.code='OPERATOR_ADMIN')) AS active_admin_count
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public AdminOperatorQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public PageRows search(String q, OperatorStatus status, int page, int size) {
        String where="""
                FROM transport_operators o WHERE (:status IS NULL OR o.status=:status)
                  AND (:q IS NULL OR LOWER(o.code) LIKE :q OR LOWER(o.name) LIKE :q
                       OR LOWER(COALESCE(o.email,'')) LIKE :q OR LOWER(COALESCE(o.phone,'')) LIKE :q)
                """;
        var p=new MapSqlParameterSource("status",status==null?null:status.name())
                .addValue("q",q==null||q.isBlank()?null:"%"+q.strip().toLowerCase(Locale.ROOT)+"%")
                .addValue("limit",size).addValue("offset",Math.multiplyExact(page,size));
        long total=jdbc.queryForObject("SELECT COUNT(*) "+where,p,Long.class);
        var rows=jdbc.query("SELECT o.id,o.code,o.name,o.phone,o.email,o.status,o.created_at,o.updated_at,"+COUNTS
                +where+" ORDER BY o.name ASC,o.id ASC LIMIT :limit OFFSET :offset",p,(rs,n)->
                new AdminOperatorListItem(rs.getLong("id"),rs.getString("code"),rs.getString("name"),
                        rs.getString("phone"),rs.getString("email"),OperatorStatus.valueOf(rs.getString("status")),
                        rs.getLong("active_staff_count"),rs.getLong("active_admin_count"),
                        api(JpaJdbcTime.read(rs, "created_at")),api(JpaJdbcTime.read(rs, "updated_at"))));
        return new PageRows(rows,total);
    }

    public Optional<AdminOperatorDetail> detail(Long id) {
        var rows=jdbc.query("""
                SELECT o.id,o.code,o.name,o.phone,o.email,o.address,o.status,o.created_at,o.updated_at,
                  (SELECT COUNT(*) FROM operator_staff s WHERE s.operator_id=o.id) AS staff_total,
                  (SELECT COUNT(*) FROM operator_staff s WHERE s.operator_id=o.id AND s.status='ACTIVE') AS staff_active,
                  (SELECT COUNT(*) FROM operator_staff s WHERE s.operator_id=o.id AND EXISTS
                    (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=s.user_id AND r.code='OPERATOR_ADMIN')) AS staff_admins,
                  (SELECT COUNT(*) FROM operator_staff s WHERE s.operator_id=o.id AND EXISTS
                    (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=s.user_id AND r.code='OPERATOR_STAFF')) AS staff_members,
                  (SELECT COUNT(*) FROM buses b WHERE b.operator_id=o.id) AS buses,
                  (SELECT COUNT(*) FROM operator_routes opr WHERE opr.operator_id=o.id) AS routes,
                  (SELECT COUNT(*) FROM trips t JOIN operator_routes opr ON opr.id=t.operator_route_id WHERE opr.operator_id=o.id) AS trips,
                  (SELECT COUNT(*) FROM bookings bk JOIN trips t ON t.id=bk.trip_id JOIN operator_routes opr ON opr.id=t.operator_route_id WHERE opr.operator_id=o.id) AS bookings
                FROM transport_operators o WHERE o.id=:id
                """,Map.of("id",id),(rs,n)->new AdminOperatorDetail(rs.getLong("id"),rs.getString("code"),
                        rs.getString("name"),rs.getString("phone"),rs.getString("email"),rs.getString("address"),
                        OperatorStatus.valueOf(rs.getString("status")),new StaffCounts(rs.getLong("staff_total"),
                        rs.getLong("staff_active"),rs.getLong("staff_admins"),rs.getLong("staff_members")),
                        new OperationalCounts(rs.getLong("buses"),rs.getLong("routes"),rs.getLong("trips"),rs.getLong("bookings")),
                        api(JpaJdbcTime.read(rs, "created_at")),api(JpaJdbcTime.read(rs, "updated_at"))));
        return rows.stream().findFirst();
    }
    public record PageRows(List<AdminOperatorListItem> rows,long total) {}
}
