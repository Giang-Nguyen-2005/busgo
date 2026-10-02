# Final BusGo Operator UX Cleanup

Verified 2026-10-02 on `feature/final-hardening`. Existing M15 P0/P1/M15.5 changes were preserved. No commit or push. This cleanup changes frontend presentation and adds read-only dashboard queries through existing APIs; backend contracts, permissions, booking/payment/inventory logic remain unchanged.

## 1. Files modified by this cleanup

Production:
- `frontend/src/pages/operator/OperatorHomePage.tsx`
- `frontend/src/pages/operator/OperatorBookingsPages.tsx`
- `frontend/src/pages/operator/OperatorTripOperationsPages.tsx` (manifest only)
- `frontend/src/features/operator/operator.css` (scoped cleanup rules)

Verification:
- `frontend/tests/fixtures/operator-cleanup-browser.html`
- `frontend/tests/fixtures/operator-cleanup-browser.tsx`
- `frontend/tests/operator-cleanup-browser-check.mjs`
- `docs/operator-ux-cleanup-verification.md`

Local screenshots, gallery and browser results are under `.tools/operator-cleanup-render/` (ignored artifacts). The final Git status below also includes pre-existing work, including backend changes; those were not introduced by this cleanup.

## 2. Dashboard

Added a daily summary strip, a highlighted boarding/upcoming trip, a readable operating schedule and direct links to seats, passengers, occupancy and trip-filtered bookings. Management links retain their role gate.

All production counts use current contracts: today's trips and status subsets come from the existing day query; highlighted-trip booking count comes from pagination.totalElements and explicitly includes all statuses. Whole-trip availability comes from occupancy, while booked seats count distinct seats booked on at least one segment. Incomplete inventory displays “Chưa đủ dữ liệu”. No revenue, load-factor or trend analytics were fabricated. Only the highlighted trip adds booking and occupancy queries, rather than querying every list row; their independent refresh scope is disclosed.

## 3. Booking list

Three primary zones now separate booking/contact, journey/time/seats, and status/money. Email and creation time remain accessible in a native disclosure. Desktop retains operational table rows; mobile uses compact three-zone cards. Existing filters, pagination, query behavior and links are retained.

## 4. Manifest

Strong, unbroken seat labels lead each row. Identity, phone, pickup/dropoff and text status badges take priority. Booking/ticket details move into a disclosure. Rows share one surface with dividers, reducing nested cards. Missing passenger identity is explicitly described as unavailable; booking contact is labeled as such. Search and journey grouping are retained.

## 5. Booking detail

One grouped dossier replaces similarly weighted cards: overview, journey, contact, seats/tickets, payment and internal metadata. Desktop places journey/contact together; mobile stacks them. Latest payment is prominent, with complete payment history, original ticket details and internal/customer metadata available through disclosures. Useful existing fields are retained.

## 6. Responsive results

Rendered all four screens at 390, 820 and 1440 pixels in headless Edge. Manually inspected the required desktop/mobile screenshots and tablet layouts. Long Vietnamese contact and route labels wrap readably. No document-level horizontal overflow or uncaught browser page errors occurred. Screenshot review caught manifest seat-code wrapping; scoped padding, column width and nowrap rules corrected it, and the full browser matrix was rerun.

## 7. Accessibility

Text accompanies every status color. Existing labels, links, table semantics and global focus styling are retained. Secondary information uses native details/summary; keyboard Enter expansion was verified for booking-row details and payment history. Disclosure controls have a 44px minimum height. This was targeted keyboard/rendered verification, not a full screen-reader or automated WCAG audit. Existing shell drawer/reduced-motion verification is recorded in `docs/m15-5-verification.md`; this cleanup adds no animation and does not change the drawer.

## 8. Screenshots

21 final full-page PNGs: each of the following at 390, 820 and 1440 pixels:
- operator-dashboard
- dashboard-incomplete
- dashboard-empty
- bookings
- booking-detail
- booking-detail-expanded
- manifest

Gallery: `../.tools/operator-cleanup-render/index.html`.
Machine-readable results: `../.tools/operator-cleanup-render/results.json`.

Screenshots render the actual React components with isolated, explicitly synthetic API fixtures, including long Vietnamese labels. They do not represent production records or a live backend integration test.

## 9. Checks

- `npm test` (frontend): 73 passed, 0 failed.
- `npm run build` (frontend): passed TypeScript and Vite production build after the final CSS correction.
- Build advisories remain: vendor Zod PURE-comment annotations and a 579.35 kB main chunk (185.33 kB gzip).
- Browser checks: all three widths passed dashboard count scope, incomplete/empty states, booking filter/reset, keyboard disclosures, truthful contact wording and manifest search, with no page overflow or page errors.
- `git diff --check`: passed.
- Backend tests were not rerun; this task did not change backend code.

## 10. Remaining weaknesses / deferred work

- Long mobile manifests and expanded booking histories still require substantial vertical scrolling. Virtualization, bulk workflows and additional display modes are outside this cleanup.
- The retained mobile trip tab strip scrolls horizontally within its container.
- Dashboard data refreshes independently; it is not an atomic operational snapshot. The UI states the scope, and incomplete inventory is not presented as a reliable seat metric.
- Dense-screen validation used representative fixtures; live operator data, assistive-technology testing and broader content extremes remain useful follow-up coverage.
- Bundle splitting and vendor warning cleanup remain separate performance work.
- Customer screens, seat board, occupancy matrix, admin screens and global design tokens were not redesigned in this cleanup.

## 11. git status --short

Full working-tree status, including preserved earlier work:

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
 M frontend/src/features/admin/admin.css
 M frontend/src/features/auth/AuthProvider.tsx
 M frontend/src/features/customer/customer.css
 M frontend/src/features/operator/TripWorkspace.tsx
 M frontend/src/features/operator/dispatch.ts
 M frontend/src/features/operator/operations.ts
 M frontend/src/features/operator/operator.css
 M frontend/src/features/operator/shared.tsx
 M frontend/src/layouts/AdminLayout.tsx
 M frontend/src/layouts/OperatorLayout.tsx
 M frontend/src/pages/BookingPage.tsx
 D frontend/src/pages/FoundationPage.tsx
 M frontend/src/pages/PaymentPage.tsx
 M frontend/src/pages/TicketPage.tsx
 M frontend/src/pages/TripPage.tsx
 M frontend/src/pages/operator/OperatorBookingsPages.tsx
 M frontend/src/pages/operator/OperatorHomePage.tsx
 M frontend/src/pages/operator/OperatorSeatsPage.tsx
 M frontend/src/pages/operator/OperatorTripOperationsPages.tsx
 M frontend/src/pages/operator/OperatorTripsPages.tsx
 M frontend/src/styles.css
?? backend/src/main/java/com/busgo/common/time/JpaJdbcTime.java
?? backend/src/test/java/com/busgo/JpaJdbcTimeIT.java
?? backend/src/test/java/com/busgo/demo/DemoDataSeederIT.java
?? docs/final-demo.md
?? docs/m15-5-verification.md
?? docs/m15-p1-verification.md
?? docs/m15-verification.md
?? docs/operator-ux-cleanup-verification.md
?? frontend/src/design-tokens.css
?? frontend/src/features/booking/recovery.ts
?? frontend/tests/fixtures/m15-browser.html
?? frontend/tests/fixtures/m15-browser.tsx
?? frontend/tests/fixtures/m15-matrix-browser.html
?? frontend/tests/fixtures/m15-matrix-browser.tsx
?? frontend/tests/fixtures/operator-cleanup-browser.html
?? frontend/tests/fixtures/operator-cleanup-browser.tsx
?? frontend/tests/m15-5-browser-check.mjs
?? frontend/tests/m15-browser-check.mjs
?? frontend/tests/m15-ux.test.mjs
?? frontend/tests/operator-cleanup-browser-check.mjs
```
