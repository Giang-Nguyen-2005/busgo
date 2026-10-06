package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import com.busgo.booking.*;
import com.busgo.marketplace.*;
import com.busgo.trip.entity.TripStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("dev") @Transactional
class M24ABookingCompatibilityIT extends M20Support {
    @Autowired MarketplaceService marketplace;
    @Autowired PartialCancellationService partial;
    @Test void modifiedCurrentCompletedTripIsReviewable() {
        var f=expanded(); var owner=customer("M24-M20"); authenticate(owner.user());
        var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        var target=targetTrip(f);
        var modification=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target));
        modifications.confirm(owner.user(),b.bookingId(),false,modification.id());
        assertThat(marketplace.eligibility(owner.user(),b.bookingId()).eligible()).isFalse();
        target.setStatus(TripStatus.COMPLETED); trips.saveAndFlush(target);
        assertThat(marketplace.eligibility(owner.user(),b.bookingId()).eligible()).isTrue();
        assertThat(marketplace.create(owner.user(),b.bookingId(),new MarketplaceDtos.ReviewInput(4,"Modified trip review")).operatorId()).isEqualTo(f.operator().getId());
    }
    @Test void realM21CancellationReleasesSeatsAndRetainedItemCanReview() {
        var f=expanded(); var owner=customer("M24-M21"); authenticate(owner.user());
        var b=web(f,owner,0,1); paymentService.confirm(owner.user(),b.bookingId());
        var ids=itemIds(b.bookingId());
        partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(ids.get(0))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE booking_item_id=?",Long.class,ids.get(0))).isZero();
        f.trip().setStatus(TripStatus.COMPLETED); trips.saveAndFlush(f.trip());
        assertThat(marketplace.eligibility(owner.user(),b.bookingId()).eligible()).isTrue();
        marketplace.create(owner.user(),b.bookingId(),new MarketplaceDtos.ReviewInput(5,"Retained passenger travelled"));
        assertThat(marketplace.operator(f.operator().getId()).reviewCount()).isEqualTo(1);
    }
}
