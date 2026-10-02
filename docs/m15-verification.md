# M15 P0 — final hardening verification

Executed on 2026-10-01 on `feature/final-hardening`. No commit or push was made.
This records M15 evidence; earlier milestone verification documents remain intact.

## Environment and isolation

- Java 17.0.20.1, Maven 3.9.11, Node 22.20.0, npm 10.9.3 on Windows.
  The default PATH originally selected Java 8; verification explicitly selected
  the ignored local Java 17 installation for both Maven and application startup.
- Docker was unavailable. A separate local MySQL 9.2 instance on
  `127.0.0.1:13315` used dedicated `busgo_m15_it` and `busgo_m15_demo` schemas.
  Existing development databases and the existing MySQL service were untouched.
  Flyway warns that this server is newer than its tested MySQL version.
- Random database/JWT/customer/system-admin credentials, logs and diagnostic SQL
  snapshots were kept in ignored `.tools/`. No actual secret is in this document.
  Test services were stopped after verification; their ignored data was retained.

## Delivered P0 changes

- Demo seeding is transactional and create-only. The legacy reset flag only warns.
  Exact structural identities, sole account role and sole intended membership are
  required for reuse; ambiguous reserved keys fail without repair. Passwords,
  mutable statuses/prices, existing trips, inventory, holds and bookings are preserved.
  A failed late collision rolls back the whole seed attempt.
- Seven fixed daily departures over tomorrow and the following two Vietnam
  business dates use seven buses. The 21-hour coastal service has a dedicated bus.
  Same-date starts reuse exact trips; consecutive dates add only a new final day.
  Missing trips use the production bus lock and schedule/inventory/catalogue
  preflight. Conflicting or unusable slots are skipped with a warning.
  Production trip validation was not relaxed. BLOCKED inventory is initialized
  only for newly created trips; no booking/hold is manufactured by the seeder.
- An Phú admin/staff and Minh Thành secondary admin are dedicated local fixtures.
  The secondary admin is necessary because normal operator reactivation requires
  a login-capable administrator. Existing incompatible identities fail clearly;
  existing inactive accounts/memberships are preserved. CUSTOMER registers once;
  SYSTEM_ADMIN retains the environment-driven one-time bootstrap.
- Live rehearsal found customer and staff departures seven hours apart on a
  Vietnam-time JVM. JDBC readers/filter parameters now match Hibernate's existing
  UTC Calendar binding for JPA-written timestamps. Direct-JDBC hold expiry retains
  its existing convention. No stored timestamp or migration was rewritten.
- Vietnamese management errors cover BUS_SCHEDULE_CONFLICT/BUS_NOT_AVAILABLE;
  the customer-role guard uses Vietnamese copy. Unused FoundationPage/healthApi
  were removed after checking references. No broad CSS or product change was made.
- README, demo-data, API contract and development plan now describe delivered V1,
  V2-ready infrastructure, deferred work, setup, profile/account semantics and
  date filtering. `final-demo.md` provides the rehearsal and 6–8 minute script.

## Automated release checks

| Check | Result |
| --- | --- |
| `mvn test` with Java 17 | PASS — 28 unit tests |
| Final `mvn verify -Pmysql-integration` with Java 17 | PASS — 28 unit + 163 integration tests; zero failures/errors/skips |
| `mvn package -DskipTests` with Java 17 | PASS — executable Spring Boot jar packaged |
| Frontend `npm test` | PASS — 65 tests |
| Frontend `npm run build` | PASS — TypeScript/Vite production build |
| `git diff --check` | PASS |
| Flyway/schema validation | PASS — V1–V10 applied to isolated DB; migration count remains 10; old migrations unchanged |

The frontend build retains existing dependency purity and large-chunk warnings;
bundle optimization is deferred. Retained synthetic `frontend/tests/fixtures/`
and `dispatch-preview.html` support regression/visual verification, are not
production entry points, and their fixture markers were absent from `dist`.

Integration coverage includes authentication/refresh concurrency, management,
trip creation/search, seat availability/holds/concurrency, booking/concurrency,
payment/tickets/concurrency, operator operations, system admin/staff isolation,
inactive operator suspension and M14A contracts.

New DB-backed coverage:

- `DemoDataSeederIT`: 24 cases, with real production trip creation and MySQL.
  Covers first seed, two same-date reruns, five consecutive seed dates, overlapping
  and non-overlapping manual trips, PENDING and paid bookings, active holds,
  inventory preservation, changed trip/operator/bus/fare/account/membership state,
  changed passwords for all three fixtures, wrong/extra privileged roles, foreign
  membership/identity collisions and rollback of a late collision.
  All-table snapshots compare existing rows; these tests require initially absent
  reserved fixture keys and should run against a dedicated integration schema.
- `JpaJdbcTimeIT`: 3 cases for UTC, Asia/Ho_Chi_Minh and America/New_York JVM
  defaults. Checks JPA/JDBC departure/arrival, booking creation/update/date filters,
  passenger times, admin/staff timestamps, payment timestamps and unchanged
  direct-JDBC hold expiry. Each case restores the JVM timezone.

## Executed startup and API smoke checks

| Check | Evidence/result |
| --- | --- |
| `dev` without demo/bootstrap | PASS — health UP, no seeded locations in fresh dedicated demo schema |
| First packaged `dev,demo` | PASS — 21 trips created, 0 reused/skipped; complete initial inventory |
| Same-date packaged restart | PASS — 0 created, 21 reused, 0 skipped |
| Legacy reset flag set to true | PASS — warning only; no deletion/reset |
| Consecutive business dates | PASS — automated fixed/mutable-clock tests: 7 new + 14 reused per next day; no schedule overlaps |
| Manual/user data survives restart | PASS — sorted full-data dump hashes before/after restart identical, including manually created trip and paid two-seat booking/tickets |
| Final packaged timestamp fix/restart | PASS — full-data dump unchanged again; customer ticket, operator booking/trip and staff manifest all show `2026-10-01T23:30:00Z` for the selected departure |
| Customer commerce | PASS — registration, search, two-seat hold, booking, mock confirmation, repeat confirmation with same payment ID, two electronic tickets |
| Operator/staff views | PASS — complete occupancy, two manifest rows, owned booking/contact; staff mutation denied with 403 |
| Role/operator boundaries | PASS — customer and SYSTEM_ADMIN denied operator context (403); operator admin denied system-admin API (403); foreign operator trip denied (404) |
| Secondary operator management | PASS — directory/staff read, Minh Thành deactivate, search exclusion, reactivate through normal guard |
| Four required demo-role logins | PASS — customer, An Phú admin/staff, locally bootstrapped SYSTEM_ADMIN; secondary admin also verified |
| Frontend HTTP/proxy | PASS — `/`, `/operator/trips`, `/admin/operators` served SPA HTML on 5173; proxied backend health UP |

The customer and SYSTEM_ADMIN accounts above were prepared only in the isolated
verification database; they are not additional automatic demo fixtures. A failed
early secondary-reactivation rehearsal identified its missing admin fixture;
the create-only secondary admin was added and the scenario passed afterward.

## Security and remaining acceptance

Tracked environment examples contain empty DB password placeholders; backend
configuration requires DB_PASSWORD/JWT_SECRET from the environment. Targeted
source/configuration checks found no actual JWT/DB credential or private-key/API
secret. Generated outputs, local environments and runtime secret files are
ignored. Fixed fixture passwords are explicitly LOCAL DEMO ONLY, initial values.
There is no HTTP privileged bootstrap endpoint. Existing role/tenant guards and
JWT validation remain enabled; SYSTEM_ADMIN is not seeded by `demo`.

Unverified: Docker Compose/MySQL 8.4 execution, rendered browser/mobile interaction
for this M15 pass, a production deployment and startup against an arbitrary
existing user database. HTTP SPA/proxy checks do not prove rendered browser flows.
Rehearse `final-demo.md` in independent browser profiles before recording, confirm
two available seats and all account/membership states, and keep the JVM timezone
consistent when reusing a legacy database. Reserved-key mismatches intentionally
fail; unusable/manual occupied slots intentionally skip instead of repairing data.

P1: final rendered desktop/mobile rehearsal and remaining small terminology/error
state inconsistencies found during that rehearsal. P2: broad CSS/dead-code cleanup,
bundle optimization, refunds/cancellation redesign, analytics, real payments and
new business/operator workflows. These are outside M15 P0.
