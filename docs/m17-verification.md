# M17 verification — 2026-10-03

Implemented in C:\Users\Giang\busgo on feature/management-upgrade. The initial
working tree was clean. No commit, push or deployment was performed.
Design: [m17-cancellation-recovery.md](m17-cancellation-recovery.md).

## 1. Source audit findings

Existing booking states already included CANCELLED, payment states REFUNDED, but
there was no cancellation command, refund record, ticket validity or payment
deadline. Unpaid WEB/PHONE bookings owned BOOKED segment inventory; paid payment
issued unique item tickets. Attendance was booking-item based, with nullable
ticket_id and terminal ticketless PHONE PAY_ON_BOARD NO_SHOW. All commerce and
passenger-operation writes locked the trip first. Source and ownership guards,
public token rotation and Vietnam pickup snapshot utilities were reused.

## 2. Files created

Backend:
- src/main/java/com/busgo/booking/CancellationController.java
- src/main/java/com/busgo/booking/CancellationDtos.java
- src/main/java/com/busgo/booking/CancellationService.java
- src/main/java/com/busgo/booking/PaymentExpiryJob.java
- src/main/resources/db/migration/V14__cancellation_recovery.sql
- src/test/java/com/busgo/M17CancellationConcurrencyIT.java
- src/test/java/com/busgo/M17CancellationIT.java
- src/test/java/com/busgo/PaymentExpiryJobTest.java

Frontend:
- src/features/booking/CancellationSection.tsx
- src/types/recovery.ts
- tests/cancellation.test.mjs
- tests/m17-browser-check.mjs

Docs: m17-cancellation-recovery.md and m17-verification.md.

## 3. Files modified

Backend: BookingDtos, BookingService, OperatorBookingDtos, OperatorBookingService,
Booking and BookingStatusHistory entities, OperatorBookingQueryRepository,
SecurityConfig, OperationsService, PaymentTicketDtos, PaymentTicketService,
Ticket entity, application.yml, CoreDatabaseIT and FoundationTest.

Frontend: api/errors.ts; BookingDetailPage, PaymentPage, PublicPaymentPage,
TicketPage and OperatorBookingsPages; styles.css; types/customer.ts,
types/operator.ts; utils/format.ts; tests/m16b-browser-check.mjs. The M16B browser
fixture horizon now includes all trip lifecycle states so reruns do not collide
with future BOARDING fixtures from M17.

Docs: api-contract.md, development-plan.md and product-roadmap.md.

## 4. Migrations

V14 adds nullable cancellation/deadline metadata, history reason_code, ticket
VALID/VOID metadata, refunds, indexes and constraints. Unique payment_id prevents
duplicate refunds. The composite payment ID/amount/paid timestamp foreign key
requires the full original payment and a successful timestamp. V1–V13 were not
modified. Legacy pending rows keep null deadlines. Existing demo data is unchanged;
browser fixtures use a separate new database. No triggers or SUPER grant required.

## 5. Cancellation domain

PENDING/CONFIRMED → CANCELLED only, full booking, terminal and idempotent.
One transaction validates all domains and allocations before releasing any cell;
repeats preserve refund, history and timestamps.

## 6. Customer cancellation policy

Own WEB booking only, up to six hours before the selected pickup snapshot's
planned departure, inclusive. Open pickup, SCHEDULED/BOARDING trip and no
CHECKED_IN/BOARDED/NO_SHOW item required. An intermediate-pickup test changes origin
to within five hours and pickup to seven hours, proving origin is not the cutoff.

## 7. Operator cancellation policy

Owning OPERATOR_ADMIN only; no customer cutoff. Departure, closure, completion or
any CHECKED_IN/BOARDED/NO_SHOW item blocks cancellation. Staff read only; system
admin never receives operator authority.

## 8. Unpaid cancellation

WEB, PHONE PAY_ON_BOARD and PHONE QR_TRANSFER cancel without payment/refund/ticket.
All contact/item/history data remains. Live tests verify availability after release.

## 9. Paid cancellation

Payment becomes REFUNDED, one full mock refund is inserted, issued tickets become
VOID and seats are released. Original payment amount and paid_at are compared
before/after in tests. UI says Hoàn tiền mô phỏng and explicitly denies bank transfer.

## 10. Refund model

Dedicated refunds record with payment_id, amount, payment_paid_at, refunded_at/by,
reason_code, note and created_at. Database tests insert actual duplicate and wrong
amount snapshots and reject them; unpaid/null paid_at cannot create a refund.
Only CUSTOMER_CANCELLED/OPERATOR_CANCELLED refund reasons are accepted.

## 11. Ticket voiding

Tickets retain ID/code/passenger/seat/payment/history, gain VOID status and metadata.
Historical customer reads have qrData:null. Both detail UIs suppress void QR;
operator boarding commands reject TICKET_NOT_ELIGIBLE. Customer detail links to
the historical void ticket page.

## 12. Inventory release

Complete per-item expected segments, correct trip/seat, BOOKED ownership and empty
hold metadata are checked under deterministic locks. Release clears booking_item_id
and increments version exactly once. Tests preserve other holds, blocked cells and
non-overlapping bookings, and let a new customer hold released cells. A missing
cell in a two-seat paid booking aborts with no refund, void or partial release.

## 13. Payment deadline

New WEB: 15 minutes; new PHONE QR_TRANSFER: 30 minutes; both capped by selected
pickup. Configurable positive durations. PAY_ON_BOARD and legacy null deadlines
are exempt. New confirmations check the deadline after acquiring the shared lock.

## 14. Expiry job

Bounded default batch 100, ordered deadline/ID, one transactional service call per
booking. It rechecks eligibility, skips paid/cancelled/null/PAY_ON_BOARD and
operationally incompatible rows. Errors log and roll back that booking while later
rows continue. PAYMENT_TIMEOUT creates no payment/ticket/refund. Live browser
tests run the real scheduler at one-second intervals; only each freshly created
timeout fixture has its deadline moved before its stored created_at. This matches
existing JPA/JDBC timestamp storage and does not alter seeded bookings.

## 15. Suspension recovery

An eligible owning customer can cancel unpaid WEB while its operator is inactive.
Paid suspended cancellation and accountless PHONE manual recovery remain deferred
to platform support. Suspension alone never cancels a booking. Scheduled deadline
recovery remains independent of operator activation while retaining operation guards.

## 16. Attendance interaction

EXPECTED/missing state may cancel. CHECKED_IN, BOARDED and NO_SHOW reject the whole
booking; attendance/history is never deleted or reversed. Dedicated races cover
check-in, direct boarding, paid no-show and ticketless unpaid no-show. Live
checked-in cancellation is hidden and rejected by the backend.

## 17. Lock order

Cancellation: trip → operator → booking → payment rows → tickets → attendance/
pickup closure → ordered inventory. Existing payment/operations child ordering is
preserved; every conflicting command takes the exclusive trip lock before child
locks, so those child order differences cannot deadlock each other on a trip.
Trip/operator/booking refreshes use pessimistic current reads under REPEATABLE_READ.

## 18. API changes

Explicit customer/operator POST /bookings/{id}/cancel commands with optional note.
Customer/operator GET /bookings/{id}/recovery and additive detail.recovery expose
eligibility, deadlines, cancellation, refund, validity and history. Owner isolation
404; state 409; role 403; anonymous 401; body validation 400. Reasons are derived by
server authority. No arbitrary status edit or public cancellation endpoint.

## 19. Customer UI

Detail shows eligibility/cutoff/deadline, unpaid or paid action, confirmation dialog,
final cancellation, mock refund, void ticket and history. Dialog includes code,
trip/journey, pickup/time, contact, seats, amount, payment state, policy and result.
Payment screen shows the separate deadline. Historical QR is suppressed.

## 20. Operator UI

Explicit section shows eligibility, reason, actor, time, refund and history.
Admin-only confirmation action; staff read only. Cancelled PHONE collection/link
actions are hidden. Existing CANCELLED/REFUNDED list filters stay available;
refund labels explicitly say Hoàn tiền mô phỏng.

## 21. History

Existing status history is extended with structured reason_code. Payment history
remains; cancellation appends one transition and does not overwrite previous rows.
Actor/cancelled_at/refund amount/refunded_at/payment_due_at enable later M18 reports.

## 22. Security

Tests cover foreign operator/customer 404 (including concurrent attempts), PHONE
self-service rejection, staff/system admin denial, anonymous/public cancellation
denial, public confirmation after cancellation, and VOID boarding rejection.
Existing token rotation and minimal anonymous responses remain unchanged.

## 23. Concurrency behavior

Independent transactions use barriers or latches. Controlled interleavings prove
payment first → paid cancellation/refund, cancellation first → payment rejection,
and committed payment → waiting expiry no-op. Other races cover unpaid/paid
duplicates, public payment, expiry, check-in/board/no-show, ticketless no-show,
pickup closure, seat reuse and foreign actors. Duplicate release versions, payment,
refund, ticket and history counts are asserted.

## 24. Unit tests

mvn test passed: 35 tests, zero failures/errors/skips. New job tests validate batch
bounds and continuation/counts after a per-booking failure. Log:
.tools/m17-unit-final.log.

## 25. MySQL integration tests

mvn verify -Pmysql-integration passed: 35 unit and 216 integration tests, zero
failures/errors/skips. M17 adds 9 domain/security and 16 concurrency tests to the
existing 191 integration cases. New isolated MySQL 9.2 runs on 13317 with
busgo_m17_verify_final and busgo_m17_browser; existing app databases were not reset.
Log: .tools/m17-verify-final.log. mvn package -DskipTests passed; package log:
.tools/m17-package.log. Java 17.0.20.1 and Node 22.20.0 were used.

## 26. Frontend tests/build

npm test passed: 85 tests, zero failures/skips. Five new real-component render
tests verify customer paid/unpaid actions, cutoff, staff read-only behavior,
attendance conflicts, mock refund/VOID presentation, operator QR suppression and
public cancelled wording. npm run build passed (TypeScript/Vite).
Logs: .tools/m17-frontend-tests-final.log and .tools/m17-build-final.log.
git diff --check passed.

Warnings retained: Flyway warns MySQL 9.2 is newer than tested support; Mockito/JVM
warns about bootstrap class sharing; existing exception-handler compiler uses
deprecated APIs; Zod PURE annotations are removed by esbuild; main frontend bundle
is above 500 kB. Negative constraint tests intentionally log SQL errors. Demo
collision/inactive catalog tests intentionally warn about skipped trips. Git emits
LF→CRLF conversion notices. None were suppressed.

Earlier failed iterations were fixed: trigger migration privileges (replaced by
ordinary constraints), SQL pickup key name, test security context, acceptable race
error ordering, timestamp basis for browser timeouts, future fixture bus collisions,
and a redundant TypeScript comparison. The results above refer to successful final
runs. Dependency/build access required normal execution outside the sandbox.

## 27. Browser verification

Live headless Edge through Vite at 390x1000, 820x1000 and 1440x1000 passed:
A WEB unpaid cancellation; B WEB paid payment/cancellation/full mock refund/VOID;
C PHONE PAY_ON_BOARD unpaid admin cancellation; D PHONE QR public payment/admin
cancellation/blocked public link; E checked-in cancellation guard; F real scheduled
QR expiry/seat release/payment rejection. APIs confirm no fabricated unpaid
commerce, refunded paid state, ticket validity and available seats. No page errors
or document horizontal overflow occurred. Customer paid and operator cancelled
screens and the mobile confirmation dialog were visually reviewed.

Evidence: .tools/m17-browser/results.json, screenshots and
.tools/m17-browser-final.log. A new fixture trip is created per width; existing
demo trips/bookings are never overwritten or automatically cancelled.

G regressions passed: existing m16a-browser-check.mjs at all three widths covers
PHONE methods, paid ticket QR, seat conflicts; a full WEB search/hold/booking/
payment/ticket/My Bookings flow at 1440, staff denial and invalid links. Existing
m16b-browser-check.mjs passes all three widths for employees, readiness, crew
overlap, boarding, paid/ticketless no-show, pickup closure, intermediate collection
after departure and lifecycle completion. Evidence:
.tools/m17-m16a-regression/results.json and .tools/m17-m16b-regression/results.json,
with their screenshots/logs. All use the packaged M17 backend and live APIs.

## 28. Unverified items

Production deployment/migration, MySQL 8.4, physical devices, real scanners and
full screen-reader/keyboard accessibility audit were not performed. Legacy
inconsistent payment timestamp rows may require repair before the additive check;
there is no automatic legacy rewrite. Real bank refunds are intentionally absent.

## 29. Deferred scope

Partial cancellation/refund, seat change, rescheduling, real payment gateway/bank
refund, refund accounting, trip-wide batch cancellation, reporting screens and
platform-support cancellation for suspended paid/accountless PHONE bookings.
No cancellation of every suspended operator booking, commit, push or deployment.

## 30. git status --short

The exact final file inventory follows. Local logs, runtime databases and browser
screenshots are ignored artifacts under .tools.

```text
 M backend/src/main/java/com/busgo/booking/BookingDtos.java
 M backend/src/main/java/com/busgo/booking/BookingService.java
 M backend/src/main/java/com/busgo/booking/OperatorBookingDtos.java
 M backend/src/main/java/com/busgo/booking/OperatorBookingService.java
 M backend/src/main/java/com/busgo/booking/entity/Booking.java
 M backend/src/main/java/com/busgo/booking/entity/BookingStatusHistory.java
 M backend/src/main/java/com/busgo/booking/repository/OperatorBookingQueryRepository.java
 M backend/src/main/java/com/busgo/common/security/SecurityConfig.java
 M backend/src/main/java/com/busgo/operations/OperationsService.java
 M backend/src/main/java/com/busgo/payment/PaymentTicketDtos.java
 M backend/src/main/java/com/busgo/payment/PaymentTicketService.java
 M backend/src/main/java/com/busgo/ticket/entity/Ticket.java
 M backend/src/main/resources/application.yml
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
 M docs/api-contract.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/api/errors.ts
 M frontend/src/pages/BookingDetailPage.tsx
 M frontend/src/pages/PaymentPage.tsx
 M frontend/src/pages/PublicPaymentPage.tsx
 M frontend/src/pages/TicketPage.tsx
 M frontend/src/pages/operator/OperatorBookingsPages.tsx
 M frontend/src/styles.css
 M frontend/src/types/customer.ts
 M frontend/src/types/operator.ts
 M frontend/src/utils/format.ts
 M frontend/tests/m16b-browser-check.mjs
?? backend/src/main/java/com/busgo/booking/CancellationController.java
?? backend/src/main/java/com/busgo/booking/CancellationDtos.java
?? backend/src/main/java/com/busgo/booking/CancellationService.java
?? backend/src/main/java/com/busgo/booking/PaymentExpiryJob.java
?? backend/src/main/resources/db/migration/V14__cancellation_recovery.sql
?? backend/src/test/java/com/busgo/M17CancellationConcurrencyIT.java
?? backend/src/test/java/com/busgo/M17CancellationIT.java
?? backend/src/test/java/com/busgo/PaymentExpiryJobTest.java
?? docs/m17-cancellation-recovery.md
?? docs/m17-verification.md
?? frontend/src/features/booking/CancellationSection.tsx
?? frontend/src/types/recovery.ts
?? frontend/tests/cancellation.test.mjs
?? frontend/tests/m17-browser-check.mjs
```
