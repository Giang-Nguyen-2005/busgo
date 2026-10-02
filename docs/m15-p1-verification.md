# M15 P1 — Live rehearsal UX fixes

Implemented on 2026-10-02 in `C:\Users\Giang\busgo`, branch `feature/final-hardening`.
No commit or push. The existing uncommitted M15 P0 work was preserved. No backend file was edited by this task; the backend modifications in the status snapshot predate P1.

## 1. Files modified or added by P1

Production frontend:

- `frontend/src/features/booking/recovery.ts` (new)
- `frontend/src/pages/BookingPage.tsx`
- `frontend/src/pages/TripPage.tsx`
- `frontend/src/pages/PaymentPage.tsx`
- `frontend/src/features/customer/customer.css`
- `frontend/src/features/operator/TripWorkspace.tsx`
- `frontend/src/features/operator/dispatch.ts`
- `frontend/src/features/operator/operations.ts`
- `frontend/src/features/operator/operator.css`
- `frontend/src/features/operator/shared.tsx`
- `frontend/src/pages/operator/OperatorBookingsPages.tsx`
- `frontend/src/pages/operator/OperatorSeatsPage.tsx`
- `frontend/src/pages/operator/OperatorTripOperationsPages.tsx`
- `frontend/src/pages/operator/OperatorTripsPages.tsx`

Tests, fixtures and report (all new):

- `frontend/tests/m15-ux.test.mjs`
- `frontend/tests/m15-browser-check.mjs`
- `frontend/tests/fixtures/m15-browser.html`
- `frontend/tests/fixtures/m15-browser.tsx`
- `frontend/tests/fixtures/m15-matrix-browser.html`
- `frontend/tests/fixtures/m15-matrix-browser.tsx`
- `docs/m15-p1-verification.md`

Generated screenshots, logs and browser results are ignored artifacts in `.tools/m15-p1-render/`, `.tools/m15-p1-tests.log` and `.tools/m15-p1-build.log`.

## 2. Payment and browser Back

Seat selection → contact form now replaces the seat-selection history entry. Successful booking creation retains the existing replacement transition to payment. For the ordinary search flow, the history is therefore search → payment; Back returns to search and Forward returns to the same payment. No booking is recreated or inventory released.

Payment has a prominent explicit `Quay lại chi tiết đặt vé` link to the current booking. Detail, My bookings, payment and tickets remain the recovery paths.

Successful booking creation stores only account/trip/journey/booking IDs in a bounded, tab-local recovery hint. Revisiting that exact trip and journey queries the authorized customer booking API before explaining the reserved seats and showing the appropriate recovery actions. Status and seat identity are obtained from the server, not inferred from the hint. Corrupt storage is ignored, unavailable storage does not block checkout, another account does not receive the hint, and cancelled/completed bookings do not show pending-payment copy.

## 3. Search-card image

At desktop/tablet widths above 700px, a 170px image column spans both the journey and price/action rows. The image fills most of the card height without adding another content row. Mobile keeps a 120 × 80px thumbnail. Existing generic-image fallback and truthful alternative text are retained.

## 4. Seat board and shared trip workspace

Explicit seat-button styles fix the cascade that made available and booked seats look alike. Available is green, booked blue, held amber, blocked gray, and missing uses warning text and a dashed patterned cell. Every state has a text label; color is supplementary.

Booked cells show booking contact name, phone and booking code using the existing operator booking-detail endpoint. Requests are deduplicated by booking ID and cached for 30 seconds. If details are unavailable, the booking identifier and truthful detail prompt remain. Held cells show expiry when supplied, never invented customer identity.

The existing accessible seat drawer retains booking code/status, latest payment state, contact name/phone/email, selected-seat ticket data, pickup/dropoff, and the segments covered by each intersecting booking. Different bookings on different segments remain distinct. Missing inventory takes precedence over availability; mixed journeys retain every selected segment's label.

Floors, row/column placement and empty columns remain intact. Empty aisle columns are rendered at 26px rather than as an entire 142px seat cell. Floor panels can sit side by side; each has local scrolling. Names clamp to two lines while the drawer retains the full value. The legend and selected-segment counts retain all five states.

The shared header shows route, departure date/time, bus, trip status, whole-trip available seats and distinct seats having booked inventory. Counts explicitly distinguish whole-trip availability from occupancy on any segment. The existing M14A workspace routes and permissions are retained; tabs are compact and have a strong active treatment.

## 5. Booking list and filters

Desktop remains a compact paginated table. Booking code and contact name are the primary links; phone, route, journey timing, seat count, status badges and total are easy to scan. Email and creation time are smaller secondary text. Technical IDs no longer consume a primary table column. Mobile uses compact two-column rows/cards.

The current booking-list contract exposes seat count, not seat codes, and journey pickup/dropoff timing, not a separate trip departure field. The UI labels these truthfully and links to detail for seat codes.

Booking filters are a wrapping labeled panel with keyword, trip ID, booking state, payment state, creation date, active-value chips and reset. Existing URL validation, debounce and pagination remain. Trip filters retain supported business date, route, bus and status; reset clears the URL and restores the existing Vietnam-today default. Staff ID filters and administrator choice lists preserve their existing permissions.

## 6. Occupancy matrix

Removed the duplicate segment-summary table and large overview card. Aggregate seat × segment counts serve as the semantic legend above the matrix. Rows are seats and columns are ordered segment IDs. Headers and seat labels stay sticky in an internally scrolling region; cells have compact status badges/backgrounds plus existing booking links or held expiry.

Single-segment trips use a simple two-column view and short context note. Multi-segment trips preserve independent cell states. Absent, null-status and explicitly missing cells render `MISSING`, never `AVAILABLE`; the M14A completeness warning and supplied inventory counts remain intact.

## 7. Passenger / manifest

Compact rows remain grouped by pickup/dropoff. Seat and booking/ticket identifiers, actual seat passenger information, explicitly labeled booking contact and phone, stops/times, and booking/payment badges have separate visual columns. Mobile uses a compact two-column arrangement. Missing passenger names remain `Chưa có thông tin riêng`; contact or ticket names are not substituted as actual passenger identity.

## 8. Automated checks

- `npm test` in `frontend`: PASS, 73 tests, zero failures/skips (65 existing + 8 new).
- `npm run build` in `frontend`: PASS, TypeScript and production Vite build. Existing Zod annotation and bundle-size warnings remain.
- `git diff --check`: PASS. Git emits line-ending conversion notices on existing P0 files, not whitespace errors.
- The sandbox initially prevented esbuild from traversing the parent directory to resolve Vite configuration. The ordinary build succeeded when rerun with approved escalation; no tooling/package change was needed.
- Backend suite not rerun: P1 makes no backend changes.

New automated coverage includes replacement checkout history, explicit payment detail navigation, account/journey-scoped recovery, malformed storage, all five physical seat semantics, contact/drawer mapping, booking row hierarchy, filter serialization/pagination/reset defaults, and missing occupancy rendering. Existing tests continue covering geometry, reused seats across segments, inventory completeness, authorization, and manifest identity distinctions.

## 9. Browser / rendered verification

Used headless installed Microsoft Edge and the Vite development server, with synthetic frontend adapter fixtures. No live backend booking or payment was created by this verification. The original real Trip #1208 was not reverified; equivalent fixture Trip #7 and booking `BG-FIXTURE-101` exercise the same frontend contracts and two-seat workflow.

At each of **390px, 820px and 1440px**, the browser driver:

1. Opens search, selects A01/A02 and creates a pending booking through the actual customer components.
2. Checks browser Back returns to search, Forward restores payment, and the explicit payment link reaches booking detail.
3. Revisits the original trip, checks recovery context and that booked inventory remains disabled, and resumes payment.
4. Confirms simulated payment and checks two ticket codes.
5. Opens the same booking's operator seat board, confirms two booked cells and different computed booked/available backgrounds, and inspects its drawer.
6. Checks operator list and detail, occupancy with two booked cells plus one missing cell, and a two-row manifest.
7. Operates all five booking filters and supported staff trip filters, checks their URL values, and verifies actual reset buttons clear the query string.
8. Exercises a separate **60-seat × 6-segment** stress matrix: 60 rows, 72 missing cells, vertical internal scrolling at every width, horizontal scrolling at narrow widths, sticky segment header and sticky seat labels.
9. Records no uncaught browser page errors and saves rendered screenshots for inspection.

Browser results: `.tools/m15-p1-render/results.json`. Screenshots include `*-search.png`, `*-payment.png`, `*-tickets.png`, `*-seats.png`, `*-drawer.png`, `*-bookings.png`, `*-booking-detail.png`, `*-occupancy.png`, `*-manifest.png`, `*-trips.png`, and `*-matrix-stress.png` for all three widths. Screenshots were inspected and used to refine aisle density, badge specificity, image height and manifest hierarchy.

To repeat: start `npm run dev` in `frontend`, then run `node tests/m15-browser-check.mjs` with Playwright available. On this machine `PLAYWRIGHT_MODULE` can point to `C:/Users/Giang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright`. The driver uses the installed `msedge` channel. Development fixtures are not included in the production entrypoint.

## 10. Remaining awkwardness / limits

- The booking table can show only a seat count until detail is opened because the list API does not return codes. Long stop names and full Vietnam timestamps still wrap in narrow rows.
- Inline booked-seat contacts require one cached detail request per distinct booking; a very busy board can make several requests. No additional backend endpoint was introduced.
- The exact-journey recovery hint is limited to this browser tab and the latest matching completed booking-create flow. My bookings remains recovery across devices/tabs.
- Wide physical layouts and large matrices intentionally scroll locally on small screens. Long contact names are truncated in cells and fully available in the drawer.
- Live server consistency for Trip #1208 remains covered by prior P0 rehearsal evidence, not this fixture-based P1 browser run.

## 11. Deferred V2

Customer Home remains unchanged and may be sparse in V1. Richer Home content, destination/editorial sections and personalized recommendations are deferred to V2. No new cancellation, inventory release, backend booking/payment capability, admin redesign or global customer redesign was added.

## 12. Final `git status --short`

Includes both preserved P0 changes and P1 changes. Captured below after the final verification.

```text
 M README.md
 M backend/src/main/java/com/busgo/admin/AdminOperatorQueryRepository.java
 M backend/src/main/java/com/busgo/booking/repository/OperatorBookingQueryRepository.java
 M backend/src/main/java/com/busgo/demo/DemoDataSeeder.java
 M backend/src/main/java/com/busgo/operator/OperatorStaffQueryRepository.java
 M backend/src/main/java/com/busgo/trip/operations/OperatorOccupancyQueryRepository.java
 M backend/src/main/resources/application-demo.yml
 M docs/api-contract.md
 M docs/demo-data.md
 M docs/development-plan.md
 M frontend/src/api/errors.ts
 D frontend/src/api/healthApi.ts
 M frontend/src/features/auth/AuthProvider.tsx
 M frontend/src/features/customer/customer.css
 M frontend/src/features/operator/TripWorkspace.tsx
 M frontend/src/features/operator/dispatch.ts
 M frontend/src/features/operator/operations.ts
 M frontend/src/features/operator/operator.css
 M frontend/src/features/operator/shared.tsx
 M frontend/src/pages/BookingPage.tsx
 D frontend/src/pages/FoundationPage.tsx
 M frontend/src/pages/PaymentPage.tsx
 M frontend/src/pages/TripPage.tsx
 M frontend/src/pages/operator/OperatorBookingsPages.tsx
 M frontend/src/pages/operator/OperatorSeatsPage.tsx
 M frontend/src/pages/operator/OperatorTripOperationsPages.tsx
 M frontend/src/pages/operator/OperatorTripsPages.tsx
?? backend/src/main/java/com/busgo/common/time/JpaJdbcTime.java
?? backend/src/test/java/com/busgo/JpaJdbcTimeIT.java
?? backend/src/test/java/com/busgo/demo/DemoDataSeederIT.java
?? docs/final-demo.md
?? docs/m15-p1-verification.md
?? docs/m15-verification.md
?? frontend/src/features/booking/recovery.ts
?? frontend/tests/fixtures/m15-browser.html
?? frontend/tests/fixtures/m15-browser.tsx
?? frontend/tests/fixtures/m15-matrix-browser.html
?? frontend/tests/fixtures/m15-matrix-browser.tsx
?? frontend/tests/m15-browser-check.mjs
?? frontend/tests/m15-ux.test.mjs
```
