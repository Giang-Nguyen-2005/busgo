# M16A verification — 2026-10-02

Implemented on `feature/management-upgrade`. No commit or push. Existing changes
to development-plan.md and the untracked product-roadmap.md were preserved and
extended. Design/source audit: [m16a-assisted-booking.md](m16a-assisted-booking.md).
Contracts: [api-contract.md](api-contract.md), M16A section.

## Final command results

| Check | Result |
| --- | --- |
| mvn test | Passed, 28 unit tests |
| mvn verify -Pmysql-integration | Passed, 28 unit + 173 MySQL integration tests, 0 failures/errors/skips |
| mvn package -DskipTests | Passed |
| npm test | Passed, 76 tests |
| npm run build | Passed, TypeScript and Vite |
| git diff --check | Passed |

Environment: installed Java 17.0.16, Node 22.20.0, local isolated MySQL 9.2 on port
13315. Verification uses dedicated busgo_m16a_verify; browser uses a separate
busgo_m16a_browser schema with explicit dev,demo seeding. System MySQL service and
existing application databases were not used or reset. Maven/Vite required normal
local dependency access outside the restricted execution sandbox. No dependencies
were added. Existing Zod annotation and >500 kB main bundle warnings remain. Flyway
warns that MySQL 9.2 is newer than its tested support range; migrations and Hibernate
validation nevertheless passed. MySQL 8.4 itself was not exercised on this machine.

Evidence logs (ignored local outputs): .tools/m16a-unit.log,
.tools/m16a-verify.log, .tools/m16a-package.log, .tools/m16a-frontend-tests.log,
.tools/m16a-build.log. Earlier failed iterations were corrected: JRE/JDK selection,
CHAR mapping, repeatable-read current-state refresh, migration-count assertion and
new test endpoint/flush setup. Final results above refer to passing runs.

## New MySQL security and concurrency coverage

M16AAssistedBookingIT (six tests) verifies offline bookings without accounts or
email, BOOKED segment reservation without payment/tickets, operator source/method
reads with nullable customer, manual collector/reference metadata, repeated
collection, exact customer ownership, opaque/hash-only tokens, minimal anonymous
contract, token rotation, malformed/random/numeric tokens, repeat paid links,
customer/staff/system-admin mutation denial, foreign operator create/read/collect/
link denial, inactive operator restrictions, missing requested/booked inventory,
atomic rollback without orphan holds, non-overlapping segment reuse, validation,
and manual collection during BOARDING without changing trip/boarding state.

M16AConcurrencyIT (four tests) uses independent committed MySQL transactions and
barriers to verify WEB hold/conversion vs PHONE booking, two operator employees
vs one seat, manual vs anonymous confirmation, repeated manual collection, and
non-overlapping reuse. Assertions require exactly one booking winner for overlap,
or exactly one payment/ticket/history for duplicate confirmation. Test cleanup
deletes only rows belonging to each newly created fixture and its test accounts.

Existing integration suites continue to cover customer hold/booking/payment/ticket
ownership, complete expected matrices, trip/operator suspension, lifecycle,
operator isolation, staff restrictions, timestamps, schema constraints, refresh
races and non-destructive demo seeding. CoreDatabaseIT now expects eleven migrations;
M9PaymentTicketIT asserts the canonical renamed MOCK_ONLINE method.

## Frontend and browser evidence

Three new frontend tests cover nullable offline account display, truthful unpaid
and simulated payment wording, paid delivery instructions, admin-only creation/
collection/link presentation, and staff/system-admin/customer route denial. All
73 existing frontend tests also pass.

`frontend/tests/m16a-browser-check.mjs` runs against the live isolated backend through
Vite using headless Edge/Playwright. It does not stub booking/payment/inventory APIs.
At each of 390×1000, 820×1000, and 1440×1000 it signs into the operator and completes:

- PHONE + PAY_ON_BOARD: journey/seat/contact/payment review → create → unpaid and
  no QR ticket → server-observed unavailable seat → conflicting create returns
  SEAT_NOT_AVAILABLE/409 → manual collection → ticket QR appears.
- PHONE + QR_TRANSFER: create unpaid → issue and copy link (clipboard checked) →
  open in a separate anonymous browser context → explicit simulated confirmation →
  paid result → reload retains paid state and removes confirmation action → operator
  detail reload shows ticket QR.
- No document horizontal overflow at reviewed creation, anonymous payment and
  paid detail views; operator contexts report no browser page errors.

The same run verifies real WEB customer registration/login → public search → seat
hold → contact booking → mock checkout → issued ticket, staff creation-page denial,
and the invalid-token page. Result JSON: .tools/m16a-browser/results.json. Screenshot
set: .tools/m16a-browser/ (review, paid and anonymous payment for all three widths,
plus WEB ticket). The 390 creation review, 1440 creation review and 820 anonymous
payment screenshots were visually inspected. Explicit accessible names were added
to the four new select controls after browser testing exposed label ambiguity.

## Limits and deferred scope

No live production database migration, MySQL 8.4 run, physical-device, full screen
reader, real bank transfer/gateway, Zalo send, public offline ticket delivery or
production deployment is claimed. Link expiry is the existing payment window,
not a separate TTL; issuing a replacement revokes the previous link. Full status
and data defaults are documented in V11 and the design. Offline ticket delivery
remains operator-assisted. Public paid links cannot mutate booking/contact/seat
fields. Staff capabilities move to M16B/M19; boarding, crew, cancellation/refunds,
rescheduling, reporting screens, COUNTER/AGENT and real integrations remain deferred.

## File inventory and git status

The exact final working-tree inventory follows. Paths marked `??` are untracked;
product-roadmap.md was already untracked when this task started. development-plan.md
already contained user edits. All other entries belong to this implementation.

```text
 M README.md
 M backend/src/main/java/com/busgo/booking/BookingService.java
 M backend/src/main/java/com/busgo/booking/OperatorBookingController.java
 M backend/src/main/java/com/busgo/booking/OperatorBookingDtos.java
 M backend/src/main/java/com/busgo/booking/OperatorBookingService.java
 M backend/src/main/java/com/busgo/booking/entity/Booking.java
 M backend/src/main/java/com/busgo/booking/repository/BookingRepository.java
 M backend/src/main/java/com/busgo/booking/repository/OperatorBookingQueryRepository.java
 M backend/src/main/java/com/busgo/common/security/SecurityConfig.java
 M backend/src/main/java/com/busgo/hold/SeatHoldService.java
 M backend/src/main/java/com/busgo/payment/PaymentTicketService.java
 M backend/src/main/java/com/busgo/payment/entity/Payment.java
 M backend/src/main/java/com/busgo/payment/entity/PaymentMethod.java
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/M9PaymentTicketIT.java
 M docs/api-contract.md
 M docs/development-plan.md
 M frontend/src/api/errors.ts
 M frontend/src/api/operatorApi.ts
 M frontend/src/features/operator/TripWorkspace.tsx
 M frontend/src/features/operator/operator.css
 M frontend/src/pages/operator/OperatorBookingsPages.tsx
 M frontend/src/routes/router.tsx
 M frontend/src/styles.css
 M frontend/src/types/operator.ts
 M frontend/src/utils/format.ts
?? backend/src/main/java/com/busgo/booking/AssistedBookingDtos.java
?? backend/src/main/java/com/busgo/booking/AssistedBookingService.java
?? backend/src/main/java/com/busgo/booking/PublicPaymentController.java
?? backend/src/main/java/com/busgo/booking/entity/BookingSource.java
?? backend/src/main/resources/db/migration/V11__assisted_booking.sql
?? backend/src/test/java/com/busgo/M16AAssistedBookingIT.java
?? backend/src/test/java/com/busgo/M16AConcurrencyIT.java
?? docs/m16a-assisted-booking.md
?? docs/m16a-verification.md
?? docs/product-roadmap.md
?? frontend/src/pages/PublicPaymentPage.tsx
?? frontend/src/pages/operator/OperatorBookingCreatePage.tsx
?? frontend/src/types/assisted.ts
?? frontend/tests/assisted-booking.test.mjs
?? frontend/tests/m16a-browser-check.mjs
```
