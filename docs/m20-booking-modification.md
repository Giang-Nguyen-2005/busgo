# M20 — Booking modification

## Audit before implementation

Baseline: clean `feature/v2-booking-modification`; latest migration V16. V1–V16 and the v1.5.0 tag are immutable.

* Booking identity and contact/source snapshots live in `bookings`; items retain passenger, seat and recorded unit-price snapshots. Pending and confirmed reservations own BOOKED segment rows.
* Inventory is one row per trip seat/segment. Existing hold repositories lock complete segment sets and use an opaque token, user and deadline. Expired holds can be reclaimed. Modification tokens must stay internal so ordinary booking creation cannot consume them.
* Journey resolution validates pickup/dropoff location ordering, permissions, scheduled trip, active operator/route and exact current fare. Old amounts must come from booking/item snapshots. Seat changes retain recorded fares; trip changes use target fares.
* Payment has a generated unique PAID booking key, and repository methods return one payment. Refunds currently enforce exactly one full refund via a composite FK. These constraints require additive migration before adjustment collections and partial refunds can work. Successful principal payments remain immutable in amount/time; purpose distinguishes principal from adjustments.
* Tickets currently have a unique booking-item key. Historical replacement requires retaining VOID tickets and explicitly identifying current tickets. Current-ticket joins throughout booking, occupancy, customer and operations reads must avoid duplicate passengers.
* Attendance is unique per booking item, with nullable ticket for unpaid NO_SHOW. Only EXPECTED attendance may follow a replaced ticket; CHECKED_IN/BOARDED/NO_SHOW forbid modification.
* Commerce locks trip, operator, booking, payment, ticket/attendance, inventory. Modification extends the first step to all involved trip IDs in ascending order. A refreshed booking must still belong to the originally locked source trip; otherwise reject/retry rather than acquire another trip out of order.
* Cancellation supports a six-hour customer cutoff, full-booking inventory release, mock refund, VOID tickets and immutable status history. It must refund remaining balances across payments after modifications and must still validate complete source inventory.
* PHONE QR links store a token hash on the booking; reissue revokes old links. Successful modification clears the old hash. Existing issue-link operation creates a replacement with current booking context. Pending deadlines must not be extended by modification; PAY_ON_BOARD keeps no deadline.
* Customer ownership is account + WEB. Operator context requires one active membership, denies SYSTEM_ADMIN, and restricts mutations to OPERATOR_ADMIN. Foreign IDs remain concealed as 404.
* Frontend has shared booking detail, cancellation, seat-map, money/date/status components and lazy routes. Add a full-page shared workspace and backend-driven eligibility; preserve the existing visual system.
* Reports already aggregate payment/refund events; adjustment rows in those existing tables preserve aggregate accounting. Seat/ticket joins need current-ticket filtering. Alternative trips, history and expiry must be bounded; inventory reads must be batched.

## Domain contract

Quote is transient. Create revalidates and takes a ten-minute target hold. HELD/AWAITING_PAYMENT are the only active states; a generated unique booking key enforces one active attempt. Confirm simulates required cash collection/refund in the switch transaction. COMPLETED/CANCELLED/EXPIRED are terminal. Source remains valid until commit. Failure rolls back the entire transaction, leaving the attempt retryable until expiry.

Fare delta = new recorded total minus old recorded total. Net collected = successful collections minus refunds. Paid collection/refund is the difference from net collected; unpaid bookings produce no adjustment and owe the full new total, including PAY_ON_BOARD. No real gateway or settlement is introduced.

Implementation and final verification evidence are recorded below.

## Implemented schema

* V17__booking_modifications.sql: modification header and item snapshots; one generated unique active-booking key; payment purpose/modification linkage; partial refund linkage; current-ticket uniqueness via replaced=false. V1–V16 are unchanged.
* V18__validate_refund_balances.sql: insert-time refund snapshot/balance guard. A shared current read avoids stale repeatable-read payment snapshots and supports INSERT…SELECT. Invalid amount, unsuccessful payment, mismatched paid timestamp, foreign modification and over-refund are rejected. Services serialize commerce with payment locks. No historical successful amount or paid timestamp is overwritten.
* Payment cardinality is now one-to-many per booking. The database allows at most one PAID principal BOOKING payment and at most one BOOKING_MODIFICATION collection per modification. Existing principal lookup methods explicitly filter purpose=BOOKING as well as status; they never select an arbitrary adjustment from multiple successful rows. Aggregate accounting includes every payment/refund event. Historical failed/pending rows are not excluded by a blanket one-payment-per-booking constraint.
* Refunds may contain several partial modification refunds for a payment and one final cancellation refund. Cancellation refunds each remaining payment balance, then marks successful payment rows REFUNDED, retaining amounts and paid timestamps.
* Tickets retain their old rows. replaced=true implies VOID; exactly one non-replaced row per booking item. Current-ticket repository and passenger/occupancy/customer/report joins filter replaced=false. Cancellation and customer ticket reads use this current set, including current VOID tickets after cancellation; recovery/audit retains historical VOID rows.

## Rules, lifecycle and atomicity

Customer mutation requires an owned WEB booking, CUSTOMER role, source SCHEDULED, active operator, open pickup, at least six hours before selected pickup, no terminal attendance, and an unexpired payment deadline where applicable. Operator Admin may assist owned PHONE/WEB bookings without the customer cutoff. Staff sees history and an explicit admin-required reason. SYSTEM_ADMIN is rejected even when combined with an operator role; foreign bookings/trips/modifications are concealed.

Seat change accepts a nonempty subset of items and retains their recorded unit prices. Unselected items and tickets are untouched. Target seats must be new available seats; no-op selection and swaps into seats already owned by this booking are rejected. Trip change requires all items and the same operator, pickup/dropoff locations in valid order, active exact target fare and a future SCHEDULED target. Booking ID, code, account, passenger and contact snapshots remain unchanged.

Quote writes no modification or hold. Create revalidates the quote, acquires every required target segment, snapshots the actor and mapping, and records HELD or AWAITING_PAYMENT. Target hold token is internal and never returned as an ordinary booking token. Confirmation explicitly simulates any collection/refund and commits inventory, item/trip/amount changes, affected ticket replacement and COMPLETED history in one transaction. Existing tickets are replaced only if present; unpaid bookings do not acquire tickets. EXPECTED attendance follows the replacement ticket; terminal attendance is never reset.

Cancelled/expired attempts release only their target token. Failures roll back source and target changes and cash/ticket artifacts; the attempt remains retryable until expiry or explicit cancellation. FAILED is reserved in the schema, not an automatic nontransactional error outcome. Confirmation of COMPLETED is idempotent. Expired confirmation returns EXPIRED without switching. Scheduled expiry processes at most 100 attempts per minute, each through a separate service transaction, and is idempotent.

The ten-minute TTL is configurable with busgo.booking.modification-hold-duration. Existing hold cleanup may reclaim expired target segment rows before the modification job updates the attempt; expired confirmation still cannot switch. Pending booking deadlines are retained or shortened to target pickup, never extended. PAY_ON_BOARD retains a null deadline.

## Money and payment links

Old total is the recorded booking total, never today's source fare. New total uses recorded fare for current seat-only changes and the exact target fare for trip changes. Fare delta and cash difference are separate fields. Paid quotes use net collected (all successful collections minus refunds); unpaid quotes create neither additional collection nor refund, and show the full new amount due. All amounts use BigDecimal and DECIMAL(12,2).

A successful switch revokes the old QR-link hash, including same-fare journey changes. Pending PHONE QR bookings use the existing operator issue-link operation to obtain a new link with current amount/journey. Abandoned or expired attempts do not alter the original link. No real payment gateway, transfer, webhook or settlement is present.

## Locks and query scope

Mutation locks all involved trip IDs in ascending order, then operator, booking, modification/active-attempt rows, payment rows ordered by ID, current tickets, attendance/pickup state, and segment inventory. Eligibility uses current locking reads after serialization rather than an earlier repeatable-read snapshot. A booking that moved to an unlocked trip is rejected for retry. Existing commerce operations serialize on the source trip before booking/payment locks; cancellation now rejects a changed source-trip snapshot as well.

Required source allocations and target segment sets are fetched in batches; no per-seat inventory SELECT loop. Per-item writes/ticket creation are bounded by booking size. Alternative trips are limited to 50 in the next 60 days; history is limited to the latest 50 attempts; no history queries are added to booking lists. History reads on the detail/workspace use bounded per-attempt snapshot reads (no speculative cache). Ordinary inventory hold cleanup remains the existing architecture.

## API and UI

Shared ModificationService backs customer /api/v1/bookings/{id} and operator /api/v1/operator/bookings/{id} namespaces:

| Method | Suffix | Result |
|---|---|---|
| GET | /modification-eligibility | authoritative seat/trip rule, reason/message, current item IDs, booking context |
| GET | /alternative-trips | bounded compatible owned-operator choices |
| GET | /modification-seat-availability?tripId=… | existing journey seat map |
| POST | /modification-quotes | transient authoritative quote |
| POST | /modifications | held attempt and snapshotted quote |
| GET | /modifications | latest 50 attempts |
| GET | /modifications/{mid} | owned attempt |
| POST | /modifications/{mid}/confirm | simulated cash execution and atomic switch |
| POST | /modifications/{mid}/cancel | terminal cancellation and target release |

Request: type SEAT_CHANGE/TRIP_CHANGE, targetTripId, items [{bookingItemId,targetSeatId}]. Quotes include current/new totals, fareDelta, alreadyCollected, collectionRequired, refundRequired, newAmountDue, source/target departures, journey and item mapping. Hold tokens and auth/contact/payment secrets are excluded from public contexts.

Customer and operator booking details link to a full-page shared modification workspace. It offers source-item selection, existing seat map, explicit unchanged mappings, server-priced review, ten-minute hold and confirmation. Completed history displays immutable mappings, actor category and cash semantics. Operator history also shows abandoned attempts and actor name. Staff receives no mutation controls. Existing visual foundation and money/date helpers are reused.

## Limits and deferred work

No booking split, passenger edit, pickup/dropoff/operator change, partial cancellation, promotion, dynamic pricing, notification, review, GPS, commission or animation system. No-op and intra-booking seat swaps are intentionally rejected. Alternatives cover the next 60 days and history the latest 50 attempts; cursor pagination is deferred if product demand exceeds these bounds. QR replacement is obtained through the existing issue-link action after success. M21 product scope is unchanged; M22 replaces the simulated cash execution/provider boundary and adds real gateway/webhook/settlement behavior separately.

## Final verification — 2026-10-05

Verification continued from the existing working tree. No implementation restart, commit or push was performed. The final change during release verification was test-fixture cleanup: M20 concurrency fixtures now remove their empty trip/inventory aggregates so subsequent demo-seeder assertions are not contaminated by a previous run. No architectural redesign was needed.

| Command/check | Final result |
|---|---|
| `mvn test` | PASS — 50 tests, 0 failures/errors/skips |
| `mvn verify -Pmysql-integration` | PASS — 50 unit tests and 286 integration tests, 0 failures/errors/skips |
| M20 integration subset within the full suite | PASS — 28 functional cases + 5 concurrency/atomicity cases = 33 |
| `mvn package -DskipTests` | PASS — executable Spring Boot JAR |
| `npm test` | PASS — 129 tests, 0 failures/skips |
| `npm run build` | PASS — TypeScript check and Vite production build |
| Fresh migrations | PASS — empty `busgo_m20_release`, V1 through V18, all 18 successful |
| Hibernate/schema validation and packaged startup | PASS — `ddl-auto=validate`, application started on isolated port 8086 |
| MySQL actually verified | **9.2.0**, isolated server on 127.0.0.1:3320 |
| MySQL 8.4 | **PENDING** — Docker command/MySQL 8.4 runtime unavailable; 9.2 success is not an 8.4 compatibility claim |
| `git diff --check` | PASS; only Git's Windows LF/CRLF advisory warnings |

The backend release run used a newly created schema, not the development database. The live acceptance app used a separate `busgo_m20_acceptance` schema on port 8085 with the frontend on 5180. SQL fixture changes, demo accounts, simulated collections/refunds and cancellation affected only this isolated acceptance data. The earlier combined command rejected by automatic approval review did not execute and was not a test failure. Verification was resumed with separate schema creation, environment setup and Maven commands. The frontend build's sandbox directory-access error was resolved by rerunning the same build with filesystem access; no source change was needed.

### Concurrency and rollback

All five M20 concurrency/atomicity cases passed in the full integration suite:

* Same target seat: only one contender acquires/completes the target allocation.
* Opposite-direction trip changes: deterministic ascending trip locks complete without a lock-order deadlock.
* Same booking active-attempt race: exactly one active modification is admitted.
* Expiry versus confirmation: one consistent terminal outcome, no partial switch or leaked target allocation.
* Injected failure during completion: inventory, items, tickets, payment/refund and history changes roll back together.

The original cancellation/payment concurrency tests also pass with the V18 shared-current-read refund guard. Functional coverage includes repeated modifications followed by cancellation, current principal/ticket reads, historical payment preservation, partial-ticket preservation, expiry, stale links, ownership/roles, target lifecycle and alternative-trip timestamp consistency.

### Live acceptance

| Scenario | Evidence/result |
|---|---|
| Paid WEB, one of three seats | PASS — L03→L06; L04/L05 current ticket IDs 11/12 remained untouched |
| Same-fare paid trip change | PASS — 1,950,000 ₫→1,950,000 ₫, no cash adjustment |
| Higher-fare paid change | PASS — 1,950,000 ₫→2,250,000 ₫, simulated collection 300,000 ₫ |
| Lower-fare paid change | PASS — 2,250,000 ₫→1,800,000 ₫, simulated refund 450,000 ₫ |
| Current ticket/payment read after repeated changes | PASS — three current electronic tickets and current total 1,800,000 ₫ rendered; original 1,950,000 ₫ payment and paid timestamp retained |
| Customer cutoff | PASS — explicit six-hour ineligibility message; modification links unavailable |
| History and booking identity | PASS — mappings, actor categories and cash snapshots visible; `BG-6CF9B0649B4D4390` retained through all changes and cancellation |
| PHONE PAY_ON_BOARD trip change | PASS — trip changed, full 600,000 ₫ remains due; no payment or ticket created |
| Operator-assisted PHONE and WEB seat changes | PASS — both completed; WEB L06→L07 still leaves ticket IDs 11/12 untouched |
| Operator audit actor | PASS — history displays Quản trị nhà xe and Demo An Phu Operator Admin |
| Staff | PASS — browser exposes history/admin-required reason, no mutation links; live mutation API returns 403 |
| Foreign customer/operator | PASS — browser displays concealed not-found result; live APIs return 404 |
| SYSTEM_ADMIN | PASS — live modification API returns 403; integration role guards also pass |
| Stale payment link | PASS — browser invalid/replaced-link message; old token context and confirmation APIs both return 404 |
| Cancellation after five completed modifications | PASS — remaining refund 1,800,000 ₫ allocated as 1,500,000 ₫ + 300,000 ₫; total collections/refunds both 2,250,000 ₫; exactly three current tickets VOID |

Customer seat-selection/review and operator trip-selection/review were checked at widths 390, 820 and 1440. Document scroll width equalled client width at each (375, 805 and 1425 respectively, allowing for the scrollbar). The final customer detail/cancellation history also passed at 390. No uncaught JavaScript/console errors were recorded. Temporary viewport overrides were reset.

Local verification artifacts (ignored `.tools` directory): `m20-release-verify.log`, `m20-release-unit.log`, `m20-release-package.log`, `m20-release-startup.log`, `m20-release-frontend-test.log`, `m20-release-frontend-build.log`, `m20-security-live.log`, `m20-browser-seat-success.png`, and `m20-browser-cancellation-390.png`. These are local evidence, not committed fixtures or deployment configuration.
