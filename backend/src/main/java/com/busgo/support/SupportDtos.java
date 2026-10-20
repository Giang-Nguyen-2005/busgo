package com.busgo.support;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class SupportDtos {
    private SupportDtos() {}
    public enum Category {
        TRIP_INFORMATION, PICKUP_DROPOFF, TICKET_ACCESS, BAGGAGE, BOOKING_HELP,
        BOOKING_CHANGE_REQUEST, CANCELLATION_HELP, PAYMENT_QUESTION, REFUND_COMPLAINT, SERVICE_COMPLAINT, OTHER;
        public boolean sensitive() { return switch(this) {
            case BOOKING_CHANGE_REQUEST,CANCELLATION_HELP,PAYMENT_QUESTION,REFUND_COMPLAINT,SERVICE_COMPLAINT,OTHER -> true;
            default -> false;
        }; }
    }
    public enum State { OPEN, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED }
    public enum Escalation { NONE, OPERATOR_ADMIN, PLATFORM }
    public enum Priority { LOW, NORMAL, HIGH }
    public enum Outcome { OPERATOR_RESOLUTION_UPHELD, CUSTOMER_COMPLAINT_UPHELD, ADDITIONAL_REVIEW_REQUIRED, INSUFFICIENT_EVIDENCE }
    public record Create(@Positive long bookingId,@NotNull Category category,@NotBlank @Size(max=160) String subject,
        @NotBlank @Size(max=4000) String description,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String requestKey) {}
    public record Command(@Min(0) long version,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String requestKey,
        @NotBlank @Size(max=4000) String content,boolean internal,Long assignedUserId,State status,Priority priority,Outcome outcome) {}
    public record Ticket(long id,long bookingId,String bookingCode,long customerId,long operatorId,String operatorName,
        Category category,String subject,State status,Escalation escalation,Priority priority,Long assignedUserId,
        long version,int reopenCount,Instant resolvedAt,boolean resolvedByAdmin,Instant createdAt,Instant updatedAt,String disputeStatus,List<String> actions) {}
    public record Activity(long id,long actorId,String actorRole,String eventType,String visibility,String content,
        String fromStatus,String toStatus,Long assignedUserId,Instant createdAt) {}
    public record Dispute(long id,Ticket ticket,String reason,String status,Outcome outcome,String decision,Long decidedBy,Instant decidedAt) {}
    public record Assignee(long id,String name) {}
    public record Counter(String key,long count) {}
    public record Notice(long id,String title,String target,Instant createdAt,Instant readAt) {}
    public record AuditEvent(long id,String eventCode,long actorId,String actorRole,Long operatorId,String resourceType,
        long resourceId,String action,String outcome,Instant createdAt) {}
}
