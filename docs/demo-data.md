# Local demo data — V1.5

The fictional catalogue is for local portfolio use only. `dev,demo` explicitly
activates DemoDataSeeder; `dev` alone creates no demo data. The seeder is excluded
with `prod` or `production`. No fictional users/data occur in Flyway V1–V16.

## Enable

Follow the [Java/database/environment setup](../README.md), then from backend:

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=dev,demo'
```

M15 replaced M10.5's destructive reset and mutable upserts. The old
BUSGO_DEMO_RESET_UNBOOKED_TRIPS flag is compatibility-only: true logs a warning,
and performs no deletion. Do not delete a database volume to reseed.

## Create-only identity and preservation

Existing operators, users, memberships, locations, routes/stops, bus types/templates,
buses, associations and fares are never updated. Reused trips/inventory are never
updated; bookings, holds, payments and tickets are never removed.

Reserved catalogue reuse requires matching expected identifying/structural fields,
not only a name/plate/code. Duplicate shared natural keys, foreign bus ownership,
incompatible geometry/stops or unrelated reserved accounts fail clearly. There is
no provenance claim for old records: even an exact structural match is read-only.
Do not silently repair a collision. Start with `dev` alone to inspect it, or use
a separate empty demo database. All new fixture writes share one transaction; a
collision or unexpected failure rolls them back.

Mutable statuses, existing fare amounts and password hashes are preserved. Changed
identity/structure can require manual investigation rather than automatic reuse.
An inactive operator/account/membership remains inactive. An unavailable bus,
inactive catalogue or unusable fare prevents new trips in its slots. An existing
trip with the exact route/bus/departure is reused as-is, even after lifecycle changes.

## Local accounts

**LOCAL DEMO ONLY — never use these credentials in production.** Passwords below
are assigned only when the reserved account is first created. If changed later,
the documented initial password no longer works; seeding never resets it.

| Role | Email | Initial local password |
| --- | --- | --- |
| OPERATOR_ADMIN | operator.admin@anphu-demo.example | DemoOperator!2026 |
| OPERATOR_STAFF | operator.staff@anphu-demo.example | DemoStaff!2026 |
| OPERATOR_ADMIN (secondary) | operator.admin@minhthanh-demo.example | DemoSecondary!2026 |

The first two belong to An Phú Express (`DEMO-ANPHU`). The secondary admin belongs
only to Minh Thành (`DEMO-MINHTHANH`), allowing the existing activation guard to
approve reactivation. Each must retain its exact sole role, dedicated identity and
sole intended membership/staff code. Unexpected CUSTOMER,
SYSTEM_ADMIN, another operator role/membership, missing role/membership or changed
identity fails with a collision. Membership/account suspension is preserved rather
than repaired. On a fresh database each has one ACTIVE membership.

CUSTOMER: register once through `/register`, using a dedicated address such as
`customer@busgo-demo.example` and a locally chosen password. Preserve that account.
No customer account, booking or ticket is manufactured by seeding.

SYSTEM_ADMIN: use the existing environment bootstrap once and disable it afterward;
keep its credentials outside Git. Demo profile never grants SYSTEM_ADMIN.

## Catalogue and deterministic schedule

Three fictional operators: An Phú Express, Minh Thành Limousine and Tây Nguyên
Travel. Six locations cover HCM, Đà Lạt, Buôn Ma Thuột, Nha Trang, Đà Nẵng and Huế.
Nine directional/multi-stop routes, 13 operator-route associations and all required
forward fare pairs remain. Layouts: limousine 22 (A01–A22), two-floor sleeper 34
(L01–L17/U01–U17), seated 40 (S01–S40), with physical row/column gaps.

Every run proposes the same daily schedule for tomorrow through tomorrow+2 in
Asia/Ho_Chi_Minh. Seven buses avoid overlaps across adjacent dates:

| Local departure | Bus | Journey | Duration |
| --- | --- | --- | --- |
| 06:00 | 51B-770.03 | HCM → Nha Trang → Đà Nẵng → Huế | 21 h |
| 06:30 | 51B-770.01 | HCM → Đà Lạt | 7 h |
| 08:00 | 50F-880.01 | HCM → Đà Lạt | 7 h |
| 09:30 | 47B-660.01 | HCM → Đà Lạt | 7 h |
| 13:00 | 51B-770.02 | HCM → Đà Lạt | 7 h |
| 18:30 | 50F-880.02 | HCM → Đà Lạt | 7 h |
| 22:00 | 47B-660.02 | HCM → Đà Lạt | 7 h |

Clean first seed: 21 trips. Same-date restart: 0 new/21 reused. Next-day restart:
7 new/14 reused. Past trips remain. The 21-hour coastal service has a three-hour
gap before the next day's departure on its dedicated bus. This is a software demo
timetable, not a real transport itinerary or fleet repositioning model.

Each missing trip locks its bus and preflights production schedule overlap. Existing
manual/legacy overlapping trips cause a deterministic skip, never deletion or
rescheduling. Production TripAggregateCreator validation remains intact. Log output
reports created/reused/skipped totals and the skipped bus/route/UTC departure.
Old demo schedules can therefore reduce the result count until their dates pass.

Initial BLOCKED seats are applied only to newly created trip aggregates. No fake
HELD/BOOKED state is generated. Coastal L01 is blocked only on the first segment;
L02 only on the second. Real bookings/holds use normal APIs and pricing.

## Readiness check

Use the logged date to search **Bến xe TP. Hồ Chí Minh → Bến xe Đà Lạt**.
A clean date has six results, 220,000–300,000 VND fares and three layouts.
Additional manual trips may increase results; skips/suspension/lifecycle/commerce
may reduce them. Seeding completion is not a guarantee of demo availability.

Verify an active An Phú operator/admin/staff, AVAILABLE bus, active fare, future
SCHEDULED trip, complete inventory and at least two genuinely available seats.
If local state does not meet this, use existing management flows deliberately or
prepare a separate empty demo database; do not repair through seeding.

The operator dashboard defaults to today. Choose the logged future date in the
trip list to find the customer demo trip. See [final demo](final-demo.md).

## V1.5 rehearsal fixtures

The create-only seed remains deliberately non-transactional in its business story:
it does not manufacture paid/cancelled bookings, refunds or attendance. Create those
through the real UI before presenting, preserving their actual history. New An Phú
trips receive eligible deterministic crew assignments; existing assignments are not
restored or overwritten. Maintenance records are created/start/completed through
normal admin commands on a spare bus. Follow the preparation and backup paths in
[v1.5-demo-script.md](v1.5-demo-script.md); no SQL reset is required.
