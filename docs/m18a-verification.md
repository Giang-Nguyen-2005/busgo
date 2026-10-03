# M18A verification — 2026-10-03

Implemented in C:\Users\Giang\busgo on feature/management-upgrade. Initial working
tree was clean. No commit, push or deployment performed. Metric definitions and
exclusions: [report design](m18a-management-reports.md).

## 1. Source audit

Read the roadmap, development plan, API contract, M16A assisted booking, M16B
crew/attendance and M17 recovery/verification documents and actual booking/payment,
refund/ticket, inventory/trip/route, operator-context, security and frontend sources.
PENDING owns BOOKED cells; successful payments alone issue tickets. REFUNDED keeps
original paid_at/amount; refunds have separate refunded_at. Tickets preserve
historical issuance with VALID/VOID. Attendance belongs to booking items and can
be ticketless NO_SHOW. Existing time conventions and operator ownership are reused.

## 2. Files created

Backend (paths below are relative to repository root):

- backend/src/main/java/com/busgo/reporting/ReportController.java
- backend/src/main/java/com/busgo/reporting/ReportDtos.java
- backend/src/main/java/com/busgo/reporting/ReportFilter.java
- backend/src/main/java/com/busgo/reporting/ReportRepository.java
- backend/src/main/java/com/busgo/reporting/ReportService.java
- backend/src/main/resources/db/migration/V15__management_report_indexes.sql
- backend/src/test/java/com/busgo/M18AReportsIT.java
- backend/src/test/java/com/busgo/ReportFilterTest.java

Frontend:

- frontend/src/api/reportsApi.ts
- frontend/src/features/operator/Reports.tsx
- frontend/src/features/operator/reports.css
- frontend/src/types/reports.ts
- frontend/tests/m18a-browser-check.mjs
- frontend/tests/reports.test.mjs

Documentation: docs/m18a-management-reports.md and docs/m18a-verification.md.

## 3. Files modified

- backend/src/test/java/com/busgo/CoreDatabaseIT.java — expects 15 migrations.
- backend/src/test/java/com/busgo/FoundationTest.java — mock report repository in database-free context.
- frontend/src/features/auth/access.ts — one admin-only Báo cáo navigation item.
- frontend/src/pages/operator/OperatorHomePage.tsx — management summary from report service.
- frontend/src/routes/router.tsx — lazy /operator/reports route.
- docs/api-contract.md, docs/development-plan.md, docs/product-roadmap.md.

## 4. Migration / indexes

V15 adds five range indexes only: booking creation/cancellation, payment paid time,
refund time and ticket issuance time, each followed by trip/booking/payment join ID.
V1–V14 are unchanged. Existing operator-route/departure, item, payment booking,
ticket booking/item, refund payment and attendance/inventory uniqueness indexes
already cover joins. No duplicate range indexes or report-result tables added.
Fresh local schemas successfully migrated through V15; full schema suite passes.

## 5. Report architecture

Controller → read-only, REPEATABLE_READ service → named-parameter JDBC aggregates.
Summary consolidates bounded management totals and daily transaction buckets;
trip/route tables are paginated. Each domain retains its own grain. Trip/route page
selection narrows expensive aggregation before matrix enumeration. Query count is
constant rather than one query per trip. No new runtime dependency.

## 6. Date / time semantics

Inclusive fromDate/toDate in Asia/Ho_Chi_Minh, maximum 366 dates; half-open UTC
intervals via BusGoTime and JpaJdbcTime. Responses echo dates, timezone, asOf,
dateBasis and filters. Transaction bases: booking.created_at, payment.paid_at,
refund.refunded_at, ticket.created_at, booking.cancelled_at. Travel bases: planned
origin departure. Daily buckets use bound intervals rather than server DATE casts.
Prior-period requests are possible with identical filter contracts; no fabricated
comparison percentages. Boundary tests include exact midnight, microsecond before
midnight, month boundary, exclusive upper bound and differing UTC/Vietnam dates.

## 7. Collections

grossMockCollections sums successful original payment amounts, including later
REFUNDED, by paid_at. mockRefunds sums dedicated refund amounts by refunded_at.
netMockCollections is window gross minus window refunds and can be negative.
Payment/refund counts, method breakdown and zero-filled daily trend are returned.
Multi-seat/multi-ticket/multi-segment tests prove no amount multiplication. An old
payment refunded today keeps old gross while today's net can be negative.

## 8. Bookings

One count per booking-created cohort, current PENDING/CONFIRMED/CANCELLED/COMPLETED,
WEB/PHONE, cohort cancellations/timeouts and current unpaid PENDING reservations.
Cancelled unpaid reservations are excluded from current unpaid count. Counts are
never substituted for passenger or ticket counts.

## 9. Tickets

Issued-by-ticket-created-date includes tickets later VOID. VALID/VOID are current
states of that issuance cohort. No transported-passenger claim is inferred from
issuance. Trip/route rows separately report currently VALID tickets on their
departure cohort.

## 10. Segment load

Expected snapshot seat×segment matrix minus BLOCKED forms sellable capacity.
BOOKED cells are reserved, including unpaid bookings; paid cells require eligible
CONFIRMED/COMPLETED bookings and PAID payment. Booking filters change numerators,
never capacity. HELD is separate. Missing cells or invalid stop/segment topology
set complete=false and suppress both ratios/whole-trip availability. Seat reuse
across non-overlapping journeys, blocked/held cells, released/cancelled bookings,
missing cells/segments and unequal-capacity multiple-trip aggregation are tested.
Summary and route load now exclude CANCELLED trips from every cell aggregate and
from aggregate completeness. All-cancelled cohorts have null/not-applicable ratios.
Individual cancelled trip rows retain diagnostic counts but always have null ratios.

## 11. Routes

Owned routes, including empty rows, with trip/operated counts, bookings, valid
tickets, attributed mock gross/refund/net, summed cell ratios and actual attendance.
Operated = DEPARTED/COMPLETED. Ratio is SUM(numerator)/SUM(denominator) over only
non-cancelled trips; any incomplete non-cancelled contributor suppresses the route
ratio. Tests distinguish 3/9 from averaged 0.25, and exclude cancelled contributors.

## 12. Trips

Planned departure, route, lifecycle status, bus plate, bookings/tickets, attributed
money, attendance, load/completeness, separate whole-trip available seats. Money is
lifetime attributed to selected departing trips, not transaction-date flow. Paging
is tested with two trips and page size 1; counts and returned rows remain distinct.

## 13. Attendance

Eligible resolved ticket denominator contains only BOARDED/NO_SHOW on currently
VALID paid eligible tickets. Boarded/no-show rates use that same denominator and
are null if zero. CHECKED_IN is separate, missing/EXPECTED is Chưa ghi nhận.
Explicit ticketless no-show is a separate booking-item count, outside ticket rates.
Unpaid PENDING items without attendance remain unresolved. Cancelled bookings do
not create travel claims. Integration and real-API fixtures cover every state.

## 14. Cancellations

Cancellation flow is by cancelled_at with CUSTOMER_CANCELLED/OPERATOR_CANCELLED/
PAYMENT_TIMEOUT, refunded/unpaid cancellation counts and attributed refund amount.
Timeouts are never refunds. This flow is distinct from current cancellations of
the booking-created cohort and from independent refund transaction dates.

## 15. Dashboard

Admin today overview reuses summary for new bookings, currently valid issued
tickets, mock collections/refunds, WEB/PHONE, payment-method collection counts,
unpaid cohort and driver/pickup/attendance/inventory warnings. Existing schedule,
highlighted trip and operational shortcuts remain. Staff do not mount financial
summary queries.

## 16. Reports UI

One Báo cáo entry, five sections, shared date presets/range, route/trip/source/
method filters, CSS source/status charts and lightweight SVG collection/refund
chart. Exact daily/method values accompany charts. Compact paginated route/trip
tables scroll locally on mobile. Empty/loading/error/incomplete states are
explicit; design tokens reused; no heavy chart library. Screenshots at 390, 820,
1440 were visually inspected, including mobile overview, desktop trip table and
tablet missing-inventory warning.

## 17. API contracts

GET /api/v1/operator/reports/summary, /trips, /routes. ApiResponse envelopes,
stable records/TypeScript contracts, metadata on every response, 366-day bound,
optional routeId/tripId/source/method, page 0–100000 and size 1–100 for tables.
No arbitrary expressions, operatorId selection or platform bypass. Exact field
contracts are added to api-contract.md.

## 18. Query / multiplication safeguards

Payments do not join booking_items/tickets/inventory before aggregation. Refunds
aggregate independently through payment/booking ownership. Travel-domain aggregates
are one row per trip before joins. Attendance/item/ticket joins use unique keys.
EXISTS never increases grain; DISTINCT(amount) is not used. Matrix left joins
retain absent cells and fixed expected capacity. No JPA lazy per-trip query loop.

## 19. Security / isolation

Integration tests deny all three endpoints to anonymous, staff, customer and
SYSTEM_ADMIN, and prove foreign operator trip/route/money contributions are empty.
Active membership required via existing context; SYSTEM_ADMIN mixed-role bypass
is explicitly rejected in that context and by frontend guards. Frontend tests
prove staff/system admin do not mount report components/queries or get navigation.
Live staff requests receive 403 and direct UI navigation exposes no finances.

## 20. Performance / EXPLAIN

Final populated MySQL fixture smoke profiles (summary + trip + route calls):

| Range | Elapsed |
| --- | --- |
| 1 day | 142 ms |
| 30 days | 83 ms |
| 365 days | 95 ms |

Both broad trip performance and paginated route performance have EXPLAIN plans
for all three ranges in .tools/m18a-verify-final.log. Plans use trip departure/
operator-route, booking trip, payment paid/booking, unique inventory seat-segment,
attendance item and ticket/item keys. Derived-domain grouping uses temporary
tables and optimizer-generated keys; some tiny fixture tables are scanned as
expected. These are small-fixture smoke profiles, not production-scale SLAs.
No profiling evidence warrants materialized result tables.

## 21. Unit tests

mvn test passed with 37 tests, no failures/errors/skips (.tools/m18a-unit.log).
Final full verify also passed all 37 units. Two new tests cover Vietnam conversion,
range validation and null zero-denominator ratios. Foundation context mocks the
new repository without requiring a datasource.

## 22. MySQL integration tests

mvn verify -Pmysql-integration passed 37 units plus 226 integration tests, zero
failures/errors/skips. Ten new M18A cases cover money grain/methods/refund dates,
time boundaries, reuse/paid/unpaid/blocked/held/released/incomplete inventory,
weighted routes/pagination, attendance, cancellation/timeout, security/isolation
and 1/30/365-day profiles/EXPLAIN. Evidence: .tools/m18a-verify-final.log.
MySQL 9.2 on isolated local port 13317, busgo_m18a_verify. Existing application
databases were not reset. Java 17.0.20.1 and Maven 3.9.11.
mvn package -DskipTests passed (.tools/m18a-package-final.log).

## 23. Frontend tests / build

npm test passed 92 tests, zero failures/skips; seven new real-component tests cover
empty/populated reports, simulated financial wording, methods, sources/statuses,
incomplete inventory, absent attendance, presets/loading/shared filters and staff/
system-admin denial. npm run build passed TypeScript/Vite. Node 22.20.0.
Logs: .tools/m18a-frontend-final.log and .tools/m18a-build-final.log.

Warnings retained: MySQL 9.2 newer than Flyway-tested support, Mockito/JVM bootstrap
sharing, existing deprecated API compilation notice, Zod PURE annotation comments,
and existing frontend main chunk above 500 kB (583.98 kB). Negative constraint
tests intentionally log SQL errors; demo tests intentionally warn about skipped
trips/reset being disabled. No warnings suppressed.

## 24. Browser verification

Live headless Edge with real M18A backend/Vite and fresh busgo_m18a_browser fixtures
passed at 390×1000, 820×1000, 1440×1000. Admin reports open, inclusive date/source
filters change results, exact displayed gross/refund/net match API fixture,
WEB=1/PHONE=4 and all three methods are correct, route/trip load and attendance
states match the real APIs, missing inventory suppresses ratios with a warning,
staff cannot read any report, no horizontal document overflow or page errors.

Final fixture: gross 3,250,000; refunds 1,300,000; net 1,950,000 VND simulated;
3 paid ticket allocations/9 segment cells and 5 reserved allocations/15 cells over
102 sellable cells → paid load 8.8235%, reserved load 14.7059%. Attendance: one
boarded, one ticket no-show, one checked-in, one ticketless no-show, one unresolved;
boarding/no-show rates both 50% of two resolved eligible tickets. Deleting one
AVAILABLE cell of the new fixture trip sets complete=false, missingCells=1 and
paidSegmentLoad=null. Seeded/demo inventory is never altered by that check.
Evidence: .tools/m18a-browser-final/results.json, screenshots and browser-final.log.

## 25. Regression verification

All existing backend and frontend suites pass. Existing M16A browser flow passed
all widths: PHONE PAY_ON_BOARD/QR links, conflict guard, collection/ticket, full WEB
search/hold/booking/payment/ticket/My Bookings and staff/link denial.
Existing M16B browser flow passed all widths: employee/crew readiness/overlap,
boarding, paid/ticketless no-show, pickup closure, intermediate collection after
departure and lifecycle completion. Existing M17 browser flow passed all widths:
WEB unpaid/paid cancellation, full mock refund/VOID, PHONE methods/public guard,
live scheduled timeout/release and checked-in attendance cancellation guard.
Evidence: .tools/m18a-m16a-regression/, .tools/m18a-m16b-regression/,
.tools/m18a-m17-regression-final/ and corresponding logs.

Earlier verification iterations corrected accessible select names, cache-aware
browser filter waits, retained test authentication, and the old 14-migration
assertion. An overlapping Maven package/verify invocation interfered with compiled
classes; the successful final suites ran sequentially. An initial browser call
arrived before backend readiness, and an M17 navigation displayed a blank page
while other verification work ran; the stable sequential M17 rerun passed every
API/UI check. Results above refer to completed successful runs.

## 26. Unverified items

Production deployment/migration, MySQL 8.4, production-scale benchmark/large-fleet
query stress, real devices and full keyboard/screen-reader accessibility review
were not performed. Current bus-type edits cannot reconstruct a deleted historical
seat snapshot; expected load uses existing snapshots and flags structural defects.
The live package has the same reporting semantics as final source; the final
repository additionally factors the identical route SQL for EXPLAIN reuse.

## 27. Deferred scope

Optional CSV skipped to keep core metric/security quality focused. XLSX/PDF,
calculated comparison deltas, top-route dashboard ranking/extended trend panels,
M18B directory, profit/accounting/actual revenue/settlement, AI/predictions,
warehouse/materialized results and cross-operator platform BI are deferred.
The reports page includes route performance and daily trends. No commit or push.

## 28. git status --short

Exact final inventory follows; runtime databases, logs, packages and screenshots
under .tools are ignored artifacts.

```text
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
 M docs/api-contract.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/features/auth/access.ts
 M frontend/src/pages/operator/OperatorHomePage.tsx
 M frontend/src/routes/router.tsx
?? backend/src/main/java/com/busgo/reporting/
?? backend/src/main/resources/db/migration/V15__management_report_indexes.sql
?? backend/src/test/java/com/busgo/M18AReportsIT.java
?? backend/src/test/java/com/busgo/ReportFilterTest.java
?? docs/m18a-management-reports.md
?? docs/m18a-verification.md
?? frontend/src/api/reportsApi.ts
?? frontend/src/features/operator/Reports.tsx
?? frontend/src/features/operator/reports.css
?? frontend/src/types/reports.ts
?? frontend/tests/m18a-browser-check.mjs
?? frontend/tests/reports.test.mjs
```

## Cancelled-trip load semantic correction — 2026-10-03

Cancelled trips remain in shared departure cohorts, table rows/pagination and trip
counts/statuses. Summary load filters only its load aggregate; route load applies
the same non-CANCELLED condition to each cell total and MIN(complete), preserving
money/bookings/tickets/attendance joins. Individual CANCELLED trip ratios are null
regardless of inventory completeness, with explicit Không áp dụng UI wording.
Response shapes, service date filters, security and V15 indexes are unchanged.

Three additional MySQL tests cover a paid normal + paid cancelled route (9/12
before cancellation → 3/6 afterward, with the cancelled trip's six paid cells
excluded), summary/route agreement, cancelled row visibility/counts/null ratios,
all-cancelled null load with zero contributing capacity, incomplete cancelled
inventory not poisoning normal load, non-cancelled missing inventory still
suppressing ratios, and identical transaction collections/refunds/cancellation/
booking-created/ticket-issued cohorts before/after trip cancellation. A frontend
test distinguishes no applicable capacity from incomplete contributing inventory.

Correction verification (superseding earlier milestone test counts):

- mvn test: 37 tests, zero failures/errors/skips; .tools/m18a-load-fix-unit.log.
- mvn verify -Pmysql-integration: 37 units + 229 MySQL integration tests, zero
  failures/errors/skips, including all 13 M18A tests;
  .tools/m18a-load-fix-verify.log.
- mvn package -DskipTests: passed; .tools/m18a-load-fix-package.log.
- npm test: 93 tests, zero failures/skips; .tools/m18a-load-fix-frontend.log.
- npm run build: passed TypeScript/Vite; .tools/m18a-load-fix-build.log.
- git diff --check: passed.

All existing money, refund, cancellation, booking and ticket regression suites
remain green. Existing warnings are retained in the logs. No additional migration,
index, commit, push or deployment. Live browser acceptance above is the original
M18A evidence; this semantic correction was verified by MySQL integration and
frontend component tests/build, without rerunning the browser acceptance scripts.
