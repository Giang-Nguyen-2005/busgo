# M25 — Live trip operations

## Baseline audit (before implementation)

1. `Trip` maps `trips`: UTC planned departure and estimated arrival, with lifecycle SCHEDULED → BOARDING → DEPARTED → COMPLETED and CANCELLED. V1–V21 are immutable.
2. `OperatorTripOperationsService.updateStatus` locks the owned trip and enforces crew readiness, origin pickup closure and all-pickup completion. Repeated transitions return unchanged state.
3. Operator admin writes require exactly one active operator membership; staff read only. Employee DRIVER/ASSISTANT capabilities are crew eligibility, not staff mutation grants. SYSTEM_ADMIN is explicitly denied.
4. Operator `TripWorkspace` shares detail, lifecycle confirmation, crew and occupancy. Reuse it for controls.
5. Customers use TripPage, BookingDetailPage and TripCard; booking detail remains available after bookability ends. Public trip selection deliberately rejects unbookable trips.
6. Booking pickup uses its snapshotted `pickupTripStop.plannedDepartureTime`; search uses the selected journey stop. Neither is necessarily the origin.
7. M23 inserts notifications and email delivery rows in the business transaction. Unique recipient/event keys deduplicate; the worker sends committed outbox rows. Rollback leaves no delivery. Preferences group booking changes and reminders.
8. M24 eligibility checks the CURRENT booking trip is COMPLETED, paid confirmed booking and retained active items. Preserve these rules.
9. Cancellation locks trip → operator → booking and releases only eligible inventory; terminal lifecycle must never be resurrected. Booking cancellation is separate from the forward-only status endpoint; the baseline has no trip-cancel mutation API.
10. `BusGoTime` uses UTC LocalDateTime persisted domain values, UTC offset API values and Asia/Ho_Chi_Minh business dates. `JpaJdbcTime` aligns JDBC bindings with Hibernate.
11. TanStack Query already polls seats (15s) and operator workspace (30s). Add bounded detail polling only, with terminal stop.
12. Demo seeder creates rolling scheduled trips, snapshots stops and seeds crew only for new trips; preserves existing operations. Integration fixtures use isolated real MySQL data.

## Contract

Delay is an operational condition, never a lifecycle state. Scheduled departure/arrival and stop snapshots remain unchanged. Current fields live on `trips`; append-only snapshots live in `trip_operational_updates` (V22). Delay changes recalculate expected origin departure and arrival from scheduled times. DEPARTED ETA edits preserve departure and validate arrival against actual departure. Intermediate pickup estimate is the selected scheduled stop plus current delay; it is not a GPS estimate.

Operational POST requests require a `requestKey` (1–100 ASCII letters/digits/hyphen/underscore). Reusing a key with the same normalized payload returns current state without another history/event; a different payload conflicts. Each deliberate new publication needs a new key. Forward lifecycle actions retain their existing idempotency and confirmation UI; actual timestamps use backend Clock and are recorded once. History reads return the newest 50 entries with no update/delete API.

Notifications TRIP_DELAYED, TRIP_DELAY_CLEARED, TRIP_ETA_UPDATED (ETA without announced delay), TRIP_DEPARTED and TRIP_COMPLETED reuse M23's outbox. Only confirmed bookings or unpaid PHONE/PAY_ON_BOARD reservations with active items qualify. Current booking trip and selected pickup determine recipients/content; cancelled items are omitted. Email uses booking-change preferences and existing configuration. Reminders keep their existing 24h/2h occurrence/threshold semantics while content includes current operational information.

Customer booking detail polls every 45s while active; terminal trips or cancelled bookings stop. Search remains filtered/sorted by scheduled times; cards add expected selected pickup/arrival. Operator members can read state/history; only owned operator admins mutate. No SYSTEM_ADMIN bypass. M20/M21 money and inventory and M24 review rules are unchanged.

Limitations: no GPS, stop-specific travel prediction, map, WebSocket, payment gateway or M26+. A post-departure custom origin ETA is distinct from the simple selected-stop delay estimate.

## Verification

Completed verification and its exact limits are recorded below.

### Verified results — 6 October 2026

| Check | Result |
| --- | --- |
| Compile | Passed with repository Java 17.0.20.1 |
| Focused M25 | 31 passed (21 domain/security/read tests, 6 real concurrency tests, 4 notification/outbox tests) |
| Focused M23/M24/lifecycle regression pass | 33 passed; combined focused run 64/64 |
| Frontend polling tests | 2 passed |
| Full backend unit suite | 56 passed, no failures/errors/skips |
| Full integration stage (once) | 386 executed: 385 passed, one outdated CoreDatabaseIT V21 schema assertion failed |
| Affected recheck | Schema assertion updated to V22/new history table; CoreDatabaseIT 44/44 and strengthened M25NotificationsIT 4/4 passed (48/48). No full integration rerun |
| Package | `mvn package -DskipTests` passed |
| Full frontend / acceptance fixes | 135/135 passed; production build passed, including recheck after timing labels and completion review-query refresh |
| Whitespace | `git diff --check` passed |
| Immutable migrations | All 21 V1–V21 SQL files match HEAD checkout-filtered bytes; only V22 added |
| Database | `SELECT VERSION()` returned 9.2.0, MySQL Community Server (not 8.4) |
| Fresh packaged startup | busgo_m25_acceptance had 0 tables before launch; V1–V22 applied; Hibernate `ddl-auto=validate` initialized; packaged app started on 8095 |

The initial requested `mvn verify -Pmysql-integration` stopped in packaging before integration tests because the prior M24 local demo process held the JAR open on Windows. That identified demo process was stopped; `mvn failsafe:integration-test failsafe:verify -Pmysql-integration` executed the integration suite exactly once. Its sole outdated schema assertion was repaired and rechecked through the affected class only. The full verify command is not claimed as green.

### Limited browser acceptance

A–E passed with real isolated API fixtures and browser-operated publish/forward lifecycle controls:

- Scheduled trip #7, customer booking #1, and booking #2 with a real M21 partial cancellation. Selected pickup was Nha Trang at 15:00 Vietnam time, distinct from origin 06:00.
- Browser published delay 20, then 30 minutes. Customer showed pickup 15:20, then 15:30, and expected arrival 03:20, then 03:30 the next day. Scheduled journey times stayed intact.
- Notification panel showed each delay once per booking. Two direct retries using the same browser request key left exactly two delay events per booking and no extra history. Partial booking notifications contained retained L04 and excluded cancelled L03.
- A dedicated fictional driver was created/assigned using existing guarded APIs. Browser confirmed BOARDING. Existing APIs closed the empty origin pickup; browser confirmed DEPARTED. Actual departure appeared on both views. Repeated departure API preserved it and one event per booking.
- Existing direct-board/pickup-close APIs resolved only the two retained passengers; cancelled L03 was absent from attendance. Browser confirmed COMPLETED. Actual arrival appeared, with one completion event per booking and original timestamp preserved on repeat. Both M24 eligibility APIs returned eligible and the customer review form appeared.
- Operational history showed four immutable snapshots (20, 30, departure, completion); terminal operator mutation controls disappeared.
- Customer booking inspected at 390px (document width 375) and 1440px (document width 1425), with no horizontal overflow; operator workflow inspected at 1440px. Temporary viewport override reset. No screenshot gallery.

The fixture trip is scheduled tomorrow but was executed now deliberately for acceptance. Actual timestamps correctly reflect backend current time; they were not manually assigned. Crew, boarding and pickup guards were honored. Browser customer refresh was used for acceptance; automated frontend tests verify the 45s interval and terminal stop. Reviews remount/refetch eligibility when the booking's current trip or lifecycle changes.

No real SMTP transmission was attempted; accountless email/preference/outbox/rollback behavior was tested with configured mock delivery plus M23 delivery regressions. No GPS/per-stop ETA prediction, map, WebSocket, M22 gateway, M26 or UI polish added. The baseline still has no operator trip-cancel mutation API; cancelled-state protection was tested under the authoritative trip lock.

Evidence is in ignored `.tools/m25-*.log`, `m25-api-acceptance.json`, `m25-browser-fixture.json`, and `m25-immutable.json`. Local acceptance app: http://127.0.0.1:5175 (API 8095). Changes are uncommitted on feature/v2-live-trip-operations: 29 modified, 12 new files. **NO COMMIT. NO PUSH. NO TAG.**
