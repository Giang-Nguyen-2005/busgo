# BusGo M15.5 — Visual Redesign & Product Polish

Date: 2026-10-02 (Asia/Saigon). Branch: `feature/final-hardening`.

Presentation-only redesign layered over the existing uncommitted M15 P0/P1 work. No commit or push. Backend contracts, permissions, booking/payment/inventory behavior and route definitions were not changed by M15.5. The backend changes in the final working-tree status predate this visual pass.

## 1. Design system

Added `frontend/src/design-tokens.css` with the requested primary, accent, background, surface, text, border and semantic color properties. Added spacing, radius, shadow, typography and transition scales. Existing short CSS variable names resolve to the semantic tokens. Shared buttons, inputs, notices and surfaces use these foundations; shell-specific rules keep customer, operator and system admin visually distinct.

## 2. Palette

- Brand teal: `#0F8B83`; dark teal `#0B5F5A` for readable foregrounds and primary controls.
- Structural dark: `#163B3A`; system admin uses a quieter navy `#1C3442` / `#172B36`.
- Orange highlight: `#F59E42`; coral foreground/CTA `#B94B24` on warm `#FFF0E2` surfaces. The darker coral improves contrast compared with using bright orange for small text.
- Backgrounds: `#F5F7F4`, `#EEF3F1`; white elevated surfaces.
- Text: `#1F2D2B`, secondary `#4E625E`, muted `#60716D`.
- Green, amber, red and blue have paired soft surfaces. Available, booked, held, blocked and missing states retain explicit labels.

## 3. Typography

Defined display, page, section, card, body, metadata, label and numeric sizes. Customer hero copy is larger; operational titles stay compact. Departure times, prices, totals and summary counts use tabular numerals. Important amounts use coral; metadata uses quieter weight and color.

## 4. Customer redesign

Compact branded header, orange active-navigation accent, rounded account controls and separated footer. The existing travel image sits under a dark hero gradient with high-contrast white/orange copy. The search form overlaps the hero with stronger elevation. Existing booking guidance is numbered and separated from the account/service explanation. No invented promotions, reviews, routes, ratings or destination records were added.

## 5. Operator redesign

Dark dispatch sidebar with existing Lucide icons, clear active navigation, role/user footer and a compact profile popover. Pale workspace, stronger departure-time hierarchy, compact filter toolbars, restrained table row striping/hover and aligned amounts. Trip workspace uses a dark route/time header, real occupancy counts and an orange active tab. Desktop header was tightened after screenshot review. Booking detail groups summary and contact information; manifests emphasize seat and contact and avoid nested white panels. Lifecycle controls remain role-gated.

## 6. Admin redesign

Distinct navy structural shell, icon-supported navigation, compact summary totals, clean operator directory, grouped detail counts and a restrained red destructive-status section. Existing onboarding and status behavior remain intact; no analytics were introduced.

## 7. Seat board

Preserved row/column/floor geometry and aisles. Rounded seat objects have a heavier lower edge, semantic fills and readable seat codes. Available is green, booked blue, held amber, blocked gray and missing patterned amber. Inspected seats get a strong outline. Existing contact name/phone details remain visible, with long names clamped and full information in the drawer. Floor panels and legends provide visual grouping.

Drawer content is separated into booking/status, payment, journey, contact, and seat/ticket groups. A sticky dark heading keeps Close available while the body scrolls.

## 8. Search/results

Desktop results use a large left image, central departure/arrival timeline and metadata, and a separate warm price/CTA column at wide widths. Tablet results retain the left image with the action row below the trip information. Mobile uses a compact image strip above time, operator and price/action content. Existing fallback images and their accessible alternatives are preserved.

## 9. Checkout/tickets

Existing seat, contact, payment and ticket steps share progress styling, tinted journey summaries and warm total blocks. Payment has a clear amount and semantic status banner. Electronic tickets use a dark seat/passenger section, a dashed split with cutout details, and an unobstructed QR on white. QR payloads and ticket data are unchanged. The selected-seat hover contrast issue found in review was corrected.

## 10. Responsive results

Rendered at **390, 820 and 1440 pixels**. No document-level horizontal overflow in the captured scenarios. Filters wrap, customer summaries stack on narrow screens, operator/admin navigation collapses, floors retain geometry, and large matrices scroll within their own region. Single-segment occupancy wraps its header and uses a simpler two-column presentation. Detailed operational tables retain local horizontal scrolling where necessary.

## 11. Accessibility verification

- Existing labels, status text, selected-state semantics and native controls remain present.
- Added operator skip link and decorative icon `aria-hidden` attributes.
- Selected customer seats have a check mark; unavailable and missing states have patterns as well as text/semantics.
- Drawer opens with Close focused; Tab does not reach background page controls. Edge may briefly report `body` while keyboard focus traverses browser chrome, then returns to the native modal.
- Drawer scrolls vertically, has no internal horizontal overflow, and keeps its heading available. Close and Escape restore focus to the inspected seat; Enter reopens it.
- Reduced-motion emulation verifies the drawer animation and seat transition durations are below 1ms.
- Responsive operator/admin menu visibility was checked; staff has no lifecycle mutation control. Admin lifecycle confirmation was opened and dismissed without changing trip state.
- This is targeted rendered/keyboard verification, not a complete screen-reader or WCAG conformance audit.

## 12. Screenshots and manual review

[Open screenshot gallery](../.tools/m15-5-render/index.html). Images and logs are local ignored artifacts under `.tools/m15-5-render/`.

At each of 390 / 820 / 1440:

| Area | Captures |
| --- | --- |
| Customer | `home`, `search`, `customer-seats`, `booking`, `payment`, `tickets` |
| Operator | `operator-dashboard`, `trips`, `seats`, `bookings`, `booking-detail`, `drawer`, `drawer-bottom`, `occupancy`, `manifest` |
| System admin | `admin-dashboard`, `admin-operators`, `admin-detail` |
| Additional checks | `operator-admin-workspace`, `lifecycle-dialog`, `matrix-stress` |

Filename pattern: `<width>-<capture>.png`. This is **63 PNG captures**, including all required screens at 390 and 1440, plus the 820 review. Production React pages were rendered against the existing isolated synthetic API fixture. Fixture data is explicitly marked; these captures do not establish live backend consistency. Screenshots were manually inspected, including hero contrast, selected seats, drawer content, checkout totals, QR clearance, matrix scrolling, role shells and long Vietnamese labels.

## 13. Tests/build/diff

- `npm test`: **73 passed, 0 failed**.
- `npm run build`: **passed** (TypeScript and Vite production build).
- Vite retains the existing advisory for the main JavaScript chunk above 500 kB (approximately 579 kB minified / 185 kB gzip).
- `git diff --check`: **passed**. Git prints LF-to-CRLF advisory messages for several files; no whitespace errors.
- Extended browser driver: `frontend/tests/m15-5-browser-check.mjs`. Results: `.tools/m15-5-render/results.json`.
- Browser journey covers search → two selected seats → contact → pending payment → Back/Forward and recovery → successful simulated payment/tickets → consistent operator booking, seat, occupancy and manifest views, plus admin directory/detail and matrix stress.
- Backend tests were not rerun because this pass changes frontend presentation only.

Reproduce with Vite running on port 5173, then in `frontend`:

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/Users/Giang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'
node tests/m15-5-browser-check.mjs
npm test
npm run build
git diff --check
```

## 14. Remaining visual weaknesses

- Long contact names and stop labels still make some mobile operational views tall. Full details remain accessible in the drawer/local table scroll.
- Native lifecycle confirmation dialogs retain their basic top-left placement; controls remain readable and keyboard-accessible.
- The operational dashboard is intentionally sparse with small data sets; no invented operational counts were added to fill space.
- Existing fallback coach imagery is illustrative, and portrait crops cannot show the entire vehicle.
- Existing legacy CSS remains beneath scoped visual overrides. The token layer is coherent, but the whole historical stylesheet has not been refactored.

## 15. Deferred V2 work

Potential follow-ups, not implemented: broader CSS consolidation/component extraction, main-bundle splitting, real operator-managed vehicle photography, exhaustive screen-reader/contrast audits, and a fuller live-data visual regression suite. New features, analytics, routing changes and backend work remain out of scope.

## 16. Final working-tree status

All pre-existing M15 changes remain uncommitted. The status below includes that work and M15.5; it is not a list of changes made exclusively by this redesign.

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
?? frontend/src/design-tokens.css
?? frontend/src/features/booking/recovery.ts
?? frontend/tests/fixtures/m15-browser.html
?? frontend/tests/fixtures/m15-browser.tsx
?? frontend/tests/fixtures/m15-matrix-browser.html
?? frontend/tests/fixtures/m15-matrix-browser.tsx
?? frontend/tests/m15-5-browser-check.mjs
?? frontend/tests/m15-browser-check.mjs
?? frontend/tests/m15-ux.test.mjs
```
