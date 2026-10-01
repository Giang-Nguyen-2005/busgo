# M14B customer and system-admin UX verification

Verified 2026-10-01 on `feature/customer-admin-ux-polish`. Frontend implementation and remaining verification are complete. No commit or push was performed. No live-backend verification is claimed.

## Scope and behavior

- Customer: compact home/search hierarchy without hardcoded route suggestions or invented metrics; URL-backed journey IDs, labels, date and filters; editable search and swap; distinct zero-result and filtered-empty states; scannable time/price cards, truthful illustrative bus imagery, and explicit overnight arrival dates.
- Trip/booking: preserve physical seat floors, rows, columns and gaps; distinguish the five-seat selection limit from unavailable inventory; preserve search context through hold/login/booking; show booking summary before contact fields on mobile; retain countdown and recovery behavior. Booking contacts, seat passenger identity and ticket identity remain distinct.
- Payment/tickets/history: explicit mock-payment disclosure and status-specific actions; repeat ticket visits use a compact heading and shared journey with individual seat QR codes; history distinguishes first-use empty from filtered empty. Pending history leads to detail/review because status alone does not prove current payment eligibility.
- Profile/navigation: customer destinations respect CUSTOMER membership; immutable-email explanation, password visibility and validation, and password-change login feedback. No permission rules or backend mutations were added.
- Admin: independent compact shell; dashboard totals use three existing paginated API requests (all/ACTIVE/INACTIVE); directory uses desktop columns/mobile cards; onboarding has a dedicated page; detail starts with actual overview counts, read-only contacts and staff, explicit contact editing, then consequential status controls. Contact drafts survive background refreshes and have dirty-discard confirmation.
- Cached content remains visible on transient refresh failure with a retry warning. Authorization/not-found failures remain blocking; current-session customer/admin authorization loss follows the existing session-clearing flow. Late responses from replaced sessions are ignored.

## Routes

Added `/admin/operators/new`; legacy `/admin/operators?create=1` redirects there. Updated presentation on `/`, `/search`, `/trips/:tripId`, `/booking`, `/payment`, `/booking-success`, `/my-bookings`, `/my-bookings/:bookingId`, `/profile`, `/admin`, `/admin/operators`, and `/admin/operators/:operatorId`. `/login?passwordChanged=1` displays completion feedback. Existing booking/payment API routes and guards remain authoritative.

## Remaining-verification fixes

1. Scoped `body:has(.customer-shell)` and `body:has(.admin-shell)` minimum-width overrides remove 320px overflow caused by the legacy global body minimum plus scrollbar. Global styles and operator shell CSS remain unchanged.
2. Admin narrow navigation wraps without overlapping the brand. The customer five-seat limit has sufficient selector specificity to remain visually distinct from occupied seats.
3. Mobile seat summaries stay in flow before the map; booking summaries stay before the form. They cannot overlay seats, fields or validation feedback.
4. Shared Field now associates descriptions/errors with inputs and keeps the accessible name stable using a separate label ID. This is a semantic shared-component change, with no operator layout change.
5. Customer mobile FilterPanel explicitly cycles Tab/Shift+Tab at the first/last control. Escape closes the native dialog and restores focus to the trigger. This component is not used by the operator workspace.

## Automated verification

- `npm test` (frontend): **65 passed, 0 failed, 0 skipped**, including the 42 pre-existing tests and 23 M14B tests.
- `npm run build` (frontend): **passed**, TypeScript and Vite. Existing warnings remain for Zod purity annotations and the main bundle over 500 kB (578.88 kB in this build).
- `git diff --check`: passed after documentation updates.
- Diff scope review: no backend, operator/staff page, operator feature, OperatorLayout, global stylesheet, package or lockfile changes. Shared UI changes were reviewed; TripCard/PriceSummary are customer consumers. Existing operator tests pass. A full repeat of M14A browser scenarios was not performed.
- Added tests cover customer-role navigation, journey roundtrip/swap, initial editor values, zero-result context, five-seat limit/geometry, image alt/overnight dates, summary/countdown rendering, payment states, grouped/repeat tickets, identity wording, history empties, profile/password feedback, Field associations, API-derived admin total requests, onboarding route, directory count semantics, stable contact snapshot, read-only overview/staff, and transient-vs-blocking query errors.

## Browser fixture verification

Used `http://127.0.0.1:5173/tests/fixtures/m14b-browser.html` in the in-app Chromium browser. The fixture mounts production route components with a memory router, synthetic auth and an Axios adapter. All records and mutations are local synthetic data; no backend calls, real account creation, payments or credential changes occur.

Responsive sweep: **78 surface/width combinations**, all without document horizontal overflow. Widths: **320, 390, 768, 820, 1024, 1440**, at height 1000; focused mobile interactions also used 390 x 844. Surfaces: home, results, seats, booking, payment, tickets, history, booking detail, profile, admin dashboard, directory, onboarding, operator detail. Final 320px admin recheck measured `scrollWidth = clientWidth = 305` with a 15px scrollbar.

| Area | Browser evidence |
| --- | --- |
| Home/search | Editor, swap, keyboard autocomplete selection; URL request serialization excludes display labels. Empty results retain both long location labels and date. |
| Result cards | Overnight times/date, per-seat price, availability, real vs illustrative image labels; mobile/desktop layout inspected. |
| Seats | Selected five seats; remaining available seats disabled with limit styling, occupied seat retains separate gray styling. Two floors preserve rows 1-4 and columns 1/3. Summary is static on mobile. |
| Booking | Fixture hold proceeds to contact entry. Mobile summary precedes form; active countdown observed decreasing. Required-name error associates with input; exact accessible name stays stable after error and entry recovers. |
| Payment/tickets | PENDING mock confirmation proceeds to tickets; CONFIRMED payment screen offers tickets and no confirm action. Five ticket blocks share one journey. History return shows repeat-visit heading. CANCELLED presentation is covered by automated rendering tests. |
| History | Confirmed ticket action, filtered no-match, and no-bookings states checked. |
| Profile | Read-only email, visibility toggle, empty-password validation/focus, profile save success. Password completion message rendered via fixture logout redirect; no password was entered or changed. |
| Dashboard | 20 total / 17 active / 3 inactive derived from three fixture API pagination totals, despite one visible directory row. Request log confirms page 0, size 1 requests. |
| Directory/onboarding | Desktop/mobile presentation, delayed search request contains `q`, native required-field focus on onboarding code. Dedicated route mounts correctly. |
| Operator detail | Actual count overview, separate contact/staff/status sections; draft remains unchanged when server record refreshes; dirty discard confirmation checked. |
| Staff/status | Read-only staff separates membership status from account lock. Deactivation explains commerce/access consequences without claiming cancellation/refunds; activation confirmation and translated rejection checked. |
| Refresh/focus | Customer results and admin directory retain cached content during synthetic network failure with warning. Filter initial focus, forward/reverse wrap, visible outline, Escape and trigger restoration checked. |

The fixture intentionally simplifies backend semantics: ticket records are returned regardless of booking status, onboarding/contact writes are basic adapter responses, and logout uses the password-completion redirect for presentation verification. Browser outcomes therefore validate UI behavior, not server eligibility, transactions or authorization enforcement.

## File inventory

Created:

- `frontend/src/features/admin/admin.css`
- `frontend/src/features/admin/presentation.ts`
- `frontend/src/features/customer/QueryFeedback.tsx`
- `frontend/src/features/customer/customer.css`
- `frontend/src/features/customer/presentation.ts`
- `frontend/tests/customer-admin-ux.test.mjs`
- `frontend/tests/fixtures/m14b-browser.html`
- `frontend/tests/fixtures/m14b-browser.tsx`
- `docs/m14b-verification.md`

Modified:

- `frontend/src/api/client.ts`
- `frontend/src/api/errors.ts`
- `frontend/src/components/ui.tsx`
- `frontend/src/features/booking/holdStore.ts`
- `frontend/src/features/search/FilterPanel.tsx`
- `frontend/src/features/search/SearchForm.tsx`
- `frontend/src/features/trip/SeatMap.tsx`
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/pages/AuthPage.tsx`
- `frontend/src/pages/BookingDetailPage.tsx`
- `frontend/src/pages/BookingPage.tsx`
- `frontend/src/pages/HomePage.tsx`
- `frontend/src/pages/MyBookingsPage.tsx`
- `frontend/src/pages/PaymentPage.tsx`
- `frontend/src/pages/ProfilePage.tsx`
- `frontend/src/pages/SearchPage.tsx`
- `frontend/src/pages/TicketPage.tsx`
- `frontend/src/pages/TripPage.tsx`
- `frontend/src/pages/admin/AdminPages.tsx`
- `frontend/src/routes/router.tsx`
- `frontend/tests/management.test.mjs`
- `docs/development-plan.md`

## Unverified items and deferred scope

- No live API/database/authentication, real payment, actual password-change session invalidation, onboarding transaction, or cross-account permission verification. No new backend contract or analytics API was required.
- Server hold expiration, seat contention, uncertain network mutation recovery and all business rejection combinations were not exercised end-to-end. Existing behavior remains authoritative; active countdown rendering was checked, not a timed real expiry.
- Fresh first-payment ticket heading is model-tested; the browser sweep had already visited the fixture booking, so the interaction verified repeat-visit behavior.
- Chromium viewport checks are not physical-device, virtual-keyboard, Safari/Firefox, screen-reader, or full WCAG certification. Modern `:has()` support is required for the scoped minimum-width override.
- Backend does not expose authoritative current payability in booking history. Do not infer it from PENDING or add a misleading direct payment action.
- Deferred: analytics, refunds/cancellation redesign, passenger editing, new payment providers, backend/domain/permission changes, operator/staff redesign, framework replacement and bundle optimization.
