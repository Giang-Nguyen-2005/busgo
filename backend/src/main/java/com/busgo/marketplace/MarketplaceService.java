package com.busgo.marketplace;

import com.busgo.marketplace.MarketplaceDtos.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.user.entity.RoleCode;
import com.busgo.trip.search.TripSearchRepository;
import com.busgo.trip.search.TripSearchDtos.SearchSort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.*;

@Service
@Transactional(readOnly=true)
public class MarketplaceService {
    private final MarketplaceRepository repository;
    private final JdbcTemplate jdbc;
    private final OperatorContextService context;
    private final TripSearchRepository search;
    private final Clock clock;
    public MarketplaceService(MarketplaceRepository repository,JdbcTemplate jdbc,OperatorContextService context,TripSearchRepository search,Clock clock) {
        this.repository=repository; this.jdbc=jdbc; this.context=context; this.search=search; this.clock=clock;
    }
    public PagedResponse<Operator> operators(int page,int size) { return PagedResponse.from(repository.operators(page,size)); }
    public Operator operator(long id) { return repository.operator(id,true).orElseThrow(MarketplaceService::missing); }
    public Profile profile(long id) {
        var op=operator(id);
        return new Profile(op,repository.routes(id,30),repository.busTypes(id),upcoming(id));
    }
    public java.util.List<com.busgo.trip.search.TripSearchDtos.SearchResult> upcoming(Long operatorId) {
        var now=com.busgo.common.time.BusGoTime.utc(clock.instant());
        return search.search(new TripSearchRepository.Criteria(null,null,now,now.plusDays(30),now,
            operatorId,null,null,null,null,null,SearchSort.DEPARTURE_ASC,null,1),PageRequest.of(0,6)).getContent();
    }
    public java.util.List<Route> routes() { return repository.routes(null,8); }
    public java.util.List<Named> busTypes() { return repository.busTypes(null); }
    public PagedResponse<Review> reviews(Long id,int page,int size) { if(id!=null) operator(id); return PagedResponse.from(repository.reviews(id,page,size,true)); }

    @PreAuthorize("hasRole('CUSTOMER')")
    public Eligibility eligibility(CurrentUser user,long bookingId) {
        var state=owned(user,bookingId,false); var review=repository.reviewForBooking(bookingId).orElse(null);
        String reason=reason(state); return new Eligibility(reason==null && review==null,review!=null?"ALREADY_REVIEWED":reason,review);
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public Review create(CurrentUser user,long bookingId,ReviewInput input) {
        var state=owned(user,bookingId,true); String reason=reason(state);
        if(reason!=null) throw conflict(reason);
        if(repository.reviewForBooking(bookingId).isPresent()) throw conflict("ALREADY_REVIEWED");
        jdbc.update("INSERT INTO marketplace_reviews(booking_id,rating,review_text,created_at,updated_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",bookingId,input.rating(),input.text().trim());
        return repository.reviewForBooking(bookingId).orElseThrow();
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public Review edit(CurrentUser user,long bookingId,ReviewInput input) {
        var state=owned(user,bookingId,true); if(reason(state)!=null) throw conflict(reason(state));
        var review=repository.reviewForBooking(bookingId).orElseThrow(MarketplaceService::missing);
        jdbc.update("UPDATE marketplace_reviews SET rating=?,review_text=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND deleted_at IS NULL",input.rating(),input.text().trim(),review.id());
        return repository.reviewForBooking(bookingId).orElseThrow();
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public boolean delete(CurrentUser user,long bookingId) {
        owned(user,bookingId,true); var review=repository.reviewForBooking(bookingId).orElseThrow(MarketplaceService::missing);
        jdbc.update("UPDATE marketplace_reviews SET deleted_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",review.id()); return true;
    }
    private State owned(CurrentUser user,long id,boolean lock) {
        if(user==null || !user.roles().contains(RoleCode.CUSTOMER)) throw new BusinessException("ACCESS_DENIED","Customer access required.",HttpStatus.FORBIDDEN,null);
        // Booking lock serializes creates/edits/deletes with M20/M21 booking mutations.
        var rows=jdbc.query("SELECT status,trip_id FROM bookings WHERE id=? AND customer_id=?"+(lock?" FOR UPDATE":""),
            (rs,n)->new State(rs.getString(1),rs.getLong(2),false,null),id,user.id());
        if(rows.isEmpty()) throw missing(); var b=rows.get(0);
        String tripStatus=jdbc.queryForObject("SELECT status FROM trips WHERE id=?",String.class,b.tripId());
        boolean retained=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_items WHERE booking_id=? AND cancelled=false)",Boolean.class,id));
        return new State(b.status(),b.tripId(),retained,tripStatus);
    }
    private String reason(State b) {
        if(!"COMPLETED".equals(b.tripStatus())) return "TRIP_NOT_COMPLETED";
        if(!java.util.Set.of("CONFIRMED","COMPLETED").contains(b.status()) || !b.retained()) return "BOOKING_NOT_RETAINED";
        return null;
    }
    private record State(String status,long tripId,boolean retained,String tripStatus) {}

    @PreAuthorize("hasAnyRole('OPERATOR_ADMIN','OPERATOR_STAFF')")
    public Operator ownProfile(CurrentUser user) { return repository.operator(context.requireOperatorMember(user).getId(),false).orElseThrow(MarketplaceService::missing); }
    @PreAuthorize("hasRole('OPERATOR_ADMIN')")
    @Transactional
    public Operator updateProfile(CurrentUser user,ProfileInput input) {
        long id=context.requireAdminOperator(user).getId();
        jdbc.update("UPDATE transport_operators SET public_description=?,logo_url=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",input.description(),input.logoUrl(),id);
        return repository.operator(id,false).orElseThrow();
    }
    @PreAuthorize("hasAnyRole('OPERATOR_ADMIN','OPERATOR_STAFF')")
    public PagedResponse<Review> ownReviews(CurrentUser user,int page,int size) {
        long id=context.requireOperatorMember(user).getId(); return PagedResponse.from(repository.reviews(id,page,size,false));
    }
    @PreAuthorize("hasRole('OPERATOR_ADMIN')")
    @Transactional
    public boolean respond(CurrentUser user,long reviewId,ResponseInput input) {
        long id=context.requireAdminOperator(user).getId();
        int changed=jdbc.update("""
            UPDATE marketplace_reviews v JOIN bookings b ON b.id=v.booking_id
            JOIN trips t ON t.id=b.trip_id JOIN operator_routes opr ON opr.id=t.operator_route_id
            SET v.response_text=?,v.response_updated_at=UTC_TIMESTAMP(6)
            WHERE v.id=? AND opr.operator_id=? AND v.deleted_at IS NULL
            """,input.text().trim(),reviewId,id);
        if(changed==0) throw missing(); return true;
    }
    private static BusinessException missing() { return new BusinessException("MARKETPLACE_NOT_FOUND","Marketplace resource not found.",HttpStatus.NOT_FOUND,null); }
    private static BusinessException conflict(String reason) { return new BusinessException(reason,"This booking cannot create or update a review.",HttpStatus.CONFLICT,null); }
}
