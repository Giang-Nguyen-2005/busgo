# M21 — Partial cancellation

## Pre-implementation audit

Baseline: `feature/v2-partial-cancellation`, clean working tree, V17/V18 are latest.
Booking identity/contact/source and current total live on bookings. Items contain current
seat/passenger/unit-price snapshots but have no lifecycle flag. M20 changes those current
snapshots and retains old values in modification history. Current total must therefore
sum active recorded item prices, not route fares.

Tickets use `replaced=false` for the current ticket, with VOID replaced tickets retained.
Attendance is unique per item; EXPECTED can remain as historical data after cancellation,
but current operational reads must filter cancelled items. Terminal attendance blocks
cancellation of the selected item.

Segment inventory is owned by booking_item_id. Full cancellation validates and releases
all item allocations; it must instead validate the remaining active set after M21.
Payments are one-to-many, with immutable successful amounts/timestamps/purposes. M20's
refund allocator consumes remaining payment balances in ID order. V18 validates refunds,
but currently requires a non-modification refund to consume the entire payment balance.
V19 must extend that existing architecture with partial-cancellation linkage.

Commerce locks source trip, operator, refreshed booking, modification, ordered payments,
current tickets, attendance/pickup, then inventory. M20 locks both trip IDs in sorted
order. HELD/AWAITING_PAYMENT must block M21. Current accounting must use locking reads
after booking serialization, avoiding the earlier ownership-read transaction snapshot.

Affected reads: ModificationService, CancellationService, PaymentTicketService,
OperationsService boarding/attendance, occupancy, report item counts, customer item
counts, booking list/detail, and operator item reads. Detail/history may retain cancelled
items with explicit status. Existing shared cancellation/modification UI provides the
visual conventions for a shared M21 section in customer and operator detail.

## Implementation and verification

Implementation and acceptance completed on 2026-10-06. No commit, push, tag, gallery generation, or unrelated UI redesign.

## Domain and API

One or more selected active items may be cancelled while at least one remains. Selecting
all remaining items returns FULL_CANCELLATION_REQUIRED. Repeating an item returns
ITEM_CANCELLED; foreign items/history return the existing concealed 404. No held lifecycle,
replacement booking, fee, collection or gateway action is introduced.

Customer requires own WEB, active operator/route, SCHEDULED trip, future/open pickup,
at least six hours before pickup, unexpired pending deadline and no active M20 attempt.
Operator Admin supports owned WEB/PHONE without the six-hour cutoff. Staff reads only;
SYSTEM_ADMIN has no bypass. CHECKED_IN/BOARDED/NO_SHOW block the selected item only.

Both /api/v1/bookings/{id} and /api/v1/operator/bookings/{id} expose:
* GET /partial-cancellation-eligibility (booking reason and per-item status/reason)
* POST /partial-cancellation-quote with {bookingItemIds:[...]} (transient, no writes)
* POST /partial-cancellations with the same selection (locked revalidation and execution)
* GET /partial-cancellations (latest 50 immutable completions)
* GET /partial-cancellations/{cancellationId} (owned completion)

Quotes/history expose journey/departure, selected passenger/seat/amount snapshots,
currentTotal, cancelledAmount, newTotal, netCollected, refundRequired and newAmountDue.
Execution returns a completed history record. Monetary client input is never accepted.
Booking detail exposes cancelled and bookingItemId on customer seats; operator items
also expose cancelled. Current ticket bundles exclude partially cancelled items; their
VOID tickets remain accessible in recovery/history. UI uses server review and explicit
confirmation, then refreshes current booking/tickets/eligibility/history.

## Schema and money

Only V19__partial_cancellations.sql is added; V1–V18 stay unchanged. booking_items.cancelled
retains historical item rows and snapshots. Active-only generated seat uniqueness allows
M20 to reuse a released seat without deleting history. partial_cancellations snapshots
booking/operator/actor identity, journey/time and all money values; child rows snapshot
passenger, item, old seat, recorded amount and current ticket reference. Services expose
no history update/delete action. refunds gains partial_cancellation_id and unique
payment/cancellation linkage. The existing refund trigger is extended, retaining full
cancellation and M20 ownership/balance validation.

currentTotal = sum(active recorded unitPrice); cancelledAmount = sum(selected unitPrice).
newTotal = currentTotal - cancelledAmount. netCollected = successful collections minus
prior refunds. refundRequired = max(netCollected-newTotal,0);
newAmountDue = max(newTotal-netCollected,0). M20's allocator refunds payment balances in
ascending payment ID order. No successful amount, paid timestamp or purpose is changed.
Unpaid/PAY_ON_BOARD produces neither payment, refund nor ticket. PHONE QR revokes its
old token hash on success; deadlines are unchanged. Failure rolls back the original link.

## Atomicity and compatibility

M21 reuses M20 ownership and trip/operator/booking locking. Order is source trip,
operator, refreshed booking, active modification rows, ordered successful payments and
refund rows, current tickets, attendance/pickup, current items and selected inventory.
All item/history/total/refund/ticket/inventory/link changes share one transaction.
Payment and refund reads use explicit current locking reads, even when the ownership
lookup established an earlier repeatable-read snapshot. Cross-trip M20 locks remain in
sorted trip ID order; changed source trip is rejected for retry.

M20 candidates/plans/confirmation use active current items only. Full cancellation uses
remaining active items/tickets and remaining refundable balances. Operational boarding,
occupancy, customer counts and report item joins filter partial cancellations; payment
and refund event aggregates remain the existing accounting. EXPECTED attendance is
preserved as history but excluded operationally; terminal attendance is never reset.

## Verification record

Results below were verified on the current working tree.
Known product limits: mock refunds only; no fees, promotions, notifications or gateway.
History endpoints return the latest 50 records; no history-list pagination in this milestone.
Full-booking lifecycle remains represented by booking status, while the item cancelled
flag records partial cancellations. No M22+ or visual redesign work is included.


### Automated verification

* Compile passed. Relevant ModificationMoneyTest: 5 passed.
* Focused M21/M20/full-cancellation regression pass: 56 passed. The final M21 set is
  21 tests (15 domain/security and 6 concurrency/rollback), all passed. This includes
  the five required race/rollback cases plus failed PHONE QR link/deadline rollback.
* CoreDatabaseIT: 44 passed with V19 schema assertions; M20ModificationIT: 28 passed.
* Final full backend verify: **50 unit tests + 307 integration tests**, zero failures,
  errors or skips. Completed 2026-10-05 23:27 Vietnam time. Not repeated on continuation.
* The earlier standalone unit run exposed a missing PartialCancellationService mock in
  the database-free FoundationTest. Only that fixture was fixed; its 6 focused tests
  passed, then all 50 unit tests passed in the single full verify run.
* Frontend: **131 tests passed**, production build passed. Two targeted M21 selection
  tests and TypeScript checking passed. No frontend rerun on continuation.
* Fresh package from current sources: mvn package -DskipTests passed on 2026-10-06.
* git diff --check passed. V1–V18 have no changes.

Evidence logs (local ignored .tools directory): m21-focused.log,
m21-final-focused.log plus m21-last-focused.log for the final corrected fixture,
m21-foundation-focused.log, m21-full-verify.log, m21-full-frontend-test.log,
m21-full-frontend-build.log and m21-acceptance-package.log.

### Fresh schema / packaged startup

Runtime tested: **MySQL Community Server 9.2.0**, local TCP port 3320. No MySQL 8.4
claim is made. busgo_m21_acceptance had zero tables before startup. The packaged JAR
applied V1–V19, initialized Hibernate with ddl-auto=validate, and started successfully
on port 8086. The acceptance frontend uses API_PROXY_TARGET=http://127.0.0.1:8086
and port 5177. The existing test database process had stopped between sessions; it was
restarted with its existing data directory, then only startup was retried. No suite
rerun was needed. Logs: m21-mysql.err.log and m21-startup.log.

### Limited live acceptance

A. Paid WEB, BG-300279AACEBD46E2: L03/L04/L05 at 650,000 each. Browser cancelled L04;
refund 650,000, remaining total 1,300,000. Booking code unchanged. Remaining ticket IDs
1 and 3 exactly unchanged; selected current ticket VOID; selected inventory released.

B. Browser M20 seat change L03 → L09 completed. Cancelled L04 item, original seat and
VOID ticket stayed unchanged. Only active L03/L05 were offered as modification sources.

C. PHONE PAY_ON_BOARD, BG-27E9228FC1684A29: browser cancelled L07 from three items.
Remaining total/due 1,300,000; refund zero; database confirms zero payments, refunds and
tickets. Cancelled passenger is clearly labelled; historical recorded price remains.

D. Browser full cancellation after A and B refunded remaining 1,300,000. Total refunds
1,950,000 equal original collections. Previously released L04 allocation versions did
not change; each remaining active allocation incremented exactly once and became
AVAILABLE with no owner. Partial history remained intact.

Additional live HTTP checks: staff mutation 403, foreign operator mutation 404,
SYSTEM_ADMIN mutation 403; staff eligibility is read-only with ADMIN_REQUIRED.
Changed customer and operator review/confirmation controls were usable at 390px and
1440px. No horizontal document overflow was observed. No broad browser matrix or
screenshot gallery was generated; one local result screenshot was retained.
Live database assertions are saved in .tools/m21-acceptance-evidence.json.

### Delivery state

Changes remain on feature/v2-partial-cancellation, unstaged/uncommitted. No commit,
push or tag. No product-code changes were required by live acceptance. Remaining
limitations are the scoped mock-refund/no-fee model, latest-50 history reads, and no
verification against a separate MySQL 8.4 runtime. No outstanding M21 verification.

