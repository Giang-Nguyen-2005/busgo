# M29.4 platform, finance and support — COMPLETE (fixture-based UI acceptance)

## Repository
Baseline HEAD fe9064a9a9b49b88b896839c2e1d9212e1c94e4f; branch feature/v2-m29-platform-finance-support. Continued existing working tree. No commit, push, merge, dependency installation or M29.5 work. Historical `tatus --short` preserved.

## Implemented screens and decisions
- `/admin`: teal overview, separate application/onboarding/operator/supply groups, independent dispute counter queue, real destination links. `GET /admin/dashboard` counts SUBMITTED/UNDER_REVIEW applications, ONBOARDING approved applications whose operator has not onboarded, ACTIVE/SUSPENDED operators, active routes and upcoming scheduled trips. No combined funnel or denominator. Missing values are unknown. `GET /admin/disputes/dashboard` returns authorized status counters independently, with loading/error/retry. Finance attention links to per-operator, per-period reconciliation; no unsupported global exception total or expensive dataset scan.
- Applications: responsive list, status treatment, consistent filter surfaces. Existing detail, history, one-time invitation reveal and mutation payloads retained.
- Operator directory/governance: refined tables/headings/metrics and explicit reason-preserving suspension/reactivation confirmation with cancellation. Existing workflows and API payloads retained.
- Finance: tabs precede summary; source/posted columns and uncertainty panel show actual API totals/counts. Missing count is never inferred by subtraction. Internal liabilities/receivables are labelled as unconfirmed actual payment. All original monetary metrics remain in native expandable details, also used for immutable statement snapshots. Tables have labelled focusable local horizontal scrolling. No accounting calculations changed.
- Support: responsive inbox, distinct public/internal timeline entries, prominent final dispute outcome and additional-handling statement before conversation, independent errors, list retry and corrected checkbox layout. Backend actions remain authoritative; version/requestKey and workspace API bases unchanged. Message text remains escaped. Dispute disclaimer remains visible; no refund/booking controls added.
- Audit: timeline styling and unknown resource type preserved verbatim; existing metadata filters, actor values, Vietnam timestamps and pagination retained.

## Component and CSS structure
PlatformWorkQueue isolates its read request and error state. Summary provides shared source/posted/uncertainty presentation for overview and statement snapshots. Existing shells/foundations, Lucide ShieldCheck and feature-local CSS are reused. No authentication, authorization, backend, database, financeModel/supportModel or dispatch logic changes.

## Exact changed-file inventory
1. frontend/src/features/admin/admin.css
2. frontend/src/features/finance/Finance.tsx
3. frontend/src/features/finance/finance.css
4. frontend/src/features/onboarding/Onboarding.tsx
5. frontend/src/features/onboarding/onboarding.css
6. frontend/src/features/support/Support.tsx
7. frontend/src/features/support/support.css
8. frontend/tests/m29-4.test.mjs
9. frontend/tests/m29-4-browser.cjs
10. frontend/tests/m29-4-final.cjs
11. docs/m29-platform-finance-support.md
Ignored browser artifacts are under `.tools/m29-browser/m29-4/`.

## Automated validation — 2026-10-23
- npm run build: passed TypeScript/Vite; existing Zod annotation warnings only.
- npm test: **162 passed, 0 failed**. Five new server-rendered presentation tests cover source/ledger/missing count distinction, uncertainty/internal liabilities, retained metrics/native disclosure, separated platform populations and independent queue loading.
- git diff --check: passed.
- Logs: `.tools/m29-browser/m29-4/build.log`, `tests.log`, `browser.log`.

## Browser evidence
Real React app, router and API client at localhost:5188; Playwright Edge with deterministic synthetic API interception. No live backend transactions or integration claimed.
`results.json` reports passed for the implemented harness, with 76 route/viewport captures at 1440, 834, 390 and 360, no document horizontal overflow and no page exceptions. 86 PNGs include additional tab/state/keyboard captures.
Routes include dashboard, application list/detail, populated operator directory/detail, admin/operator finance, customer/admin/operator/staff support, dispute result, audit and denied direct finance URLs.
Interaction assertions: suspension confirmation/cancel; policy activation confirmation/cancel; audit filter/next page; staff and customer denied finance URLs make no finance API requests; customer messages exclude internal entries. Empty, slow and independent dispute-403 dashboard states captured. Reconciliation, statements and comparison tabs captured. Keyboard/reduced-motion captures are observational, not a complete accessibility assertion suite.

Visually inspected:
- SYSTEM_ADMIN-_admin-1440.png: distinct populations and queue hierarchy.
- SYSTEM_ADMIN-_admin_finance-360.png: readable side-by-side source/ledger, tabs ahead of summary, local table scrolling.
- SYSTEM_ADMIN-_admin_disputes_2-390.png: outcome above timeline, public/internal distinction and escaped markup. Oversized checkbox found and corrected afterward.
- SYSTEM_ADMIN-_admin_operators_1-360.png: readable stacked governance content, no horizontal clipping.

## Final acceptance — 2026-10-23

M29.4 UI acceptance is COMPLETE for the exercised deterministic fixture scenarios; live-backend integration is not verified. No known blocking UI defects in tested paths. Stop for human visual review.

`frontend/tests/m29-4-final.cjs` adds fixture-only POST interception and assertions. No sensitive mutation reaches a live backend. `final-results.json` records passing checks and zero page exceptions:
- Application approval cancellation sends no request; approval sends the entered note; rejection sends the entered reason. Native browser confirmations were added to approval, rejection and invitation reissue without changing payloads.
- Approval/reissue reveal synthetic invitation data once; hide/reload removes it, and browser storage contains no invitation value.
- Suspension/reactivation cancellation sends no mutation; confirmation sends the exact reason to the existing endpoint.
- Policy activation posts to the existing endpoint without a body. Reconciliation asserts operatorId/from/to/projectMissing for both projection modes.
- Support decision and escalation require confirmation and send version 7, UUID requestKey, content, internal=false and the selected outcome. Confirmation checkbox is keyboard operated with Space. Earlier customer fixture checks verify no internal messages; internal text remains escaped and visually distinct.
- Real logout followed by login as a distinct customer identity removes finance, platform and internal-message content. Staff/customer direct finance URLs make no finance request. This is a bounded rendered-state isolation test, not an exhaustive concurrent-request race audit.
- Finance tabs respond to Enter/Space; Tab/Shift+Tab returns focus correctly. Audit filter keyboard focus is checked. Account disclosure Escape returns focus. Reduced motion yields no active page-content animation. Governance/policy confirmations are inline regions with explicit cancel, not modal dialogs; native application prompts use browser accept/dismiss behavior. Full screen-reader and OS/browser matrix testing is outside this acceptance.

Final verification: npm run build passed; npm test **162 passed, 0 failed**; git diff --check passed. Logs: `final-build.log`, `final-tests.log`, `final-browser.log`. The earlier failed selector attempt is superseded by the passing focused run; no assertions were weakened.

## Final visual review
`final-captures.json` reports **28 final screenshots**, seven key views at 1440/834/390/360; no document overflow or page exceptions. Open `.tools/m29-browser/m29-4/review.html` for a simple clickable review index. Final filenames start `final-` and cover SYSTEM_ADMIN dashboard, finance, closed-dispute detail, governance confirmation, audit; OPERATOR_ADMIN finance and support detail.

Representative final images were directly inspected: dashboard 1440, platform finance 834, operator finance 390, closed dispute 360, operator support 360, audit 390, governance confirmation 360. Source/ledger columns remain readable, important finance navigation precedes summaries, table scrolling stays local, the dispute outcome precedes the timeline, public/internal messages are distinct, and the corrected support checkbox is compact. Governance is a long stacked page on mobile but retains readable controls. No overlap or clipped controls found in the reviewed views.

Non-blocking limitations: synthetic fixtures do not verify live server transitions, authorization enforcement, mail delivery, or real banking/settlement (none is claimed). Not an exhaustive session race, accessibility certification, or all-browser test. Evidence retains earlier intermediate screenshots; review.html links only final captures. No live-backend smoke test performed. M29.5 remains deferred.
