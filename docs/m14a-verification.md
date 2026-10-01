# M14A verification

## Backend contract phase (existing work)

- `GET /api/v1/operator/trips` Vietnam `businessDate` filtering while retaining
  legacy UTC `date` behavior.
- `GET /api/v1/operator/trips/{tripId}/occupancy` completeness over snapshot
  seats × snapshot segments.
- This earlier phase covered backend and contract documentation only, with no
  frontend, schema, lifecycle, or locking changes.

## Contract coverage added

`M14AOperatorContractIT` covers normal Vietnam dates, the exact Vietnam-midnight
UTC boundary, overnight trips (classified by departure business date), rejection
of simultaneous `date` and `businessDate`, operator ownership, and
`OPERATOR_STAFF` reads. Occupancy cases cover a complete multi-segment matrix, one
missing cell, an entirely missing seat, exclusion of incomplete seats from the
whole-trip available count, all four real statuses, reuse of one physical seat on
non-overlapping segments, staff reads, and foreign-operator isolation.

## Previously recorded backend verification (not rerun in the frontend phase)

| Command | Result |
| --- | --- |
| `mvn test` (Java 17.0.16) | PASS: 28 tests, including 4 M14A service contract tests; 0 failures, 0 errors, 0 skipped |
| `mvn verify -Pmysql-integration` (Java 17.0.16) | BLOCKED: the unit phase passed, then all 136 integration tests errored during application-context startup because configured MySQL at port 3307 was unreachable |
| Focused `M14AOperatorContractIT` attempts | BLOCKED before test-method execution: port 3307 was unreachable; the available local MySQL service on port 3306 rejected the repository-configured credentials |

`mvn verify -Pmysql-integration` still requires a reachable dedicated MySQL 8.x
instance with valid `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. It is not claimed
as passing in this document.

## Frontend operator UX phase — verification completed 2026-09-30

Branch: `feature/operator-ux-overhaul`. No commit or push was performed.
The continuation after the usage interruption performed verification and documentation
only; it did not redo or change the completed frontend implementation.

### Scope and diff review

The five modified backend Java files, two untracked backend test files,
`docs/api-contract.md`, and the existing backend sections of the milestone
documents were already present when frontend work began. They were preserved.
No backend contract mismatch requiring a change was found.

Reviewed the working diff and changed-file inventory. No customer page/layout,
system-admin page/layout, global styles source, package manifest, or lockfile was
changed. Shared router changes are confined to operator routes. The access helper
only adds the staff seat-route allowlist entry. The operator API interceptor
handles only operator 401/403 responses from the current session; a regression
test covers late failures from replaced sessions. New CSS overrides are scoped
under the operator shell.

### Files created for the frontend phase

- `frontend/src/features/operator/RefreshState.tsx`
- `frontend/src/features/operator/TripWorkspace.tsx`
- `frontend/src/features/operator/dispatch.ts`
- `frontend/src/pages/operator/OperatorSeatsPage.tsx`
- `frontend/src/routes/operatorTripRoutes.ts`
- `frontend/tests/operator-dispatch.test.mjs`
- `frontend/tests/dispatch-preview.html`
- `frontend/tests/fixtures/dispatch-browser.html`
- `frontend/tests/fixtures/dispatch-browser.tsx`

The browser fixture uses synthetic data and an isolated Axios adapter. It neither
logs in nor calls a backend and is not imported by the production entry point.

### Files modified for the frontend phase

- `frontend/src/api/operatorApi.ts`
- `frontend/src/features/auth/access.ts`
- `frontend/src/features/operator/OperationsShared.tsx`
- `frontend/src/features/operator/operations.ts`
- `frontend/src/features/operator/operator.css`
- `frontend/src/features/operator/queries.ts`
- `frontend/src/features/operator/shared.tsx`
- `frontend/src/layouts/OperatorLayout.tsx`
- `frontend/src/pages/operator/OperatorBookingsPages.tsx`
- `frontend/src/pages/operator/OperatorHomePage.tsx`
- `frontend/src/pages/operator/OperatorTripOperationsPages.tsx`
- `frontend/src/pages/operator/OperatorTripsPages.tsx`
- `frontend/src/routes/router.tsx`
- `frontend/src/types/operator.ts`
- `frontend/tests/management.test.mjs` (requested lifecycle wording expectation)
- `docs/m14a-verification.md` (pre-existing untracked document extended)
- `docs/development-plan.md` (existing content retained)

### Routes and workspace

New: `/operator/trips/:tripId/seats`.

Existing routes retained and updated: `/operator`, `/operator/trips`,
`/operator/trips/:tripId`, `/operator/trips/:tripId/passengers`,
`/operator/trips/:tripId/occupancy`, `/operator/bookings`, and
`/operator/bookings/:bookingId`.

Trip detail, seats, passengers, and occupancy are nested under one persistent
TripWorkspace. Its route, Vietnam departure date/time, vehicle plate/type,
status, refresh control, and admin-only lifecycle control remain common to all
four tabs: Tổng quan, Sơ đồ ghế, Hành khách, Tình trạng chặng. Direct entry,
reload-style initial routing, navigation, and back behavior are covered using
the actual lazy route definition in the automated suite.

### Implemented behavior

- Dashboard reads today's real trips using Vietnam businessDate, prioritizes
  BOARDING and SCHEDULED trips, and labels overdue scheduled trips “Quá giờ dự
  kiến”. It never infers departure from the timetable. Management shortcuts are
  separate and admin-only.
- Trip rows emphasize departure time, route, and vehicle. Date, route, vehicle,
  status, and pagination remain URL-backed. The default is today in
  Asia/Ho_Chi_Minh; only businessDate is sent for this filter.
- The seat board uses trip.seats snapshot IDs, floors, rows, columns, and seat
  codes. Gaps are preserved; no vehicle orientation or facilities are invented.
- A single segment auto-selects. Multiple segments require explicit valid
  selection. Journey selection uses TripStop IDs and resolves a contiguous
  ordered sequence of TripSegment IDs. Context is represented in URL parameters.
- Four real inventory statuses retain Vietnamese text labels. Missing cells are
  visibly distinct and never treated as available. Backend completeness and
  missing-cell counts produce an integrity warning.
- Mixed journey states and reuse by different bookings retain ordered per-segment
  indicators. Whole-journey availability requires every selected cell to exist
  and be AVAILABLE. Journey summaries do not invent held/booked/blocked partition
  totals; per-segment summaries are shown instead.
- Seat inspection opens a native modal dialog styled as a right drawer, or a
  full-screen mobile sheet. Booking details load only for the selected booking;
  intersecting bookings each show their covered segments. Passenger-on-seat,
  ticket name, and booking contact remain distinct, including null passenger names.
  Pickup/dropoff, payment state, phone copy action, ticket code, and full-detail
  links are retained.
- HELD expiry remains HELD until the server changes it. Expiry displays an update
  message and triggers one refetch per observed expiry; 30-second foreground
  occupancy polling continues. BLOCKED has no invented reason.
- Passenger search covers seat, booking code, passenger name, ticket name, contact,
  and phone, with grouping by pickup/dropoff IDs. Mobile uses cards/compact rows.
- Occupancy remains an advanced matrix with sticky seat labels and segment
  headers, a legend, completeness warning, counts, and contained scrolling.
- Booking lookup shows contact search confirmation, seat count, journey, status,
  payments, and creation time; text requests are debounced 350 ms. Booking detail
  places contact and passenger information before lower-priority account metadata.
- Last-successful refresh time and manual refresh are visible. Trip/occupancy/
  passenger views poll at 30 seconds, dashboard at 60 seconds, with background
  polling disabled by React Query defaults. Booking details are not polled.
  Transient failures retain cached data; current-session authorization loss clears
  authentication and the sensitive query cache through the existing auth flow.
- Staff can view the seat route and existing read-only operational destinations.
  Trip creation/lifecycle, fleet/route mutations, and staff management remain
  inaccessible. The backend remains authoritative.

### Commands executed in this continuation

| Command | Result |
| --- | --- |
| `npm test` in frontend | PASS: 42 tests, 0 failures, 0 skipped; all 26 existing regressions and 16 M14A tests |
| `npm run build` in frontend | PASS: TypeScript and Vite production build; 2,161 modules transformed |
| `git diff --check` at repository root | PASS; checked again after documentation updates |

Build warnings: Rollup removed two unrecognized Zod purity annotations, and Vite
reported a main bundle larger than 500 kB. Neither warning failed the build.
Git emitted LF-to-CRLF informational warnings, not whitespace errors.

M14A automated coverage: businessDate request serialization; Vietnam midnight
and invalid-date defaulting; real lazy workspace routes/back navigation; staff
seat access and admin-only controls; all single-segment labels; ordered mixed
states and booking reuse; missing cells and entirely missing seats; whole-journey
availability; HELD expiry display/refetch without local status mutation; booking
inspection mapping and nullable identity; lifecycle labels; manifest search and
ID grouping; stale-data preservation; authorization clearing/session race safety;
and server-rendered physical geometry.

### Browser checks actually completed

Browser: Codex in-app Chromium browser. Data: isolated synthetic fixture only.
Responsive sizes: desktop 1440×1100, tablet 820×1000, mobile 390×844.

| Surface / behavior | Observed result |
| --- | --- |
| Dashboard at all three widths | Compact trip rows, date, overdue label, lookup links; staff has no create action; no page-wide horizontal overflow |
| Trip list at all three widths | Strong departure time; route/vehicle/status; Vietnamese filters, date default, view action and pagination visible; rows adapt on mobile |
| Trip workspace | All four tabs render under the shared header; keyboard Tab/Enter reaches passenger view; mobile tab strip scrolls locally |
| Physical seat board | Two snapshot floors, original row/column placement and empty column preserved; mobile scroll regions exceed their own client widths while page remains contained |
| Inspection | Desktop right drawer, tablet overlay, mobile full-height sheet; latest mobile sheet measured at left 0 and height 844px, fitting the content viewport |
| Reused booked seat | Separate BG-100/BG-101 sections with covered segments and on-demand detail; no booking-detail requests before opening a booked seat (earlier completed check retained) |
| Passenger view | Desktop compact columns, tablet wrapping, mobile cards; phone search retains matching rows and distinct identity labels |
| Occupancy matrix | Sticky headers and seat labels verified; mobile matrix horizontal keyboard scroll works locally; statuses and missing cells are named in text |
| Authorization presentation | Staff view omits lifecycle action; switching fixture role to admin exposes its confirmation; no lifecycle mutation was submitted |
| Keyboard / focus | Enter opens seat drawer; visible purple focus outline; modal focus remains inside; Escape closes seat and lifecycle dialogs and restores their triggers |
| Refresh failure | Cached content remains visible with a temporary-failure warning (earlier completed fixture check retained) |

The interrupted Vite server was no longer listening when lazy passenger navigation
was resumed. Restarting the same local preview and reloading restored module
loading; the responsive checks then completed. This was a preview lifecycle issue,
not a live-backend test or application API failure.

Representative screenshots were saved in the task's visualization directory:
`m14a-desktop-final.png`, `m14a-mobile-final.png`,
`m14a-mobile-matrix.png`, and `m14a-dashboard-{1440,820,390}.png`.

### Unverified and practical limits

- No live backend, MySQL, real login, live inventory/booking flow, or end-to-end
  authorization session was exercised in this frontend phase. The earlier
  backend integration blockers above remain historical evidence, not rechecked.
- Actual server-side lifecycle mutation, payment-window effects, and concurrent
  dispatch changes were not exercised in the browser; only rendering/confirmation
  and existing automated mutation regression behavior were checked.
- Clipboard success, screen-reader announcements, full WCAG/contrast audit,
  Safari/Firefox, physical touch devices, and large production data sets were not
  verified. Browser checks are responsive smoke tests, not exhaustive certification.
- Staff route/vehicle filters currently accept IDs because management catalogs
  are not fetched for staff. Admin filters have named choices.
- The booking-list contract exposes seatCount, not seat codes; the list displays
  the real count. Specific seats remain available in booking detail/inspection.

### Deferred scope

Customer and system-admin redesign; new analytics; check-in/boarded/no-show;
passenger editing; seat reassignment or blocking mutations/reasons; cancellation,
refunds, and new lifecycle transitions; backend/schema changes; optional floor
selector and hold countdown; production-scale performance and cross-browser QA.
