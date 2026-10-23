# M29.3 — Operator dashboard and visual dispatch

**COMPLETE for the frontend scope; awaiting human visual review.**

Implemented on 23 October 2026 (Asia/Saigon) from verified `main` at `f4cc230`. Branch: `feature/v2-m29-operator-dispatch`. All changes remain uncommitted. No push, merge, tag, Git identity or remote changes. Historical untracked `tatus --short` preserved. No applicable AGENTS.md was found.

## Dashboard

The production `/operator` page extends the M29 V3 teal navigation and M29.1 foundations. A dark command panel pairs the complete day's five-state distribution with a priority trip (boarding first, then upcoming scheduled, then overdue scheduled). Counts have a visible Vietnam business-date basis. Empty, completed-only and cancelled-only days retain their actual states. Planned departure never advances the trip lifecycle.

A bounded-height schedule offers departure time, route, bus, actual status, overdue warning, detail link and a direct dispatch link. The existing highlighted trip retains booking and occupancy data, with explicit incomplete-inventory warnings. Operational management attention, fleet readiness and one financial/business summary remain admin-only. Financial language continues to describe simulated payments. No occupancy is fetched for the other schedule rows.

## Dispatch architecture and API sources

`/operator/dispatch` is a real lazy React Router page. Both operator roles use the existing operator guard. Customer and System Admin are denied. Navigation, icon map, layout group and access helper agree. Staff receive no new mutation, finance, fleet, configuration or management permissions.

| Data | Existing source | Scope and cache |
| --- | --- | --- |
| Daily dashboard | `operatorApi.trips` through `allPages` | Existing `['operator','trips','today',date]`; all pages; 60-second refresh |
| Dispatch schedule | `operatorApi.trips(filters, signal)` | Existing `['operator','trips',filters]`; 20 records per page; 30-second refresh |
| Trip snapshot | `operatorApi.trip(id, signal)` | `['operator','trips',id]`; selected trip only; 30 seconds |
| Seat inventory | `useOccupancy` → `operatorApi.occupancy(id, signal)` | `['operator','trips',id,'occupancy']`; selected trip only; 30 seconds |
| Booking inspector | `useOperatorBooking` → `operatorApi.booking(id, signal)` | `['operator','bookings',id]`; only the explicitly opened related booking |
| Highlighted trip booking count | Existing `operatorApi.bookings({tripId,page:0,size:1})` | Existing dashboard key; total booking count, not passengers |
| Management and fleet | Existing `useManagementOverview`, `ManagementOverview`, `FleetDashboardWarnings` | Existing authorized report/readiness endpoints; admin only |

No new endpoints, passenger-list prefetches, route-choice calls or bus-choice calls. Trip detail and occupancy start together. All queries pass AbortSignal through existing clients. Trip/date/status/page changes key the selected workspace, resetting floor, segment/journey, seat, booking and expiry tracking. The existing auth/session code clears cache on logout/authorization loss; 401/403 suppress cached sensitive content. Transient refresh errors retain valid prior data with a warning and retry.

Schedule pagination displays the server total and current page. Date and status apply server-side. Route, bus and text filters explicitly apply to the loaded page only, using permitted trip data. Changing page clears these local filters. URL state supports back/forward and deep links; an explicit authorized trip ID can open outside the current schedule page, with its actual departure date visible in the workspace. The UI does not claim that page-local filters searched the entire day.

## Seats and inspector

The new compact `DispatchSeatGrid` is presentation only. It uses the original `selectedSegments`, `seatCells`, `cellStatus`, `cellLabels`, `seatSummary`, `boardStatus`, `intersectingBookings` and `refreshExpiredHolds` helpers. No independent inventory algorithm was introduced. Existing `OperatorSeatsPage` remains unchanged.

- A single segment selects automatically. Multi-segment trips start without a context and disable inspection until an explicit segment or continuous forward journey is valid.
- Journey endpoints use actual trip-stop IDs. Contiguity and order come from the shared domain helper, never matching stop names.
- Floors preserve row/column indices and aisle/row gaps; wide grids scroll locally. Changing floor clears the inspector.
- Missing cells stay missing. Mixed states or distinct booking identities show “Khác nhau theo chặng.” Availability never comes from just one part of a journey.
- Expired holds cause one immediate refresh per seat/segment/expiry key; ordinary polling handles unchanged server holds. No local conversion to AVAILABLE.
- Inspector lists every selected segment's exact state and hold expiry. Separate booking buttons preserve identities for disjoint segments. Only clicking a booking loads it.
- `BookingInspectionContent`, `CompletenessWarning`, `SeatLegend` and `HoldState` are reused. Booking contact and passenger identity stay in the inspector, not the grid. Payment comes from actual booking payment records. Booking, payment and boarding remain separate concepts.
- Inspector is read-only, with existing booking detail, passenger and occupancy links. No payment, booking, check-in or trip-status commands were added.

## Responsive behavior

Desktop: trip list, seat workspace and adjacent sticky inspector. At tablet widths the inspector moves below the workspace. Mobile: compact date/trip selection, filter sheet, segment/journey and floor controls, local seat scrolling and an inspection sheet. Modal sheets reuse the native M29.1 Drawer for Escape, focus containment/return, close control, inert background and scroll locking. The shared Drawer gains only an optional close-label prop; navigation defaults stay unchanged.

Dispatch-specific light drawer styles correct the navigation drawer's dark-theme inheritance when rendering light booking cards. Seat selection uses restrained 140–160 ms transitions and respects reduced motion. No chart or UI dependency was installed.

## Verification

- `npm run build`: PASS (TypeScript and Vite production build). Windows sandbox blocked Vite realpath access on the first attempt; the authorized build outside that restriction passed.
- `npm test`: **157 passed, 0 failed**. New tests cover exact dispatch permissions/navigation, floor/row/column geometry, missing and mixed inventory, distinct booking IDs, context validation and hold expiry. Existing dispatch tests continue covering whole-journey availability, booking/payment separation, transient vs authorization errors, late-session isolation and existing seat routes.
- The earlier dashboard ordering regression assertion was updated to the new approved command/schedule-before-management composition; it still checks exactly one business summary.
- `git diff --check`: PASS.
- Browser: real React app, production router/guards and real API client, with deterministic synthetic HTTP fixtures. **44 screenshots**, both operator roles at **1440, 834, 390 and 360 px**. No live backend or authenticated live smoke test was performed or claimed.
- Browser checks: normal/no/one/many trips, completed/cancelled days, pagination and back/forward, one/two floors, explicit multi-segment journey, mixed/missing cells, expired-held server refresh, separate bookings for one reused seat, inspector close/reopen and keyboard focus/Escape/scroll locking, mobile filters, no-match results, date resets, slow list loading, failed refresh with retained data and retry, 403 cache removal, off-page deep links, role-restricted data requests and Customer/System Admin direct-URL rejection. Requests are asserted GET-only.
- Screenshots were visually inspected at desktop, tablet and mobile sizes. This caught and prompted correction of excessive spacing and mobile inspector contrast. Overflow assertions also passed.

## Evidence

All review outputs are under `.tools/m29-browser/m29-3/` (the repository ignores `.tools`; evidence remains local):

- `review.html` — screenshot gallery.
- `acceptance.cjs`, `acceptance.json`, `acceptance.log` — repeatable browser fixture harness and recorded assertions; harness also uses the existing `../fixtures.cjs`.
- `build.log`, `tests.log` — final command results.
- `OPERATOR_ADMIN-dashboard-{1440,834,390,360}.png` and `OPERATOR_STAFF-dashboard-{1440,834,390,360}.png`.
- `OPERATOR_ADMIN-dispatch-{1440,834,390,360}.png` and corresponding `OPERATOR_STAFF` captures.
- `OPERATOR_ADMIN-inspector-{1440,834,390,360}.png` and corresponding `OPERATOR_STAFF` captures.
- For each role: `stale-refresh-1440`, `empty-1440`, `one-1440`, `completed-1440`, `cancelled-1440`, `dispatch-empty-1440`, `loading-1440`, `filter-empty-360`, `second-booking-1440`, `floor-two-1440` PNGs.

## Exact source/document inventory

1. `frontend/src/components/foundations/Drawer.tsx` — optional accessible close label.
2. `frontend/src/components/foundations/navigationIcons.ts` — dispatch icon.
3. `frontend/src/features/auth/access.ts` — exact staff dispatch path and shared navigation.
4. `frontend/src/features/operator/dispatch-board.css` — dashboard/dispatch compositions and responsive styling.
5. `frontend/src/layouts/OperatorLayout.tsx` — dispatch in operational navigation.
6. `frontend/src/pages/operator/OperatorHomePage.tsx` — dashboard command area, warnings, schedule links and retained data.
7. `frontend/src/pages/operator/OperatorDispatchPage.tsx` — selected-trip workspace, schedule, filters, geometry and inspector.
8. `frontend/src/routes/router.tsx` — lazy guarded route.
9. `frontend/tests/m19.1-ui-polish.test.mjs` — new dashboard ordering regression expectation.
10. `frontend/tests/m29-operator-dispatch.test.mjs` — focused route/domain/presentation tests.
11. `docs/m29-operator-dispatch.md` — this handoff.

## Limits and next phases

- Backend integration was exercised through intercepted API responses, not a running backend. Production authentication/data smoke testing remains a separate human/environment acceptance step.
- Route/bus/text search is page-local, explicitly labelled. No full-day occupancy totals or invented boarding percentages are provided.
- Complete dashboard counts require all day-list pages to finish. The existing API offers no atomic multi-page snapshot; concurrent server changes during pagination follow its existing consistency behavior.
- Segment/floor/inspector state deliberately resets on trip/date/page/status changes; URL history retains date/status/page/trip and page-local filters, not individual inspected seats.
- Human visual approval remains pending. No M29.4 or M29.5 implementation was started; their finance/support/system-admin scope remains for separately authorized work.

No backend, database, business-rule, payment, voucher, booking, attendance or seat-hold changes. Stop after M29.3; do not commit or push.
