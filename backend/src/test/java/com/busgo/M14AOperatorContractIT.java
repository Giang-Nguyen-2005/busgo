package com.busgo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.OperatorStaff;
import com.busgo.operator.entity.TransportOperator;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.trip.entity.InventoryStatus;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M14AOperatorContractIT extends M8BookingTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired OperatorStaffRepository staff;

    @Test
    void businessDateUsesVietnamDayIncludingMidnightAndOvernightDeparture() throws Exception {
        Fixture fixture = fixture();
        String admin = memberToken(fixture.operator(), RoleCode.OPERATOR_ADMIN);

        list(admin, "2030-09-20").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].departureTime").value("2030-09-20T01:00:00Z"));

        fixture.trip().setDepartureTime(LocalDateTime.of(2030, 9, 20, 17, 0));
        fixture.trip().setEstimatedArrivalTime(LocalDateTime.of(2030, 9, 20, 20, 0));
        trips.saveAndFlush(fixture.trip());
        list(admin, "2030-09-20").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
        list(admin, "2030-09-21").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1));

        fixture.trip().setDepartureTime(LocalDateTime.of(2030, 9, 20, 16, 30));
        fixture.trip().setEstimatedArrivalTime(LocalDateTime.of(2030, 9, 20, 18, 30));
        trips.saveAndFlush(fixture.trip());
        list(admin, "2030-09-20").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1));
        list(admin, "2030-09-21").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
    }

    @Test
    void businessDateRejectsLegacyDateCombinationAndPreservesOwnershipAndStaffRead() throws Exception {
        Fixture owned = fixture();
        Fixture foreign = fixture();
        String staffToken = memberToken(owned.operator(), RoleCode.OPERATOR_STAFF);
        String foreignAdmin = memberToken(foreign.operator(), RoleCode.OPERATOR_ADMIN);

        mvc.perform(get("/api/v1/operator/trips")
                        .header("Authorization", bearer(staffToken))
                        .param("date", "2030-09-20")
                        .param("businessDate", "2030-09-20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        list(staffToken, "2030-09-20").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].id").value(owned.trip().getId()));
        mvc.perform(get("/api/v1/operator/trips")
                        .header("Authorization", bearer(staffToken))
                        .param("date", "2030-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1));
        list(foreignAdmin, "2030-09-20").andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].id").value(foreign.trip().getId()));
    }

    @Test
    void completeMultiSegmentMatrixReportsAllFourStatusesAndSeatReuse() throws Exception {
        Fixture fixture = fixture();
        String admin = memberToken(fixture.operator(), RoleCode.OPERATOR_ADMIN);
        setStatus(fixture, 0, 0, InventoryStatus.BOOKED);
        setStatus(fixture, 0, 2, InventoryStatus.BOOKED);
        setStatus(fixture, 1, 0, InventoryStatus.HELD);
        setStatus(fixture, 1, 1, InventoryStatus.BLOCKED);

        occupancy(admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("data.complete").value(true))
                .andExpect(jsonPath("data.expectedInventoryCellCount").value(6))
                .andExpect(jsonPath("data.actualInventoryCellCount").value(6))
                .andExpect(jsonPath("data.missingInventoryCellCount").value(0))
                .andExpect(jsonPath("data.segmentCount").value(3))
                .andExpect(jsonPath("data.seats[0].segments[0].status").value("BOOKED"))
                .andExpect(jsonPath("data.seats[0].segments[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("data.seats[0].segments[2].status").value("BOOKED"))
                .andExpect(jsonPath("data.seats[1].segments[0].status").value("HELD"))
                .andExpect(jsonPath("data.seats[1].segments[1].status").value("BLOCKED"))
                .andExpect(jsonPath("data.seats[0].segments[0].missing").value(false));
    }

    @Test
    void missingCellIsExplicitAndIncompleteSeatIsNotWholeTripAvailable() throws Exception {
        Fixture fixture = fixture();
        String admin = memberToken(fixture.operator(), RoleCode.OPERATOR_ADMIN);
        deleteCell(fixture, 0, 1);

        occupancy(admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("data.complete").value(false))
                .andExpect(jsonPath("data.expectedInventoryCellCount").value(6))
                .andExpect(jsonPath("data.actualInventoryCellCount").value(5))
                .andExpect(jsonPath("data.missingInventoryCellCount").value(1))
                .andExpect(jsonPath("data.wholeTripAvailableSeatCount").value(1))
                .andExpect(jsonPath("data.seats[0].segments[1].missing").value(true))
                .andExpect(jsonPath("data.seats[0].segments[1].status").doesNotExist());
    }

    @Test
    void entirelyMissingSeatRemainsVisibleAndNeverCountsAsAvailable() throws Exception {
        Fixture fixture = fixture();
        String admin = memberToken(fixture.operator(), RoleCode.OPERATOR_ADMIN);
        for (int segment = 0; segment < fixture.segments().size(); segment++) {
            deleteCell(fixture, 0, segment);
        }

        occupancy(admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("data.seatCount").value(2))
                .andExpect(jsonPath("data.seats.length()").value(2))
                .andExpect(jsonPath("data.seats[0].segments.length()").value(3))
                .andExpect(jsonPath("data.seats[0].segments[0].missing").value(true))
                .andExpect(jsonPath("data.seats[0].segments[1].missing").value(true))
                .andExpect(jsonPath("data.seats[0].segments[2].missing").value(true))
                .andExpect(jsonPath("data.actualInventoryCellCount").value(3))
                .andExpect(jsonPath("data.missingInventoryCellCount").value(3))
                .andExpect(jsonPath("data.wholeTripAvailableSeatCount").value(1));
    }

    @Test
    void occupancyAllowsStaffReadAndIsolatesForeignOperator() throws Exception {
        Fixture owned = fixture();
        Fixture foreign = fixture();
        String staffToken = memberToken(owned.operator(), RoleCode.OPERATOR_STAFF);
        String foreignAdmin = memberToken(foreign.operator(), RoleCode.OPERATOR_ADMIN);

        occupancy(staffToken, owned).andExpect(status().isOk())
                .andExpect(jsonPath("data.complete").value(true));
        occupancy(foreignAdmin, owned).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("TRIP_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions list(String token,
            String businessDate) throws Exception {
        return mvc.perform(get("/api/v1/operator/trips")
                .header("Authorization", bearer(token)).param("businessDate", businessDate));
    }

    private org.springframework.test.web.servlet.ResultActions occupancy(String token,
            Fixture fixture) throws Exception {
        return mvc.perform(get("/api/v1/operator/trips/{id}/occupancy", fixture.trip().getId())
                .header("Authorization", bearer(token)));
    }

    private void setStatus(Fixture fixture, int seat, int segment, InventoryStatus status) {
        jdbc.update("""
                UPDATE trip_seat_segment_inventory SET status=?, version=version+1
                WHERE trip_seat_id=? AND trip_segment_id=?
                """, status.name(), fixture.seats().get(seat).getId(),
                fixture.segments().get(segment).getId());
    }

    private void deleteCell(Fixture fixture, int seat, int segment) {
        jdbc.update("""
                DELETE FROM trip_seat_segment_inventory
                WHERE trip_seat_id=? AND trip_segment_id=?
                """, fixture.seats().get(seat).getId(), fixture.segments().get(segment).getId());
    }

    private String memberToken(TransportOperator operator, RoleCode roleCode) {
        User user = new User();
        user.setFullName("M14A operator member");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        user.setPasswordHash("not-used");
        user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        userRoles.saveAndFlush(new UserRole(user, roles.findByCode(roleCode).orElseThrow()));
        OperatorStaff membership = new OperatorStaff();
        membership.setOperator(operator);
        membership.setUser(user);
        membership.setStaffCode(UUID.randomUUID().toString());
        membership.setStatus(ActiveStatus.ACTIVE);
        staff.saveAndFlush(membership);
        return jwt.issue(new CurrentUser(user.getId(), List.of(roleCode)), "access");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
