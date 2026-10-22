# BusGo M29.1 — UI foundations

## Status and scope

**COMPLETE for the M29.1 frontend scope**, validated on 22 October 2026 (Asia/Saigon).

Baseline was verified as `main` at `0c57d8e`, with no tracked changes and only the historical untracked `tatus --short`. Implementation remains uncommitted on that branch. No M29.2–M29.5 work was performed.

Approved reference: `.tools/m29-design-prototype/m29-final-visual.html` (M29 V3 — Premium Modern SaaS + Travel Booking), with `V3-HANDOFF.md`. The Phase 0 review and V2 prototype were inspected for context. V3's structural teal, restrained hierarchy, icon vocabulary and motion informed these production React components. Its proposed dashboards, homepage and Dispatch Board were deliberately not copied.

Browser evidence uses the **real React application, production router and guards**, served by Vite, with synthetic API responses supplied by Playwright. These are not screenshots of the prototype. No live backend was available or started; live authentication-server and business-transaction integration acceptance is **not claimed**. This limitation does not replace the existing unit/regression tests or authorize deployment.

## Design decisions

- Extend `design-tokens.css`; preserve all existing variable names and the legacy aliases in `styles.css`.
- Structural surface `#102F2E`, primary action `#0F766E`, hover/dark teal `#115E59`, highlight `#14B8A6`, canvas `#F6F8F7`, white surfaces, warm brand accent `#D6A76B`.
- Preserve existing success/warning/danger/info treatments and legacy orange accents used by business pages. Warm gold and bright teal decorate the brand, active indicators and dark-surface focus; they are not replacements for status foreground colors.
- Keep existing spacing/radius/shadow scales. Add a 252px sidebar, 72px topbar minimum, 1500px workspace content maximum and responsive 16–32px gutters.
- Keep the local Inter/Segoe UI/Arial/system fallback stack; no external fonts or packages. Body text remains 15px, shell navigation 14px, secondary shell text 12–13px, with tabular numbers.
- Preserve shared `.button`, `.secondary`, `.card`, `.field`, select and domain-badge contracts. Add explicit tertiary/destructive styles, unify focus rings, retain visible validation, and ensure shared icon controls have 44px targets. No status meaning or labels changed.

## Shared components

| Component | Contract and use |
| --- | --- |
| `IconButton` | Required accessible label; defaults to `type="button"`; forwards disabled, expanded and controls attributes. Used by shell menu/close controls. |
| `Drawer` | Native modal dialog with labelled heading, close button, Escape, backdrop dismissal, explicit Tab/Shift+Tab wrapping, native background inertness, body scroll locking/restoration and focus return. Closes when resizing to desktop. |
| `PageHeader` | Title, optional description and actions. Existing `OperatorPageHeader` delegates to it without changing callers. |
| `PageContent` | Animates existing content DOM on pathname changes. Does not key/remount the outlet, change URLs, reset queries or implement new scroll behavior. |
| `LoadingIndicator` | Accessible status text plus decorative spinner. Existing `Loading()` export remains compatible. |
| `Skeleton` / `LoadingSkeleton` | Static decorative line, heading, panel and circle blocks; list/table/summary/panel arrangements with a single loading description. No fake numbers or business statuses. |
| Existing `Empty`, `ErrorState`, `Field`, domain badges | Public contracts retained, including child recovery links, retry buttons, validation associations and semantic status text. |

`MyBookingsPage` is the single initial-loading demonstration for `LoadingSkeleton`. Existing React Query branching, cache keys, stale-data handling, errors and refetch behavior remain unchanged. No mass migration of loading states was attempted.

## Shells and icons

`WorkspaceShell` shares the structure for Operator and System Admin. Each retains its distinct workspace title, role, account identity and original authorized routes. Desktop sidebars scroll independently; active navigation uses text weight and a teal edge indicator. Topbars show workspace/current section, account details, customer-area access and notifications where originally authorized.

Operator menus still derive from `operatorNavigation()` and `canManageOperator()`. The four existing groups are retained. The explicit icon map covers every returned destination; there is no generic bus fallback. Staff retain their existing eight destinations and do not gain finance, fleet management or staff-administration entries. System Admin retains exactly its six existing navigation destinations.

Navigation uses Lucide 18px icons with consistent stroke treatment; menu/close controls use 20px icons. Customer navigation has home/application/ticket/support icons. No extra icon package or emoji navigation was introduced.

Customer retains authentication presentation, notification access, profile/management access, login/register/logout, operator applications and all footer wording, including simulated-payment and Vietnam-time notices. Its header remains sticky. At small widths the drawer contains the account actions and important links that no longer fit inline.

All shells use the same 1050px mobile/tablet boundary. Drawers overlay the current page instead of expanding a long menu above it. Navigation dismisses the drawer, location changes also close it, and background overflow and focus are restored. Native account disclosures support Escape, outside-pointer dismissal and route-change dismissal.

## Motion and loading

- Existing hover/press transitions use the 140ms token.
- Page entrance uses 180ms, with a restrained 4px translation and partial-opacity fade on the content only.
- Drawer entrance uses 220ms. Dismissal is immediate and does not hold focus or navigation for an exit animation.
- Reduced motion disables drawer/spinner animation and bypasses page entrance; changing the preference while an entrance runs cancels it.
- `RouteProgress` waits 180ms before displaying its indicator, and cancels that timer when navigation completes. The delay never blocks page rendering. Fast cached transitions do not flash a global loading indicator.
- Skeletons are static, avoid animation distraction and never replace stale-but-valid booking data.
- Existing customer pathname scroll-to-top behavior is preserved; no new history or scroll policy was introduced.

## CSS ownership and remaining debt

`shells.css` owns the new `ws-*`, `customer-*` shell elements. `foundations.css` owns `app-*` primitives. Retired shell selectors and their responsive overrides were removed from the old global/customer/operator/admin styles. Existing outer `.customer-shell`, `.operator-shell` and `.admin-shell` classes remain for page-specific consumers.

Page business styling stays in place: seat maps, booking/payment status, finance and support were not restyled or rewritten. The new outlet wrapper's immediate-card spacing is explicitly handled in the shared workspace stylesheet. No blanket `!important` migration stylesheet was introduced; existing global reduced-motion fallbacks remain.

Deferred CSS debt includes historical page-level repeated rules, some small operational labels, hardcoded colors and old business-component selectors. Finance/support retain their current local control and table styles. These need contextual review in later phases, not a global selector sweep. Legacy orange business accents remain intentional during this controlled migration.

## Validation

### Build and regression tests

- `npm run build`: **PASS** — TypeScript and Vite production build; final Vite time 8.18s.
- `npm test`: **152 passed, 0 failed, 0 skipped** (146 existing plus 6 new foundation tests).
- `git diff --check`: **PASS** (Git emits normal LF/CRLF conversion notices).
- Initial build attempts exposed an unused icon import/customer drawer wiring issue; these were fixed. A later Vite `realpath` EPERM was a Windows sandbox restriction; the final build ran successfully with approved local access.
- No backend/database suites were run.

New tests validate exact role-derived navigation in both desktop and mobile markup, icon coverage, System Admin identity/destinations, customer/guest account routes, accessible loading descriptions, icon control labels and retained field/error/empty recovery contracts.

### Browser acceptance

Headless Microsoft Edge / Playwright against `http://127.0.0.1:5188`:

- **36 page/viewport combinations passed**: nine real routes at 1440×900, 834×844, 390×844 and 360×844.
- Routes: customer bookings and trip seat selection; Operator Admin overview, bookings and finance; Operator Staff overview and bookings; System Admin overview and disputes.
- **Four role interaction suites passed**: keyboard opening, 25 consecutive Tab checks, Escape, focus return, scroll locking/restoration, close button, backdrop dismissal, navigation dismissal, back/forward, desktop resize dismissal, reduced motion, account/logout and notification access where permitted.
- **Five loading/navigation scenarios passed**: labelled initial skeleton; delayed route progress versus fast navigation; retained header DOM; stale-data error with recovery; blocking error/retry; guest mobile login/register navigation (related assertions grouped into five scenarios).
- **Five additional checks passed**: layout stability while opening each of three shell types, direct staff finance-route denial, and System Admin/customer SPA switching with isolated styles and sticky customer header.
- Final primary run: **0 page/console errors, 0 unmatched API fixtures, 0 document-level horizontal overflows**.
- A focus-containment issue discovered during testing was fixed in `Drawer`; no known blocking frontend defect remains in these tested paths.

50 actual screenshots and machine-readable results are in `.tools/m29-browser/` (ignored, not staged):

| Evidence | Files |
| --- | --- |
| Customer | `customer-{1440,834,390,360}.png`, `customer-seats-{1440,834,390,360}.png` |
| Operator Admin | `operator-{1440,834,390,360}.png`, `operator-bookings-{1440,834,390,360}.png`, `operator-finance-{1440,834,390,360}.png` |
| Operator Staff | `staff-{1440,834,390,360}.png`, `staff-bookings-{1440,834,390,360}.png` |
| System Admin | `admin-{1440,834,390,360}.png`, `admin-disputes-{1440,834,390,360}.png` |
| Focus/drawers | `{customer,operator_admin,operator_staff,system_admin}-drawer-focus-360.png`, `guest-drawer-360.png` |
| Accounts/notifications | `{operator_admin,operator_staff,system_admin}-account-390.png`, `{customer,operator_admin}-notifications-390.png` |
| Loading/errors | `customer-loading-390.png`, `customer-error-390.png`, `customer-stale-error-390.png`, `route-progress-390.png` |
| Reports/logs | `acceptance.json`, `loading-acceptance.json`, `extra-acceptance.json`, corresponding `.log` files, `build.log`, `tests.log` |

Representative desktop/customer-seat/mobile-finance/admin-dispute/focus/loading screenshots were visually inspected and compared with the approved V3 structural direction. Finance still clearly distinguishes source 650,000 VND from posted 0 VND in its test fixture. Customer seat states and operational booking/payment states retain their existing presentation.

### Reproduction

Use the existing Playwright installation; no dependency changes are required. Start the frontend with `npm run dev -- --host 127.0.0.1 --port 5188`. From the repository root, set `PLAYWRIGHT_MODULE` to the locally available Playwright module directory, then run:

```powershell
node frontend/tests/m29-browser-check.cjs
node frontend/tests/m29-loading-browser-check.cjs
```

`BUSGO_ORIGIN` can override the test origin. The synthetic response definitions live in `frontend/tests/fixtures/m29-api.cjs`; they are test-only and never bundled into the production application. Supplemental local cross-workspace checks are saved in `.tools/m29-browser/extra.cjs`.

### Limits

Live-backend login, real notification delivery, bookings, payments and database state were not exercised; no production-readiness claim is made for those integrations. Browser coverage is Edge on Windows, not Safari/iOS, Firefox, screen-reader certification or physical touch-device testing. Visual comparison is manual, not pixel-diff automation. Existing business-page density and small labels are deferred, as required by this phase.

## Exact source/test/document inventory

Modified existing files:

- `frontend/src/components/RouteProgress.tsx`
- `frontend/src/components/ui.tsx`
- `frontend/src/design-tokens.css`
- `frontend/src/features/admin/admin.css`
- `frontend/src/features/customer/customer.css`
- `frontend/src/features/operator/operator.css`
- `frontend/src/features/operator/shared.tsx`
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/layouts/OperatorLayout.tsx`
- `frontend/src/pages/MyBookingsPage.tsx`
- `frontend/src/styles.css`

New files:

- `frontend/src/components/foundations/Drawer.tsx`
- `frontend/src/components/foundations/IconButton.tsx`
- `frontend/src/components/foundations/Loading.tsx`
- `frontend/src/components/foundations/PageContent.tsx`
- `frontend/src/components/foundations/PageHeader.tsx`
- `frontend/src/components/foundations/foundations.css`
- `frontend/src/components/foundations/navigationIcons.ts`
- `frontend/src/layouts/WorkspaceShell.tsx`
- `frontend/src/layouts/shells.css`
- `frontend/tests/fixtures/m29-api.cjs`
- `frontend/tests/m29-browser-check.cjs`
- `frontend/tests/m29-foundations.test.mjs`
- `frontend/tests/m29-loading-browser-check.cjs`
- `docs/m29-ui-foundations.md`

## Safety and handoff

Authentication, authorization helpers, route definitions, APIs/payloads, query keys, mutations, financial calculations, backend and database are unchanged. Booking/seat holds/payment/tickets, trip status/segment inventory/boarding, voucher discounts, support permissions and simulated/internal finance meanings remain owned by their existing implementations.

No new Dispatch route or other capability was added. Homepage/search, booking-flow redesign, dashboard composition, finance/support redesign and later M29 phases are deferred.

Final Git state: `main`, HEAD `0c57d8e`; 12 modified tracked files and 14 new implementation/test/document files, all unstaged, plus the untouched historical `tatus --short` (2,945 bytes, original modification time retained). No commit, push, merge, tag, identity or remote change. Generated `.tools` evidence is ignored and not staged.

Stop after M29.1 for human review.

