# BusGo M26 — comprehensive final verification handoff

Prepared 12 October 2026 (Asia/Saigon). Continue the CURRENT working tree on `feature/v2-operator-onboarding`. No commit, push, merge or tag. Preserve all existing work and V1–V22 migrations. Do not restart/redesign M26 or rerun passed suites.

## 1. Overall status

**COMPLETE.** The final valid-invitation mobile acceptance passed on 12 October 2026 at 390×844. Implementation, historical automated tests, remaining API/database acceptance, packaged startup and required responsive views now have passing evidence. The earlier malformed-URL navigation was not bypassed: following explicit user authorization to close acceptance, the original securely retained absolute invitation link was opened directly in its supported fragment format. No reissue or invitation consumption was needed.

`CURRENT_STATUS.md`, `PROJECT_HANDOFF.md` and `AI_WORKFLOW.md` were absent from the repository, including a hidden-file search. Existing authoritative M26 notes and development plan were read and updated instead; no replacement conventions were invented. No AGENTS.md was found by the repository search; the pre-existing M26 audit also records no repository/ancestor AGENTS.md.

## 2. Implemented features

Public legal/contact operator applications; System Admin list/search/filter/detail, start review, reasoned rejection and approval; one-time first-admin invitations with expiry/reissue; dedicated admin acceptance and activation; reasoned suspension/reactivation; platform governance dashboard, legal operator directory/detail, invitations and chronological governance history. Legacy admin operator creation/contact editing remains supported with guarded status transitions.

Final verification changed documentation and ignored local acceptance artifacts only. It did not alter production source, tests, schema files or previous migrations.

## 3. Business rules

- Application lifecycle: SUBMITTED → UNDER_REVIEW → APPROVED; SUBMITTED or UNDER_REVIEW → REJECTED. Direct approval is denied. Rejected applications are terminal and cannot be reopened; resubmission creates a new record.
- Legal code is stripped, uppercased and whitespace-normalized. SUBMITTED/UNDER_REVIEW/APPROVED applications reserve their identity. Rejection releases the reservation. An approved operator identity remains unique.
- Approval creates an INACTIVE operator and a 72-hour invitation; it creates no admin user, password, route, bus or trip. Acceptance activates that same operator with a new dedicated OPERATOR_ADMIN user and active membership. Existing emails cannot be repurposed.
- ACTIVE → SUSPENDED and SUSPENDED → ACTIVE require a reason. Reactivation requires completed onboarding and at least one active, nondeleted admin with exactly the dedicated operator-admin role and one active operator membership.
- Suspension hides public marketplace profile/supply and blocks new holds, bookings, commerce/payment confirmation, modification and guarded route/trip supply creation. Existing bookings remain readable and permitted whole/partial cancellations and simulated refunds retain their usual eligibility rules.
- Existing trip service remains available to the operator admin while suspended. Crew readiness, legal forward lifecycle, paid-ticket attendance, pickup closure, terminal-state, cancellation and inventory restrictions remain authoritative. Staff remains read-only.
- SYSTEM_ADMIN governs platform/onboarding only; it cannot impersonate an operator or bypass booking, payment, ticket, passenger, refund or trip permissions. Legacy status patch cannot bypass onboarding or suspension workflows.

## 4. API changes

All paths below have `/api/v1` prefix. New onboarding responses set `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/public/operator-applications` | Submit normalized legal/contact information |
| GET | `/public/operator-invitations/{token}/context` | Validate invitation and show intended identity |
| POST | `/public/operator-invitations/{token}/accept` | Create dedicated first admin and activate operator |
| GET | `/admin/dashboard` | Governance/supply counts, no financial values |
| GET | `/admin/operator-applications` | Bounded paginated status/search list |
| GET | `/admin/operator-applications/{id}` | Details, safe invite metadata, latest history |
| POST | `/admin/operator-applications/{id}/review` | Start review |
| POST | `/admin/operator-applications/{id}/approve` | Atomic operator/invitation creation |
| POST | `/admin/operator-applications/{id}/reject` | Terminal rejection with reason |
| POST | `/admin/operator-applications/{id}/invitation/reissue` | Revoke previous pending invite and issue new one |
| GET | `/admin/operators/{id}/governance` | Onboarding readiness, supply counts and history |
| POST | `/admin/operators/{id}/suspend` | Reasoned suspension |
| POST | `/admin/operators/{id}/reactivate` | Guarded reactivation |

Existing admin directory/detail includes legal/onboarding fields and active-route/upcoming-trip counts; status filter accepts ACTIVE/INACTIVE/SUSPENDED. Existing cancellation, membership, route/trip creation and legacy admin status behavior has the M26 guards described above. Existing operational endpoints are reused, not replaced.

## 5. Database changes

Exact migration: `backend/src/main/resources/db/migration/V23__operator_onboarding.sql`.

- `transport_operators`: SUSPENDED check value; nullable `legal_name`, `legal_code`, `legal_contact`, `website`, `onboarded_at`; unique legal code; status index. Existing installed operators with an administrator are marked provisioned at their creation time during migration.
- `operator_applications`: normalized legal/contact/public metadata, status, optional unique operator FK, submission time; generated unique `reserved_legal_code` for nonrejected states; status/date index; approved/operator linkage check.
- `operator_admin_invitations`: application/operator FKs, intended email, unique case-sensitive ASCII SHA-256 hash, creation/expiry/accepted/revoked times; generated unique current application key ensures at most one unconsumed/unrevoked invite; expiry and accepted/revoked checks.
- `operator_onboarding_history`: application/operator/actor FKs, event, actor category, old/new state, note and timestamp; application/operator indexes. Append-only through service workflows; no update/delete endpoint. This is application-level immutability, not a database trigger preventing a privileged direct SQL edit.
- `notifications.booking_id` and `booking_code` become nullable for accountless receipt/rejection messages. Invitations are excluded from the outbox.

V1–V22 were not edited in this continuation.

## 6. Security and concurrency

Invitations use 32 random bytes with URL-safe encoding; only SHA-256 hashes persist. The raw link is returned once and the UI offers copy/hide controls. The frontend uses a URL fragment, clears it after acceptance, and does not retain mutation results in query cache. Acceptance/password DTO string representations redact secrets. Invitations are neither emailed nor stored in notification payloads.

The API context/accept routes carry the token in the request path: production reverse-proxy/access-log configuration must avoid recording those paths. Fragment protection does not automatically protect API access logs. No password, JWT or raw invitation token appears in this handoff/evidence report.

Onboarding transactions use READ_COMMITTED and pessimistic application → operator → invitation ordering. Approval locks the application; acceptance/reissue serialize and reject consumed/revoked/expired invites. Unique database reservations protect legal identity and token races; user-email uniqueness protects concurrent identity creation. Commercial writes lock trip(s) before operator and revalidate ACTIVE; suspension locks only the operator, avoiding reverse trip locking. All approval/acceptance writes are atomic; forced approval rollback and relevant concurrency scenarios historically passed.

Onboarding commands are state-guarded: repeated approval, rejection, suspension/reactivation or consumed acceptance conflicts rather than creating duplicates. Existing forward trip transitions and attendance/pickup target-state retries retain their idempotency; M25 delay/ETA request keys distinguish identical retry from conflicting payload.

## 7. Frontend changes

`frontend/src/features/onboarding/Onboarding.tsx` and `onboarding.css` implement public `/operator-application`, fragment-based `/operator-invitation`, `/admin` dashboard, application list/detail and operator governance sections. Router/layout navigation, directory types/status labels and existing admin pages integrate them. Public applications collect business information without customer registration; accepted invitees use normal login and role-based operator routing.

System Admin detail shows legal identity, provisioning timestamp, dedicated-admin readiness, invitation metadata, originating application and latest governance history. Rejection needs a nonblank reason; terminal applications have no review/approval/rejection controls. Suspension/reactivation needs a reason. No documents/uploads, bank/payout fields or financial dashboard was added.

## 8. Test results

Historical results below were reused, not executed on 12 October:

| Check | Historical final result |
| --- | --- |
| M26 focused | 45 PASS (30 onboarding, 9 suspension, 6 concurrency) |
| Focused regressions | 50 PASS, previous-session report |
| Frontend | 135/135 PASS, 0 failures |
| FoundationTest | 6/6 PASS, 0 failures/errors |
| Backend unit | 56 PASS, 0 failures/errors in final full verify |
| Backend integration | 431 PASS, 0 failures/errors/skips |
| Package | PASS |
| Approval/invitation concurrency | PASS, previous verification |
| Forced approval rollback | PASS, previous verification |
| Suspension/booking races | PASS, previous verification |

Evidence includes `.tools/m26-focused-final.log`, `.tools/m26-foundation-recheck.log`, `.tools/m26-full-verify.log`, `.tools/m26-complete-frontend-postfix.log` and `.tools/m26-final-package.log`. An earlier standalone unit log had six FoundationTest environment errors; the later FoundationTest and full verification resolved them. Do not mistake that earlier attempt for the final result or claim all historical attempts passed.

New verification: **20 named API/database checks PASS**, zero outstanding product failures. They cover manifest/inventory, service lifecycle and guards, preserved commercial state, reactivation identity/history, future-trip public search and available hold, rejection/no operator, normalized resubmission, immutable rejected history and approved duplicate rejection. `.tools/m26-final-acceptance.json` records the exact names/fixture IDs.

Two harness interruptions occurred: it initially used `inventoryComplete` instead of the actual `complete` field; the old fixture's past planned arrival was rejected after actual departure today. The harness resumed without repeating successful actions and used an explicit future ETA. Neither required a production fix. No focused or full test suite/build/package was rerun.

Final closure verification: valid invitation context and form, control accessibility/bounds, no horizontal overflow, required-field validation and password-mismatch feedback passed at 390×844. No existing test suite was rerun. Read-only database inspection confirmed invitation 3 remains unaccepted/unrevoked, operator 5 remains INACTIVE, and there are zero admin memberships and zero acceptance events for operator 5.

## 9. Browser acceptance

| Remaining scenario | New result and boundary |
| --- | --- |
| A: suspended existing-trip service | API/database PASS; browser operational interaction NOT EXECUTED |
| B: reactivation | API/database PASS; browser shows ACTIVE operator 4, unchanged admin, provisioning and appended history; browser reactivation click NOT EXECUTED |
| C: rejection/resubmission | API/database PASS; browser list/detail confirms separate rejected/resubmitted records and reason; browser submit/reject clicks NOT EXECUTED |
| D: public application ~390px | PASS: top and footer/form controls visually inspected; measured document width 375px within 390px viewport |
| D: invitation ~390px | **PASS**, final closure at 390×844: valid operator/admin context, full-name/password/confirmation fields, accessible controls/action, native required-field feedback and visible mismatch error; document width 390px, no overflow. Invitation not consumed. Earlier missing-token/error view also passed. |
| D: System Admin ~1440px | PASS: dashboard, application list/detail, rejection history, directory and operator detail/governance; measured document width 1425px within 1440px viewport |
| D: console | PASS for no errors: 0 error entries; one distinct React Router missing HydrateFallback warning, three occurrences |
| E: fresh schema/startup | Historical startup evidence reused and existing packaged application restarted; browser not applicable |

Historical browser flows reported PASS: public submission, System Admin visibility/review/approval, one-time invitation, reissue invalidating old invitation, acceptance, operator-console access, suspension hiding profile/rejecting new holds/preserving booking access, operator admin denied System Admin access. These are not newly executed assertions here. Review/approval was used once in the new browser session solely to prepare a pending invitation responsive fixture; acceptance was not repeated.

No major usability/layout regression was observed in inspected states. Warning is documented without broad router changes. Screenshot: `.tools/m26-final-governance-1440.jpg`. Temporary viewport override was reset.

Final mobile findings: operator `M26 rejection fixture`, intended admin `m26.final.admin@example.test`, and expiry loaded correctly. Form inputs and buttons were enabled and bounded between x≈22.8 and x≈367.6 within 390px. With the mismatch error visible, the activation button remained fully accessible at y≈670.8–716.0 in the 844px viewport. Empty submission focused the required full-name field and displayed `Please fill out this field.`; different passwords displayed `Mật khẩu xác nhận chưa khớp.` without calling acceptance. Password values stayed masked. Console contained zero error entries and the known single distinct HydrateFallback warning. Token-free screenshot: `.tools/m26-invitation-390-validation.jpg`; summary: `.tools/m26-invitation-390-evidence.json`. Inputs were cleared, viewport override reset and temporary tab closed afterward. No token was reconstructed or exposed in reports/screenshots/logs.

## 10. Fresh database and packaged startup

Historical `.tools/m26-fresh-db.log`: isolated `busgo_m26_acceptance` had **0 tables** before start; exact MySQL version **9.2.0**. `.tools/m26-startup.log`: packaged jar launch, Empty Schema, successful V1 through V23 migration, successful Hibernate EntityManagerFactory initialization and application startup. `application.yml` uses `ddl-auto: validate`; initialization/startup confirms validation succeeded with that configuration.

12 October: reused this same acceptance database without recreation/reset. The existing packaged `backend/target/busgo-0.0.1-SNAPSHOT.jar` (built 7 October) successfully restarted with dev profile, validated 23 migrations and started on port 8096. MySQL version was queried again as 9.2.0. Fresh migration is historical evidence, not a newly created database on 12 October. Logs warn MySQL 9.2 is newer than this Flyway release's tested support (8.1); migration/validation/startup nevertheless succeeded on the exact runtime.

The development database was not altered. Existing isolated MySQL data directory `.tools/m23-mysql` was reused at 127.0.0.1:3323, with `busgo_m26_acceptance` explicitly selected for every fixture query/write. Backend runs at 8096; Vite at 5173 proxies to 8096. This continuation launched MySQL PID 18688, backend PID 9224 and Vite PID 9168; verify current processes before reuse/cleanup.

## 11. Known limitations and deferred features

No M26 acceptance gap remains. React Router has a nonblocking HydrateFallback warning. SMTP delivery was not newly tested; onboarding receipts/rejections use existing outbox behavior and invitations require private manual sharing. No legal-document upload, applicant self-service tracking/editing, existing-email account reuse, impersonation or System Admin operational bypass. History is bounded in reads (100 events, 50 invitations), retained in storage. Approval/reissue/suspension are not general request-key APIs.

Payments/refunds remain simulated. M22 real platform gateway awaits M26 closure and public HTTPS; M27 commission/receivable/settlement, M28 broader audit/support and M29 UI polish remain deferred.

## 12. Git status

Branch `feature/v2-operator-onboarding`. All pre-existing M26 changes preserved. No commit/push/merge/tag, no staged mutation. `git diff --check` exit 0; Git emits ordinary LF→CRLF conversion warnings, with no whitespace errors. The final exact modified/untracked file listing is recorded below. Ignored `.tools/` acceptance fixtures/logs/screenshots do not appear in ordinary Git status; some pre-existing fixture files hold credentials, so do not share them wholesale.

```text
## feature/v2-operator-onboarding...origin/feature/v2-operator-onboarding
 M backend/src/main/java/com/busgo/admin/AdminOperatorDtos.java
 M backend/src/main/java/com/busgo/admin/AdminOperatorQueryRepository.java
 M backend/src/main/java/com/busgo/admin/AdminOperatorService.java
 M backend/src/main/java/com/busgo/booking/CancellationService.java
 M backend/src/main/java/com/busgo/booking/PartialCancellationService.java
 M backend/src/main/java/com/busgo/common/security/SecurityConfig.java
 M backend/src/main/java/com/busgo/demo/DemoDataSeeder.java
 M backend/src/main/java/com/busgo/notification/NotificationDeliveryWorker.java
 M backend/src/main/java/com/busgo/notification/NotificationService.java
 M backend/src/main/java/com/busgo/operator/entity/OperatorStatus.java
 M backend/src/main/java/com/busgo/operator/repository/OperatorStaffRepository.java
 M backend/src/main/java/com/busgo/route/OperatorRouteService.java
 M backend/src/main/java/com/busgo/trip/TripAggregateCreator.java
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
 M docs/api-contract.md
 M docs/database-design.md
 M docs/development-plan.md
 M frontend/index.html
 M frontend/src/features/operator/management.ts
 M frontend/src/layouts/AdminLayout.tsx
 M frontend/src/layouts/CustomerLayout.tsx
 M frontend/src/pages/admin/AdminPages.tsx
 M frontend/src/routes/router.tsx
 M frontend/src/types/admin.ts
 M frontend/src/utils/status.ts
 M frontend/tests/customer-admin-ux.test.mjs
?? backend/src/main/java/com/busgo/onboarding/OnboardingController.java
?? backend/src/main/java/com/busgo/onboarding/OnboardingDtos.java
?? backend/src/main/java/com/busgo/onboarding/OnboardingService.java
?? backend/src/main/java/com/busgo/operator/OperatorCommerceGuard.java
?? backend/src/main/resources/db/migration/V23__operator_onboarding.sql
?? backend/src/test/java/com/busgo/M26ConcurrencyIT.java
?? backend/src/test/java/com/busgo/M26OnboardingIT.java
?? backend/src/test/java/com/busgo/M26SuspensionIT.java
?? docs/m26-final-handoff.md
?? docs/m26-operator-onboarding.md
?? frontend/src/features/onboarding/Onboarding.tsx
?? frontend/src/features/onboarding/onboarding.css
```

## 13. Remaining work and continuation state

**No remaining M26 acceptance work.** The valid mobile form was inspected successfully using the original absolute link from the authorized System Admin workflow. The earlier invalid URL was not reused or bypassed. The invitation remains pending; inspection and client-side validation did not create an identity or activate an operator. M26 is ready for commit and merge from an acceptance perspective, subject to normal human review. No stage, commit, push, merge or tag was performed.

Acceptance state now: operator 4 ACTIVE, original admin/membership reused; booking 1 preserved; trip 22 COMPLETED after explicit boarding/closure; future trip 23 SCHEDULED with valid fares/inventory and public search visibility. Application 2 remains REJECTED with its original immutable history. Resubmission application 3 was subsequently APPROVED to prepare invitation rendering, creating separate INACTIVE operator 5 with zero admin users/memberships. Pending invitation expires 15 October 2026 at 12:54 Vietnam time. This intentional second legal identity is not a duplicate of reactivated operator 4. Do not rerun the mutation harness from its initial assumptions: it resumed across checkpoints and these fixtures have advanced.

No remaining code defect was found. No full/focused suite needs repeating without a relevant subsequent code change. Final docs cleanup is complete; missing root status/workflow files were noted, not synthesized.

## 14. Recommended next milestone

Follow the existing `docs/development-plan.md` M26 sequencing and `docs/product-roadmap.md` V2 marketplace roadmap: M26 acceptance is closed; establish public HTTPS deployment, then proceed to **M22 real payment integration using one platform-level gateway**. Do not implement M22 in this continuation. M27 settlement/commission remains separate and later.
