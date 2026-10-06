package com.busgo.marketplace;

import static com.busgo.common.time.BusGoTime.api;
import com.busgo.marketplace.MarketplaceDtos.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;

@Repository
public class MarketplaceRepository {
    public static final String REVIEW_CONTEXT = """
        FROM marketplace_reviews v JOIN bookings b ON b.id=v.booking_id
        JOIN trips t ON t.id=b.trip_id JOIN operator_routes opr ON opr.id=t.operator_route_id
        JOIN transport_operators op ON op.id=opr.operator_id JOIN routes r ON r.id=opr.route_id
        JOIN users u ON u.id=b.customer_id
        """;
    public static final String RATING_JOIN = """
        LEFT JOIN (SELECT opr.operator_id, AVG(v.rating) average_rating, COUNT(*) review_count
          FROM marketplace_reviews v JOIN bookings b ON b.id=v.booking_id
          JOIN trips t ON t.id=b.trip_id JOIN operator_routes opr ON opr.id=t.operator_route_id
          WHERE v.deleted_at IS NULL GROUP BY opr.operator_id) ratings ON ratings.operator_id=op.id
        """;
    private final JdbcTemplate jdbc;
    public MarketplaceRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Page<Operator> operators(int page,int size) {
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE status='ACTIVE'",Long.class);
        var rows=jdbc.query("SELECT op.*, ratings.average_rating, COALESCE(ratings.review_count,0) review_count FROM transport_operators op "
            +RATING_JOIN+" WHERE op.status='ACTIVE' ORDER BY ratings.average_rating DESC, review_count DESC,op.id LIMIT ? OFFSET ?",this::operator,size,(long)page*size);
        return new PageImpl<>(rows,PageRequest.of(page,size),count);
    }
    public Optional<Operator> operator(long id,boolean publicOnly) {
        return jdbc.query("SELECT op.*, ratings.average_rating, COALESCE(ratings.review_count,0) review_count FROM transport_operators op "
            +RATING_JOIN+" WHERE op.id=?"+(publicOnly?" AND op.status='ACTIVE'":""),this::operator,id).stream().findFirst();
    }
    private Operator operator(ResultSet rs,int row) throws SQLException {
        return new Operator(rs.getLong("id"),rs.getString("name"),rs.getString("public_description"),rs.getString("logo_url"),
            rs.getObject("average_rating")==null?null:rs.getDouble("average_rating"),rs.getLong("review_count"));
    }
    public List<Route> routes(Long operatorId,int limit) {
        return jdbc.query("""
            SELECT DISTINCT r.id,r.name,r.origin_location_id,r.destination_location_id FROM routes r
            JOIN operator_routes opr ON opr.route_id=r.id JOIN transport_operators op ON op.id=opr.operator_id
            WHERE r.status='ACTIVE' AND opr.status='ACTIVE' AND op.status='ACTIVE' AND (? IS NULL OR op.id=?)
            ORDER BY r.id LIMIT ?
            """,(rs,n)->new Route(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4)),operatorId,operatorId,limit);
    }
    public List<Named> busTypes(Long operatorId) {
        return jdbc.query("""
            SELECT DISTINCT bt.id,bt.name FROM bus_types bt JOIN buses b ON b.bus_type_id=bt.id
            JOIN transport_operators op ON op.id=b.operator_id WHERE bt.status='ACTIVE' AND b.deleted_at IS NULL
            AND b.status='AVAILABLE' AND op.status='ACTIVE' AND (? IS NULL OR op.id=?) ORDER BY bt.id LIMIT 100
            """,(rs,n)->new Named(rs.getLong(1),rs.getString(2)),operatorId,operatorId);
    }
    public Page<Review> reviews(Long operatorId,int page,int size,boolean publicOnly) {
        String where=" WHERE v.deleted_at IS NULL AND (? IS NULL OR op.id=?)"+(publicOnly?" AND op.status='ACTIVE'":"");
        long total=jdbc.queryForObject("SELECT COUNT(*) "+REVIEW_CONTEXT+where,Long.class,operatorId,operatorId);
        var rows=jdbc.query("SELECT v.*,u.full_name,op.id operator_id,op.name operator_name,r.name route_name "+REVIEW_CONTEXT+where+
            " ORDER BY v.created_at DESC,v.id DESC LIMIT ? OFFSET ?",this::review,operatorId,operatorId,size,(long)page*size);
        return new PageImpl<>(rows,PageRequest.of(page,size),total);
    }
    public Optional<Review> reviewForBooking(long bookingId) {
        return jdbc.query("SELECT v.*,u.full_name,op.id operator_id,op.name operator_name,r.name route_name "+REVIEW_CONTEXT+
            " WHERE v.booking_id=? AND v.deleted_at IS NULL",this::review,bookingId).stream().findFirst();
    }
    private Review review(ResultSet rs,int n) throws SQLException {
        return new Review(rs.getLong("id"),rs.getInt("rating"),rs.getString("review_text"),rs.getString("full_name"),rs.getLong("operator_id"),
            rs.getString("operator_name"),rs.getString("route_name"),time(rs,"created_at"),time(rs,"updated_at"),rs.getString("response_text"),time(rs,"response_updated_at"));
    }
    private java.time.OffsetDateTime time(ResultSet rs,String key) throws SQLException {
        var value=rs.getObject(key,java.time.LocalDateTime.class); return api(value);
    }
}
