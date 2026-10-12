package com.busgo.onboarding;

import jakarta.validation.constraints.*;
import java.util.Locale;

public final class OnboardingDtos {
    private OnboardingDtos() {}
    public enum ApplicationStatus { SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED }
    private static String trim(String s) { return s==null?null:s.strip(); }
    private static String email(String s) { return s==null?null:s.strip().toLowerCase(Locale.ROOT); }
    public record ApplicationRequest(
        @NotBlank @Size(max=150) String name,
        @Size(max=150) String legalName,
        @NotBlank @Pattern(regexp="[A-Z0-9-]{3,50}") String legalCode,
        @NotBlank @Email @Size(max=150) String email,
        @NotBlank @Pattern(regexp="[+0-9 ()-]{6,20}") String phone,
        @NotBlank @Size(max=255) String address,
        @NotBlank @Size(max=100) String contactName,
        @NotBlank @Email @Size(max=150) String adminEmail,
        @Size(max=500) @Pattern(regexp="^$|https?://[^\\s]+") String website,
        @Size(max=2000) String publicDescription) {
        public ApplicationRequest {
            name=trim(name);legalName=trim(legalName);legalCode=legalCode==null?null:legalCode.strip().toUpperCase(Locale.ROOT).replaceAll("\\s", "");
            email=OnboardingDtos.email(email);adminEmail=OnboardingDtos.email(adminEmail);phone=trim(phone);address=trim(address);
            contactName=trim(contactName);website=trim(website);publicDescription=trim(publicDescription);
        }
    }
    public record Decision(@Size(max=1000) String note) {}
    public record Reason(@NotBlank @Size(max=1000) String reason) {}
    public record Acceptance(@NotBlank @Size(max=100) String fullName, String password, String confirmPassword) {
        @Override public String toString() { return "Acceptance[REDACTED]"; }
    }
    public record InviteResult(long applicationId,long operatorId,String invitationPath,java.time.OffsetDateTime expiresAt) {
        @Override public String toString() { return "InviteResult[REDACTED]"; }
    }
}
