# M16B verification — 2026-10-03

Implemented on feature/management-upgrade. No commit or push was performed.
Design and source audit: [m16b-crew-boarding.md](m16b-crew-boarding.md).
Contracts: [api-contract.md](api-contract.md), M16B section.

## 1. Source audit findings

M16A PHONE and WEB commerce were already implemented. Unpaid bookings reserve
segment inventory and have no tickets; paid tickets are immutable per booking
item. Employees therefore remain separate from account staff, and operational
attendance is per booking item with a nullable paid-ticket reference. Existing trip-first payment locking, bus overlap guards,
operator-context isolation and read-only staff are reused. No V1-V12 migration was
edited. JPA lifecycle status now flushes before JDBC commands in a nested transaction.

## 2. Files created

- backend/src/main/java/com/busgo/operations/OperationsDtos.java
- backend/src/main/java/com/busgo/operations/OperationsService.java
- backend/src/main/java/com/busgo/operations/OperationsController.java
- backend/src/main/resources/db/migration/V12__crew_boarding.sql
- backend/src/main/resources/db/migration/V13__reservation_attendance.sql
- backend/src/test/java/com/busgo/M16BConcurrencyIT.java
- backend/src/test/java/com/busgo/M16BSecurityIT.java
- backend/src/test/java/com/busgo/M16BFixtures.java
- backend/src/test/java/com/busgo/OperationsLifecycleTest.java
- frontend/src/api/operationsApi.ts
- frontend/src/types/operations.ts
- frontend/src/features/operator/CrewBoarding.tsx
- frontend/tests/crew-boarding.test.mjs
- frontend/tests/m16b-browser-check.mjs
- docs/m16b-crew-boarding.md
- docs/m16b-verification.md

## 3. Files modified

Backend: SecurityConfig; DemoDataSeeder; PaymentTicketService;
OperatorTripOperationsService. Existing tests: CoreDatabaseIT, FoundationTest,
M12OperationsConcurrencyIT, M12OperatorOperationsIT, M14AContractTest,
M16AConcurrencyIT, M9PaymentConcurrencyIT. Old concurrency cleanup deletes only its
fixture's new operational rows, preserving unrelated records. M12 payment tests
now distinguish pickup readiness from the existing customer payment guard.

Frontend: api/errors.ts; features/auth/access.ts; TripWorkspace.tsx;
TripStatusAction.tsx; features/operator/operations.ts, queries.ts, operator.css;
pages/operator/OperatorTripsPages.tsx, OperatorTripOperationsPages.tsx;
routes/router.tsx; tests/operator-operations.test.mjs and m16a-browser-check.mjs.
The M16A script accepts a separate evidence output directory and now verifies
My Bookings after ticket issuance. Docs: api-contract, development-plan, roadmap.

## 4. Migrations

V12 adds operator_employees, employee_capabilities, driver_profiles,
trip_crew_assignments, ticket_boarding, trip_stop_operations and operational_history.
Unique keys protect operator/code, active trip/employee/duty, ticket boarding, and
trip/stop closure. Supporting employee-overlap and history indexes are included.
V13 backfills booking_item_id only on existing attendance, then enforces unique booking-item attendance, nullable unique ticket_id, and ticketless NO_SHOW-only records. No historical passenger state backfill; no existing bookings or tickets are rewritten.

## 5–8. Employee, crew, overlap and readiness

ACTIVE/INACTIVE operational employees need no login. DRIVER/ATTENDANT capabilities
may coexist. Driver licence fields are required; edits use version checks.
Deactivation/removing a capability rejects unreleased active-trip assignments.
Multiple crew members are supported. Releases preserve actor/time/history.
Trip -> bus -> ascending employee row locks precede READ_COMMITTED overlap checks;
back-to-back intervals pass. Overlap race tests use different buses, so a shared
bus lock cannot accidentally provide the employee protection. BOARDING requires
an operational bus, valid active driver, unexpired licence through operation/end
date and no invalid/conflicting crew. Licence class is not a legal-compliance claim.

## 9–12. Attendance, intermediate pickup, closure and no-show

EXPECTED -> CHECKED_IN -> BOARDED; explicit NO_SHOW accepts EXPECTED/CHECKED_IN.
Explicit direct-board atomically records check-in + board. Terminal states cannot
be changed; duplicate target commands retain committed timestamps/events.
Paid issued tickets are required for check-in/boarding. Explicit unpaid PHONE PAY_ON_BOARD no-show is supported. Origin uses BOARDING; intermediate open pickups
also allow DEPARTED. Planned times are advisory. Closure rejects every unresolved
valid passenger, including unpaid reservations, and never automatically marks
absence. Origin must close before departure; every active pickup closes before
completion. NO_SHOW preserves booking/payment/BOOKED inventory and records pickup,
actor, time and history. Ticketless NO_SHOW resolves pickup attendance and blocks later new full-booking payment.

## 13. PAY_ON_BOARD integration

Manifest collection reuses M16A's existing full-booking payment command and shows
the booking total before confirmation. Successful payment issues tickets and
EXPECTED attendance in one transaction; it does not check in or board. Only
employee PHONE collection extends to an open intermediate pickup after departure.
Customer/anonymous payment windows remain unchanged. No partial payment exists.

### Operational gap correction (V13)

Attendance is anchored to booking_item, with nullable ticket_id, rather than
fabricating unpaid tickets. The unique item key provides one attendance outcome
per reservation passenger; paid-ticket commands keep their existing behavior.
Only PENDING PHONE PAY_ON_BOARD ticketless reservations can be explicitly absent.
No-show preserves booking fields/status history, payments, tickets and every
inventory field. The API records actor, timestamp, booked pickup and BOOKING_ITEM
history. Closure and completion resolve attendance by item. Unresolved unpaid
items continue to block closure. NO_SHOW remains terminal, including after closure.
New full-booking payment rejects if any item has NO_SHOW; paid retries remain
idempotent. All commands share the trip lock; payment uses a locking current read
for the terminal guard even when called from a REPEATABLE_READ transaction.

| Required case | Automated coverage |
| --- | --- |
| A: unpaid absent, no commercial changes, pickup closes | unpaidAbsencePreservesCommerceAndClosesPickup; compares full booking/inventory/history snapshots, no payments/tickets; also completes trip |
| B: paid boards and unpaid no-shows at same pickup | paidAndUnpaidPassengersResolveSamePickup |
| C: unresolved unpaid blocks closure | A, B and oneNoShowBlocksFullBookingPaymentButRemainingItemsStillNeedResolution |
| D: unpaid cannot check in/board | A plus frontend disabled check-in and database ticketless-NO_SHOW constraint |
| E: payment then attendance | Existing paid check-in/board and intermediate payment/boarding tests |
| F: duplicate unpaid no-show | duplicateUnpaidAbsenceIsIdempotentUnderConcurrency; unchanged row and one history event |
| G: closure/no-show race | unpaidAbsenceAndClosureSerializeSafely; retries closure after resolution |
| H: commerce regressions | Full MySQL and frontend suites, including M16A/WEB/PHONE/payment/ticket tests |

Additional coverage: payment/no-show race, terminal full-booking payment with
multiple items, invalid method/stop/item, API operator ownership, staff/system
admin/customer/anonymous denial and body validation. The manifest enables unpaid
PHONE PAY_ON_BOARD absence while keeping check-in/boarding paid-only, hides new
collection for no-show bookings, and explains that inventory remains reserved.

## 14–17. APIs, UI, permissions and history

Employee GET/POST/PATCH; trip crew GET/PUT; attendance/pickups/history GET;
explicit ticket check-in/board/direct-board/no-show POST; explicit close-pickup
POST. Paths, field names and conflict codes are documented in api-contract.md.
The compatible legacy manifest API remains; the existing Hành khách UI uses the
richer attendance read including unpaid booking items. Navigation adds Nhân sự
vận hành; Overview adds crew/readiness/actions. Manifest has payment/attendance,
pickup filters, search and explicit confirmation. Staff read only; admins mutate.
SYSTEM_ADMIN and public payment links have no operator authority. Domain history
is append-only through the API and contains entity/actor IDs, action/time and
optional reason without copied customer personal data.

## 18–20. Final command results

| Command | Final result |
| --- | --- |
| mvn test | Passed: 33 unit tests, zero failures/errors/skips |
| mvn verify -Pmysql-integration | Passed: 33 unit + 191 MySQL integration tests, zero failures/errors/skips |
| mvn package -DskipTests | Passed |
| npm test | Passed: 80 tests, zero failures/skips |
| npm run build | Passed: TypeScript and Vite |
| git diff --check | Passed; Git line-ending conversion notices retained below |

Five new unit cases verify that readiness/closure/completion failures never write
lifecycle/history, that transitions flush before history, repeats do not duplicate
history, and skipped lifecycle transitions never reach operational guards.

Fifteen MySQL concurrency/domain tests cover same driver on overlapping trips using
independent committed transactions and barriers, back-to-back and distinct crews,
inactive/deactivation/expired-licence readiness, duplicate check-in, duplicate
board, board/no-show race, closure/board race, completion/unresolved intermediate
passenger race and payment/boarding race. Three security/eligibility tests cover
foreign employee read/edit, foreign crew/boarding, wrong-trip tickets,
SYSTEM_ADMIN/customer/staff mutation denial, staff attendance reads, public-token
and anonymous denial, refunded/cancelled eligibility and no historical backfill.
Existing WEB/PHONE commerce, payment, ownership, inventory and seed tests pass.
Four new frontend tests cover unpaid blocked actions, terminal/closed pickup
controls and read-only employee access; lifecycle expectations were updated for
intermediate collection and the new query refreshes.

Environment: Java 17.0.20.1, Node 22.20.0, isolated local MySQL 9.2 at 13315.
Dedicated busgo_m16b_verify and busgo_m16b_browser schemas were used. The system
MySQL service and existing application databases were not reset or migrated.
Normal local dependency access was needed outside the restrictive sandbox.
No project dependencies were added. Logs are ignored local artifacts:
.tools/m16b-gap-unit.log, m16b-gap-verify-final.log, m16b-gap-package.log,
m16b-gap-frontend-tests.log and m16b-gap-build.log.

Warnings retained: Flyway reports MySQL 9.2 newer than its tested support range;
Mockito/JVM warns about bootstrap class sharing; existing exception-handler tests
have deprecated-API compiler notices; esbuild removes problematic Zod PURE
annotations; the main frontend chunk remains above 500 kB. Constraint-negative
tests intentionally log database errors. Demo collision/inactive catalogue tests
intentionally warn about skipped trips. Git reports LF -> CRLF conversion notices
for the three updated planning/contract documents. These were not suppressed.

Earlier failed verification iterations were corrected: a test mock dependency,
new-unit-test fixture identity construction, lifecycle flush visibility, anonymous
security-test context, Windows build-jar lock, seat fixture contract, select
accessible names, and a UI refresh race that dismissed a new closure confirmation.
Final results above refer only to successful final runs.
The gap correction also fixed browser-test timing: absence assertions now wait
for the rendered status rather than the identically named action button, and
action checks wait for the preceding mutation's query refresh. Final live runs
passed for all three widths. The 390px origin-resolved screenshot was reviewed:
unpaid NO_SHOW retains the unpaid label, hides collection, explains retained seats,
and shares a closed origin pickup with paid boarded/no-show passengers.

## 21. Browser verification

frontend/tests/m16b-browser-check.mjs runs live headless Edge against the packaged
backend through Vite at 390x1000, 820x1000 and 1440x1000. Each width uses fresh
API-created future trips on separate buses rather than rewriting existing demo
passengers. It creates and edits an employee in the UI, rejects BOARDING without
crew, assigns crew, rejects the employee on an overlapping trip, checks in/boards a
paid passenger, verifies unpaid check-in disabled and unpaid no-show enabled, rejects closure with unresolved
passengers, explicitly records paid and unpaid no-shows, confirms no unpaid ticket/payment and rejects later payment, closes origin and departs, rejects early
completion, collects the intermediate PAY_ON_BOARD booking through the UI, checks
in/boards after DEPARTED, closes remaining pickups and completes. Server state and
history counts are checked. No document horizontal overflow or page errors occurred.
The final run also checks that collection amounts are real and stale missing-crew
errors clear after successful assignment. Evidence: .tools/m16b-gap-browser/results.json
and employee/crew/origin/intermediate/completed screenshots for all widths.

The live M16A regression at all three widths passes PHONE PAY_ON_BOARD collection,
PHONE QR_TRANSFER anonymous mock payment, ticket QR, reserved-seat conflict and
no overflow. A WEB customer passes registration/login, search, hold, booking,
mock payment, issued ticket, My Bookings and booking detail. Staff creation denial
and invalid public-token handling pass. Evidence: .tools/m16b-gap-regression/results.json,
its screenshots, and .tools/m16b-gap-regression.log. The 390 manifest and 1440 crew
screenshots were visually reviewed; final output was rechecked after the final run.
Final gap browser evidence is .tools/m16b-gap-browser/results.json and
.tools/m16b-gap-browser.log. Both live scripts used the final packaged code;
the separate running copy avoided locking Maven's target jar during packaging.

## 22. Unverified items

No MySQL 8.4, production migration/deployment, physical device, real scanner,
complete screen-reader audit, real transport service or legal compliance is claimed.
The final local browser and MySQL scenarios use test-only schemas and fixtures.
Historical passenger attendance was not backfilled. Licence edit history snapshots
remain unimplemented. Unpaid reservation absence is explicitly supported by V13.

## 23. Deferred scope

Cancellation/refund, seat change/reschedule, camera QR scanning, driver mobile,
payroll, GPS, rest-rule optimization, actual arrivals, attendance reversal/pickup
reopening and staff mutation capabilities. No commit, push or deployment.

## 24. git status --short

```text
 M backend/src/main/java/com/busgo/common/security/SecurityConfig.java
 M backend/src/main/java/com/busgo/demo/DemoDataSeeder.java
 M backend/src/main/java/com/busgo/payment/PaymentTicketService.java
 M backend/src/main/java/com/busgo/trip/operations/OperatorTripOperationsService.java
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
 M backend/src/test/java/com/busgo/M12OperationsConcurrencyIT.java
 M backend/src/test/java/com/busgo/M12OperatorOperationsIT.java
 M backend/src/test/java/com/busgo/M14AContractTest.java
 M backend/src/test/java/com/busgo/M16AConcurrencyIT.java
 M backend/src/test/java/com/busgo/M9PaymentConcurrencyIT.java
 M docs/api-contract.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/api/errors.ts
 M frontend/src/features/auth/access.ts
 M frontend/src/features/operator/TripStatusAction.tsx
 M frontend/src/features/operator/TripWorkspace.tsx
 M frontend/src/features/operator/operations.ts
 M frontend/src/features/operator/operator.css
 M frontend/src/features/operator/queries.ts
 M frontend/src/pages/operator/OperatorTripOperationsPages.tsx
 M frontend/src/pages/operator/OperatorTripsPages.tsx
 M frontend/src/routes/router.tsx
 M frontend/tests/m16a-browser-check.mjs
 M frontend/tests/operator-operations.test.mjs
?? backend/src/main/java/com/busgo/operations/
?? backend/src/main/resources/db/migration/V12__crew_boarding.sql
?? backend/src/main/resources/db/migration/V13__reservation_attendance.sql
?? backend/src/test/java/com/busgo/M16BConcurrencyIT.java
?? backend/src/test/java/com/busgo/M16BFixtures.java
?? backend/src/test/java/com/busgo/M16BSecurityIT.java
?? backend/src/test/java/com/busgo/OperationsLifecycleTest.java
?? docs/m16b-crew-boarding.md
?? docs/m16b-verification.md
?? frontend/src/api/operationsApi.ts
?? frontend/src/features/operator/CrewBoarding.tsx
?? frontend/src/types/operations.ts
?? frontend/tests/crew-boarding.test.mjs
?? frontend/tests/m16b-browser-check.mjs
```
