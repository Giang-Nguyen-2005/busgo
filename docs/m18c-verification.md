# M18C verification — 2026-10-04

This file records the required final report and local verification evidence. No
commit or push is authorized. Verification uses an isolated local MySQL 9.2 server
at 127.0.0.1:13319, schemas busgo_m18c_final and busgo_m18c_browser; existing application
databases are untouched. JDK 17 is the existing ignored .tools/jdk17 runtime.

## 1. Source audit

A–H findings are in [M18C design](m18c-fleet-maintenance.md): AVAILABLE/MAINTENANCE/
INACTIVE, required immutable trip.bus, no reassignment/history, AVAILABLE+type
creation/readiness guards, M16B crew composition, reliable departure/arrival windows.

## 2. Files created

Backend fleet: FleetMaintenanceGuard.java, MaintenanceController.java,
MaintenanceDtos.java, MaintenanceRules.java, MaintenanceService.java.
Migration: V16__fleet_maintenance.sql.
Tests: MaintenanceRulesTest.java, M18CFleetMaintenanceIT.java, M18CFleetConcurrencyIT.java.
Frontend: api/fleetApi.ts, types/fleet.ts, features/operator/FleetMaintenance.tsx,
features/operator/fleet.ts, features/operator/fleet.css, tests/fleet.test.mjs.
Documentation: m18c-fleet-maintenance.md, this verification file.

## 3. Files modified

Backend: BusService.java, FleetDtos.java, OperationsService.java, TripService.java,
TripAggregateCreator.java. Tests: FoundationTest.java (new service mocks for the
database-free context), CoreDatabaseIT.java (V16 count/table whitelist).
Frontend: api/errors.ts, features/auth/access.ts, pages/operator/OperatorBusesPages.tsx,
pages/operator/OperatorHomePage.tsx, routes/router.tsx, types/operator.ts.
Docs: api-contract.md, development-plan.md, product-roadmap.md.

## 4. Migration/indexes

Additive V16 only, maintenance and append-only bus status history. Four indexes:
maintenance(bus_id,status,scheduled_start), maintenance(status,scheduled_start),
maintenance(bus_id,status,completed_at,id), history(bus_id,changed_at,id).
Existing V4 bus schedule index reused; V1–V15 unchanged.

## 5–7. BusStatus, model and lifecycle

Actual statuses preserved. Ownership through bus; ten bounded maintenance types;
required bounded schedule, bounded notes, audit times/actors, optional manual odometer
and next due metadata. SCHEDULED -> IN_PROGRESS/CANCELLED; IN_PROGRESS -> COMPLETED.
No repeat completion, reopening, arbitrary PATCH or DELETE.

## 8–11. Time, trips, assignment and BOARDING

UTC/JpaJdbcTime storage, Vietnam UI/business dates. Strict half-open origin-departure/
estimated-arrival overlap; back-to-back permitted. Maintenance creation conflicts
with nonterminal assigned trips; active maintenance blocks all assignment. Existing
ownership/status/type/trip guards preserved. M16B crew/licence/conflict logic remains
and receives the additional maintenance warning/check.

## 12–14. Restoration and history decisions

Restore AVAILABLE only if proven prior AVAILABLE and current MAINTENANCE. Original
or intentional INACTIVE stays INACTIVE; original MAINTENANCE stays unavailable.
No assignment history because assignment is immutable. Status history is justified
by automatic restoration and existing mutable status; actual transitions only,
append-only, no fake no-op history/backfill, latest 100 visible.

## 15–16. Readiness and due model

AVAILABLE + active type + no active maintenance + no planned maintenance now. Future
plan outside now stays ready. Latest completed due metadata per type; unrelated work
cannot erase due dates. Explicit date <Vietnam today overdue, today..today+7 due soon;
scheduled work within next seven days upcoming. Unknown mileage never implies overdue.

## 17–20. Directory/workspace/UI/dashboard

Desktop compact fleet rows, phone cards; identity/status/readiness/maintenance/trip/
warning visibility. Bus tabs overview/trips/maintenance/history; paginated trips and
maintenance, filters, bounded scheduling/completion/cancellation forms, explicit
confirmations and safe conflict links. Standalone maintenance page. One dashboard
fleet warning section with physical status counts, due/overdue and upcoming unready
trips; permission-aware mounting.

## 21–24. Reports/API/access/isolation

M18A unchanged; fleet summary is separate from filtered financial report cohorts.
API contract documents all explicit commands/read routes, DTOs, pagination and errors.
Admin-only fleet as audited; staff/customer/public/system admin denied. Active trusted
membership only; no operatorId selector. Foreign bus/record/trip/history reads/mutations
hide ownership with 404. V1 global plate uniqueness preserved.

## 25–26. Concurrency/queries

READ_COMMITTED writes, ordered existing trip locks -> bus -> maintenance. New trips
discovered after bus acquisition cause FLEET_PLAN_CHANGED rollback/retry, never a
reverse trip lock. Trip creation locks bus then inserts a new row. Status writes
share this guard; M16B keeps trip -> bus -> employees. Latest completions are limited
per type, planning uses nonterminal trips, directory page <=100. Dashboard still
performs per-bus reads; production batching/EXPLAIN is explicitly deferred.

## 27. Unit tests

MaintenanceRulesTest covers half-open boundaries, back-to-back, all enum transition
pairs, deterministic due dates, bounded validation and readiness composition.
Latest unit run results will be recorded below; logs remain in ignored .tools.

## 28. MySQL integration tests

M18CFleetMaintenanceIT covers create/start/complete/cancel, terminal rejection,
complete-once timestamp, history, overlap boundaries, guarded trip creation,
IN_PROGRESS/BOARDING guards, active trip start rejection, inactive restoration,
explicit due metadata, foreign isolation, HTTP access, invalid values, scheduled
readiness composing with crew, manual maintenance/no-op history and per-type due
supersession. M18CFleetConcurrencyIT covers maintenance-versus-trip creation,
two starts, and maintenance-versus-BOARDING with one consistent winner/no deadlock.

## 29–31. Frontend/browser/regression

Frontend tests cover responsive rows/cards, readiness, upcoming/overdue messages,
bounded create form/Vietnam wall time, lifecycle command visibility, retained
completion/cancellation history, readable conflicts, unauthorized query denial and
responsive CSS. Existing suites are run in full. Browser acceptance uses real
backend and headless Edge, including a non-Vietnam browser timezone, at 390/820/1440.
Per-width screenshots/results are retained under .tools/m18c-browser.

Final command results and observed warnings will be recorded below.

## 32. Unverified items

Production MySQL 8 deployment, large-fleet EXPLAIN/performance, physical mobile devices,
screen-reader/manual accessibility and production migration rollout are not claimed.
Headless browser checks establish viewport behavior, not real-device acceptance.

## 33. Deferred scope

Parts/inventory/warehouse, fuel, payroll, GPS/telematics/IoT, insurance, depreciation/
accounting, predictive AI, procurement, synchronized odometer, financial maintenance,
exports, staff fleet reads and trip reassignment. No expansion beyond operational fleet.

## 34. Repository state

All changes remain local and uncommitted. Final git status --short and command
evidence are appended after verification completes.

## Final command evidence

| Check | Final result | Local evidence |
| --- | --- | --- |
| mvn test | PASS: 44 tests, zero failures/errors/skips | .tools/m18c-unit.log |
| mvn verify -Pmysql-integration | PASS: 44 units + 253 MySQL integration tests, zero failures/errors/skips | .tools/m18c-verify-final.log |
| mvn package -DskipTests | PASS | .tools/m18c-package.log |
| npm test | PASS: 109 tests, zero failures/skips | .tools/m18c-frontend.log |
| npm run build | PASS: TypeScript + Vite production build | .tools/m18c-build.log |
| git diff --check | PASS | final repository check |

New backend coverage: five unit cases, fifteen lifecycle/readiness/security MySQL
cases and three concurrency races. Existing M16A/M16B/M17/M18A/M18B and earlier
suites pass in the full 253-case integration run. Final frontend has eight new fleet
cases plus all existing regression cases. No skipped integration tests.

Final browser acceptance passed with the verified backend jar (a separate ignored
.tools/m18c-live.jar copy avoids Windows locks on Maven output), isolated demo
schema, and real API calls. At each of 390, 820 and 1440: fleet list/overview,
future plan remains ready, start makes MAINTENANCE/unready, assignment and BOARDING
reject, complete retains record/due data and restores AVAILABLE safely, overlap
returns a readable owned-trip conflict and leaves trip SCHEDULED, cancel retains
reason/history, status history, standalone bus filter, dashboard and foreign
operator reads returning 404. Staff fleet API is 403 and UI is denied. Browser
uses America/New_York while inputs/displays remain Vietnam time. No document-level
horizontal overflow or JavaScript exceptions on tested screens.

Twelve screenshots (overview, in-progress, maintenance history, directory at each
width), results.json and the local runner are under .tools/m18c-browser and
.tools/m18c-browser.cjs. Screenshots were visually inspected; desktop rows were
compacted to remove duplicate readiness badges, then browser acceptance reran.
Final results: .tools/m18c-browser.log and .tools/m18c-browser/results.json.

Warnings were retained, not disabled: existing deprecated test API notice; Flyway
warns MySQL 9.2 exceeds its tested version range; Zod dependency pure-annotation
warnings and the existing >500 kB frontend chunk warning. Negative regression
fixtures also intentionally log unmapped routes, corrupt-inventory rejection,
duplicate constraint checks and preserved/skipped demo trips. These did not fail
final checks. Production MySQL 8 and large-fleet profiling remain unverified.

Initial checks exposed the new database-free context dependencies and the old
migration count/table whitelist; both were updated. Intermediate build overlap
and Windows jar-lock issues were resolved with sequential Maven verification and
a separate live jar copy. Final evidence above supersedes those intermediate runs.

## Final git status --short

33 changed paths: 17 created and 16 modified. Branch feature/management-upgrade.
No commit, push, migration edits to V1–V15, or existing application database changes.

```text
 M backend/src/main/java/com/busgo/fleet/BusService.java
 M backend/src/main/java/com/busgo/fleet/FleetDtos.java
 M backend/src/main/java/com/busgo/operations/OperationsService.java
 M backend/src/main/java/com/busgo/trip/TripAggregateCreator.java
 M backend/src/main/java/com/busgo/trip/TripService.java
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
 M docs/api-contract.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/api/errors.ts
 M frontend/src/features/auth/access.ts
 M frontend/src/pages/operator/OperatorBusesPages.tsx
 M frontend/src/pages/operator/OperatorHomePage.tsx
 M frontend/src/routes/router.tsx
 M frontend/src/types/operator.ts
?? backend/src/main/java/com/busgo/fleet/FleetMaintenanceGuard.java
?? backend/src/main/java/com/busgo/fleet/MaintenanceController.java
?? backend/src/main/java/com/busgo/fleet/MaintenanceDtos.java
?? backend/src/main/java/com/busgo/fleet/MaintenanceRules.java
?? backend/src/main/java/com/busgo/fleet/MaintenanceService.java
?? backend/src/main/resources/db/migration/V16__fleet_maintenance.sql
?? backend/src/test/java/com/busgo/M18CFleetConcurrencyIT.java
?? backend/src/test/java/com/busgo/M18CFleetMaintenanceIT.java
?? backend/src/test/java/com/busgo/MaintenanceRulesTest.java
?? docs/m18c-fleet-maintenance.md
?? docs/m18c-verification.md
?? frontend/src/api/fleetApi.ts
?? frontend/src/features/operator/FleetMaintenance.tsx
?? frontend/src/features/operator/fleet.css
?? frontend/src/features/operator/fleet.ts
?? frontend/src/types/fleet.ts
?? frontend/tests/fleet.test.mjs
```
Temporary verification backend/Vite/MySQL servers were stopped after successful checks; isolated data and artifacts are preserved.
