# M29.2 — Customer experience implementation

**M29.2 COMPLETE — ready for human review, uncommitted.**

## Scope and baseline

Implemented in the real React frontend, starting from verified local `main` at `8873dfc` (M29.1). Implementation branch: `feature/v2-m29-customer-experience`. No commit, push, merge, reset, or Git configuration change. The pre-existing untracked `tatus --short` is untouched. No repository AGENTS.md was found in the checkout or applicable parent directories.

## Screens and design choices

- **Home:** V3 editorial headline, teal structure and warm accent, existing local `/images/busgo/hero-coach.jpg`, prominent search, distinct discovery sections, Lucide route/operator cues, useful empty states and retry. Public operators, routes, upcoming trips and reviews still use real API results. No invented popularity, fares, ratings or availability. The simulated-payment disclaimer remains visible.
- **Search:** compact journey editor, server result count, all six supported sorts, individual removable filter chips and clear-all. Desktop sidebar and mobile modal sheet share the same supported fields. MapPin, Navigation, CalendarDays, ArrowLeftRight and Search are composed within the search controls.
- **Trip cards:** compact customer-scoped layout, real operator/rating, schedule, duration, route, bus type, available count, price and action. Existing real-image/fallback behavior retained. Scheduled times are labelled; delayed estimates retain day changes and operational labels. Missing optional estimates show “Đang cập nhật” rather than crashing date formatting.
- **Seat selection:** compact journey heading, operational notices, availability/legend and map before itinerary. Optional floor navigation retains each seat's real floor/row/column and selection across floors. Mobile summary/action stays accessible at the bottom; geometry can scroll locally on wide buses. Operator and modification usages retain the prior multi-floor presentation by default.
- **Checkout:** contact first on mobile, hold countdown above contact fields, journey/voucher/fare summary, final action explicitly linked to the contact form. Mobile sticky total/action; desktop summary alongside the form. Actual quoted gross, discount and net remain visible.
- **Payment:** prominent amount, explicit simulated-payment heading, deadline treatment, labelled illustrative QR, pending/confirmed/unpayable actions, recovery messages. No real-payment claim.
- **E-tickets:** dark valid-ticket heading, clear seat/code/QR hierarchy, separate muted/red invalid ticket treatment. VOID or absent QR data never renders a boarding QR.
- **My Bookings:** shared card hierarchy, price treatment, scrollable status controls and mobile information spacing. M29.1 skeleton, status URL filters, pagination and authenticated access remain intact.

V3's typography, teal buttons, restrained borders, whitespace and short motion are carried into the existing tokens and shell. The prototype's fictional results and conceptual controls were not copied. Local travel artwork replaces the prototype illustration. No new dependencies or remote assets were introduced.

## Search and filter behavior

The existing `/trips/search` API supplies the full filtered count and paginated results. The UI sends `operatorId`, `busTypeId`, `minRating`, `minSeats`, `minPrice`, `maxPrice`, `departureFrom`, `departureTo`, `sort`, and pagination unchanged. Supported sort values remain RECOMMENDED, RATING_DESC, DEPARTURE_ASC, DEPARTURE_DESC, PRICE_ASC and PRICE_DESC. Changes reset page; browser back/forward restores URL state.

Mobile edits are drafts. Apply validates and updates the URL. Reset clears the draft, requiring Apply; Escape/close dismisses without applying, and reopening restores applied values. Applied chips remove one condition immediately; clear-all removes all filters. The native dialog contains focus; Tab/Shift+Tab loop, Escape and close return focus, scroll locking restores the previous body overflow. The body scrolls independently of visible footer actions. Reopening starts at the top with the close control focused.

Autocomplete retains selected IDs and labels together on swap. Typing invalidates IDs. During the debounce window, previous-query suggestions cannot be accepted by Enter. Keyboard navigation scrolls the active option into view. Departure dates still use the Vietnam-time `today()` utility.

Initial loading uses the M29.1 skeleton on discovery/search. Initial errors no longer claim previously loaded data exists. Existing cached-data/refetch feedback remains when data can actually be displayed.

## Safety invariants

No backend, database, API contract, authorization, inventory algorithm, financial workflow, support/dispute rule, dashboard or Dispatch Board changes.

Seat limit, IDs/segments, selection restoration and revalidation, polling, account eligibility, hold creation, hold conflict handling, expiry redirect, booking recovery, contact schema, voucher quote mutation, booking creation and payment mutation are retained. Local seat totals remain estimates until a server hold. No local voucher allocation is calculated. The submit disabled conditions remain attached to the actual submit control. Payment stays on the existing mock-confirm endpoint. Ticket QR contents come only from the API; invalid/missing QR conditions remain unchanged.

## CSS and shared components

`features/customer/customer.css` consolidates the previous conflicting customer overrides into one scoped stylesheet. All selectors are under `.customer-shell`, apart from the pre-existing customer-only body minimum-width exception. Global `styles.css`, M29.1 tokens, shell components and operator/admin CSS are untouched. TripCard and Journey consumers were inspected; TripCard's schedule wording is appropriate to its customer/marketplace usages. SeatMap's new navigation is opt-in from TripPage so operator assisted booking is unaffected. Reduced motion disables customer transitions and drawer animation.

## Verification

- `npm run build` in `frontend`: PASS, including TypeScript and Vite production build. Existing warnings about Zod comment annotations and Notifications static/dynamic imports remain. The first sandboxed Vite build failed on Windows `realpath` permissions; the authorized rerun passed.
- `npm test` in `frontend`: PASS, 154 tests, 0 failures. The old mobile summary-before-form assertion was updated to the approved form/summary/confirm order and strengthened with explicit form association/order checks. Two focused tests cover opt-in floor navigation/shared defaults and delayed/missing estimated timestamps. No existing business assertion was removed.
- `git diff --check`: PASS.
- Browser: PASS — 23 acceptance groups, 39 screenshots, zero uncaught page errors, zero unexpected console errors and zero unhandled API requests. Expected HTTP error logs originate from deliberately failed fixture responses. See `../.tools/m29-browser/m29-2/acceptance.json` for the final machine-readable results and checks. Tests drive the real React router with Edge/Playwright and synthetic API responses. **No live backend integration was run or claimed.** Initial sandboxed Edge/Vite isolation required authorized browser/server execution outside the sandbox.

Browser coverage includes homepage/discovery, autocomplete/swap/invalid input/date, advanced filter payloads, draft apply/reset/cancel, focus/scroll lock, sort/pagination/back-forward, empty/error/retry/loading, one/two-floor seat maps, maximum five, login return/restoration, polling inventory change, seat conflict, expired hold, contact validation, quoted discount, booking/payment error recovery, simulated success, invalid tickets, My Bookings and reduced motion. Representative screen matrix: 1440×900, 834×844, 390×844, 360×844. Measurements assert no document-level horizontal overflow. Page/console runtime errors and unknown API calls are recorded by the suite.

### Reproduce browser acceptance

Start Vite in `frontend` with `npm run dev -- --host 127.0.0.1 --port 5190`, then from the repository root run `node frontend/tests/m29-customer-browser.cjs`. `BUSGO_TEST_ORIGIN` overrides the origin. `PLAYWRIGHT_MODULE` can point to an installed Playwright package; the default uses this workstation's existing bundled runtime. No package installation is required here. `--interactions-only` is a diagnostic subset; the final report uses the full run. Fixtures use future January 2027 travel dates and relative hold expirations; refresh the fixed travel dates when rerunning after that date.

### Evidence locations

All evidence is under `.tools/m29-browser/m29-2/` (the repository already ignores `.tools/`).

- `review.html` screenshot index; `acceptance.json`, `build.log`, `tests.log`.
- `{home,search,seats,checkout,payment,tickets,bookings}-{1440,834,390,360}.png`: the 28-screen viewport matrix.
- `filter-drawer-360.png`, `search-empty-360.png`, `search-error-360.png`, `search-loading-360.png`.
- `seats-selected-360.png`, `seats-one-floor-360.png`, `seats-live-notice-360.png`.
- `checkout-voucher-360.png`, `payment-success-360.png`, `home-empty-360.png`, `home-error-360.png`.

All pictured fares, people, ratings, bookings and QR payloads are synthetic test fixtures, not production records or working tickets.

## Exact changed-file inventory

1. `frontend/src/components/ui.tsx`
2. `frontend/src/features/customer/customer.css`
3. `frontend/src/features/marketplace/Marketplace.tsx`
4. `frontend/src/features/search/FilterPanel.tsx`
5. `frontend/src/features/search/SearchForm.tsx`
6. `frontend/src/features/trip/SeatMap.tsx`
7. `frontend/src/pages/BookingPage.tsx`
8. `frontend/src/pages/HomePage.tsx`
9. `frontend/src/pages/MyBookingsPage.tsx`
10. `frontend/src/pages/PaymentPage.tsx`
11. `frontend/src/pages/SearchPage.tsx`
12. `frontend/src/pages/TicketPage.tsx`
13. `frontend/src/pages/TripPage.tsx`
14. `frontend/tests/customer-admin-ux.test.mjs`
15. `frontend/tests/m29-customer.test.mjs` (new)
16. `frontend/tests/m29-customer-browser.cjs` (new)
17. `docs/m29-customer-experience.md` (new)

Generated evidence is separately enumerated above; build outputs remain ignored. `presentation.ts`, BookingSummary, PromotionPrice, design tokens, auth/recovery utilities and API modules were inspected and reused without changes.

## Limitations and deferred work

- Live backend/auth/payment integration was not exercised; acceptance uses deterministic synthetic responses in a real browser.
- Browser coverage is desktop Edge with responsive viewports, not physical iOS/Android devices, every zoom level or a screen-reader certification.
- Backend-provided images, unusually long route names and bus geometries beyond the fixture set may warrant additional data-driven review. Wide physical seat layouts use local horizontal scrolling rather than moving seats.
- Existing build warnings described above remain outside this scope.
- M29.3/M29.4 dashboard, operator/admin workflow and Visual Dispatch Board work is deferred. None was started.

Stop at M29.2 for human review. No commit or push.
