package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.operations.*;
import com.busgo.operations.OperationsDtos.*;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.booking.AssistedBookingDtos.RecordPayment;
import com.busgo.user.entity.RoleCode;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@org.springframework.security.test.context.support.WithMockUser(roles="OPERATOR_ADMIN")
@Transactional(isolation=Isolation.READ_COMMITTED)
class M16BSecurityIT extends M16ATestSupport {
    @Autowired OperationsService ops;
    @Autowired MockMvc mvc;
    @Test void employeesCrewAndTicketsAreOperatorScopedAndStaffIsReadOnly() throws Exception {
        var f=fixture(); var foreign=fixture();
        var admin=actor(f,RoleCode.OPERATOR_ADMIN); var other=actor(foreign,RoleCode.OPERATOR_ADMIN);
        var employee=ops.saveEmployee(admin,null,new EmployeeInput("DRIVER","Driver","0901234567",EmployeeStatus.ACTIVE,Set.of(Capability.DRIVER),"LICENCE","DEMO",LocalDate.of(2099,12,31),null));
        mvc.perform(get("/api/v1/operator/employees/{id}",employee.id()).header("Authorization",bearer(other))).andExpect(status().isNotFound());
        String employeeBody="{\"employeeCode\":\"OTHER\",\"fullName\":\"Other\",\"phone\":\"0901234567\",\"status\":\"ACTIVE\",\"capabilities\":[\"ATTENDANT\"],\"version\":0}";
        mvc.perform(patch("/api/v1/operator/employees/{id}",employee.id()).header("Authorization",bearer(other)).contentType("application/json").content(employeeBody)).andExpect(status().isNotFound());
        String crewBody="{\"assignments\":[{\"employeeId\":"+employee.id()+",\"duty\":\"DRIVER\"}]}";
        mvc.perform(put("/api/v1/operator/trips/{id}/crew",f.trip().getId()).header("Authorization",bearer(other)).contentType("application/json").content(crewBody)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/operator/trips/{id}/crew",foreign.trip().getId()).header("Authorization",bearer(other)).contentType("application/json").content(crewBody)).andExpect(status().isNotFound());
        authenticate(admin);
        var booking=assisted.create(admin,request(f,0,2,PaymentMethod.QR_TRANSFER));
        String publicToken=token(assisted.issueLink(admin,booking.bookingId()));
        assisted.record(admin,booking.bookingId(),new RecordPayment(PaymentMethod.QR_TRANSFER,null));
        long ticket=jdbc.queryForObject("SELECT id FROM tickets WHERE booking_id=?",Long.class,booking.bookingId());
        jdbc.update("UPDATE trips SET status='BOARDING' WHERE id=?",f.trip().getId());
        String pickup="{\"stopId\":"+f.stops().get(0).getId()+"}";
        mvc.perform(post("/api/v1/operator/trips/{trip}/tickets/{ticket}/check-in",f.trip().getId(),ticket).header("Authorization",bearer(other)).contentType("application/json").content(pickup)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/operator/trips/{trip}/tickets/{ticket}/check-in",foreign.trip().getId(),ticket).header("Authorization",bearer(other)).contentType("application/json").content(pickup)).andExpect(status().isNotFound());
        for(var denied:List.of(actor(f,RoleCode.OPERATOR_STAFF),actor(f,RoleCode.SYSTEM_ADMIN),customer("m16b-customer").user())) {
            mvc.perform(post("/api/v1/operator/trips/{trip}/tickets/{ticket}/check-in",f.trip().getId(),ticket).header("Authorization",bearer(denied)).contentType("application/json").content(pickup)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/operator/employees").header("Authorization",bearer(denied)).contentType("application/json").content(employeeBody)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/operator/trips/{id}/crew",f.trip().getId()).header("Authorization",bearer(denied)).contentType("application/json").content(crewBody)).andExpect(status().isForbidden());
            if(denied.roles().contains(RoleCode.OPERATOR_STAFF)) mvc.perform(get("/api/v1/operator/trips/{id}/attendance",f.trip().getId()).header("Authorization",bearer(denied))).andExpect(status().isOk());
            if(denied.roles().contains(RoleCode.SYSTEM_ADMIN)) mvc.perform(get("/api/v1/operator/employees").header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/operator/trips/{trip}/tickets/{ticket}/check-in",f.trip().getId(),ticket).header("Authorization","Bearer "+publicToken).contentType("application/json").content(pickup)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/operator/trips/{trip}/tickets/{ticket}/check-in",f.trip().getId(),ticket).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()).contentType("application/json").content(pickup)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT status FROM ticket_boarding WHERE ticket_id=?",String.class,ticket)).isEqualTo("EXPECTED");
    }
    @Test void historicalTicketsStayUnrecordedAndEligibilityIsRevalidated() throws Exception {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        assertThat(ops.attendance(admin,f.trip().getId()).get(0).get("ticketId")).isNull();
        assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        long ticket=jdbc.queryForObject("SELECT id FROM tickets WHERE booking_id=?",Long.class,b.bookingId());
        jdbc.update("DELETE FROM ticket_boarding WHERE ticket_id=?",ticket);
        assertThat(ops.attendance(admin,f.trip().getId()).get(0).get("boardingStatus")).isNull();
        jdbc.update("UPDATE trips SET status='BOARDING' WHERE id=?",f.trip().getId());
        String path="/api/v1/operator/trips/"+f.trip().getId()+"/tickets/"+ticket+"/check-in";
        String body="{\"stopId\":"+f.stops().get(0).getId()+"}";
        jdbc.update("UPDATE payments SET status='REFUNDED' WHERE booking_id=?",b.bookingId());
        mvc.perform(post(path).header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("code").value("TICKET_NOT_ELIGIBLE"));
        jdbc.update("UPDATE payments SET status='PAID' WHERE booking_id=?",b.bookingId());
        jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",b.bookingId());
        mvc.perform(post(path).header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_boarding WHERE ticket_id=?",Integer.class,ticket)).isZero();
    }
    @Test void unpaidNoShowApiIsScopedValidatedAndReadOnlyForStaff() throws Exception {
        var f=fixture(); var foreign=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN);
        authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        long item=jdbc.queryForObject("SELECT id FROM booking_items WHERE booking_id=?",Long.class,b.bookingId());
        jdbc.update("UPDATE trips SET status='BOARDING' WHERE id=?",f.trip().getId());
        String path="/api/v1/operator/trips/"+f.trip().getId()+"/booking-items/"+item+"/no-show";
        String body="{\"stopId\":"+f.stops().get(0).getId()+",\"reason\":\"Customer absent\"}";
        mvc.perform(post(path).header("Authorization",bearer(actor(foreign,RoleCode.OPERATOR_ADMIN))).contentType("application/json").content(body)).andExpect(status().isNotFound());
        for(var denied:List.of(actor(f,RoleCode.OPERATOR_STAFF),actor(f,RoleCode.SYSTEM_ADMIN),customer("unpaid-no-show").user()))
            mvc.perform(post(path).header("Authorization",bearer(denied)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization",bearer(admin)).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization",bearer(admin)).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("data.status").value("NO_SHOW")).andExpect(jsonPath("data.ticket_id").doesNotExist()).andExpect(jsonPath("data.booking_item_id").value(item));
        assertThat(count("payments",b.bookingId())).isZero(); assertThat(count("tickets",b.bookingId())).isZero();
    }
}
