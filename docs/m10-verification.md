# M10 customer frontend — audit and verification

Date: 20/09/2026. Branch: `feature/customer-frontend`. Changes remain uncommitted.
Backend implementation, migrations and tests are unchanged. M11 is not complete.

## Audit before implementation

- Existing frontend was a React/Vite foundation page, a health API client, Router
  and a TanStack Query provider. Tailwind 4 was already configured. There was no
  authentication state, lint script or frontend test runner.
- Existing package ranges: React 19.1, Vite 7.1, TypeScript 5.9, Tailwind 4.1,
  Query 5.90, Axios 1.12, Router 7.9, RHF 7.63 and Zod 4.1. Added only
  `@hookform/resolvers`, `qrcode.react`, and `lucide-react`; lockfile retained.
- Inspected Java controllers, DTOs and services for M2/M3/M5–M9, security rules,
  the error envelope, API contract, UI specification and development plan.
- Success objects use `{ data }`; paginated lists use `{ data: [], pagination:
  { page, size, totalElements, totalPages } }`, with zero-based pages.
- Login returns accessToken, refreshToken, expiresIn and user (id/fullName/email/
  roles). Refresh returns the three token fields; rotation is single-use. GET me
  adds phone. Registration returns a profile summary with singular role and
  does not log the user in. Profile edits fullName/phone only. Password policy is
  at least eight Unicode code points and at most 72 UTF-8 bytes, with no required
  character classes. Password changes revoke refresh tokens.
- Trip detail/search include operator/route/busType, pickup.departureTime,
  dropoff.arrivalTime, price, durationMinutes, availableSeats and status.
  Seat maps use availableSeatCount, price and seat snapshots with floor/row/
  column/seatCode/seatType/available. Both endpoints require journey location IDs.
- M7 defaults to 1–5 seats and ten-minute holds. Hold response includes
  holdToken, tripId, pickup/dropoff, tripSeatIds, seats, pricePerSeat, totalPrice,
  expiresAt and ACTIVE/EXPIRED status. GET hold supports server revalidation.
- M8 creation accepts holdToken/contactName/contactPhone/contactEmail only.
  Booking detail returns bookingId/code/status, route/operator, stop.time,
  contact, seats with unitPrice, pricePerSeat, totalAmount and createdAt.
  History uses routeName/operatorName, departureTime and seat-code arrays.
- M9 mock confirmation accepts no amount/body and returns paymentStatus and
  bookingStatus. Ticket bundle contains paymentMethod/status, amount, journey,
  and one ticket per seat with exact qrData.
- Errors use code/message/details/timestamp. Known business codes map to
  Vietnamese messages; stack traces are never rendered.
- Older UI examples included CASH, passenger forms, cancellation, shortened
  query names and a different hold total field. Current implementation and the
  explicit M10 request resolve these differences; no material ambiguity or
  necessary missing endpoint required a backend change.

## Architecture and integration

`api/`: one Axios client, response/error handling, session token storage.
`features/auth/`: auth context and shared RHF/Zod validation.
`features/search/`: backend autocomplete and responsive filter sheet.
`features/trip/`: snapshot seat map and journey-bound selection draft.
`features/booking/`: hold storage and common booking summary.
`components/`, `layouts/`, `pages/`, `routes/`, `types/`, `utils/`: shared UI,
customer shell, page composition, lazy routes, Java DTO types and VN formatting.
Query owns server state; context owns authentication only.

Public routes: `/`, `/login`, `/register`, `/search`, `/trips/:tripId`.
CUSTOMER routes: `/booking`, `/payment`, `/booking-success`, `/profile`,
`/my-bookings`, `/my-bookings/:bookingId`. Multiple-role customers are allowed.

Integrated APIs: register/login/refresh; GET/PATCH users/me; change-password;
GET locations; trip search/detail/seats; POST/GET seat-holds; POST bookings;
GET bookings/me and booking detail; POST payments/mock-confirm; GET ticket.
The API base is `VITE_API_BASE_URL` (default `/api/v1`), with the existing Vite
proxy and `API_PROXY_TARGET`. Run instructions are in README.md.

Tokens live in tab-scoped session storage. A shared refresh promise prevents
parallel rotation, with at most one request retry and no authentication-endpoint
refresh loop. Account changes cancel stale retries, clear cached private data,
and clear the active hold reference. Local logout does not revoke backend tokens.

## Final UX polish

- Anonymous Continue stores only trip/journey IDs, selected seat IDs, a safe
  relative return URL and a timestamp. A 30-minute draft is read only for its
  matching journey. Login returns to the trip; a fresh seat response restores
  available seats and removes unavailable/missing seats with a Vietnamese notice.
  No hold is created before authentication or automatically after login.
- Display dates use `dd/MM/yyyy`; timestamps use `HH:mm • dd/MM/yyyy` in
  Asia/Ho_Chi_Minh. Backend ISO data is unchanged. Native date/time picker chrome
  follows the browser/OS locale; shared text summaries use Vietnamese formatting.
- Preserved the four-step checkout sequence on trip/contact/payment/ticket pages.
  Mobile labels are readable and wrap within each step.
- Reduced home hero height/illustration emphasis, strengthened the search card,
  focus and autocomplete states, and kept compact benefits/how-it-works content.
- Retained result cards and desktop filter sidebar. Price and departure-time
  filters are grouped; mobile uses a native modal bottom sheet with focus
  containment, Escape/close support, backdrop and active-filter count. Applying
  or clearing filters closes the sheet and queries the backend.
- Result cards clarify departure/arrival, duration, bus type, availability, fare
  and CTA; an arrival date appears when it differs from the departure date.
- Seat layout still comes entirely from floor/row/column snapshots. Legend,
  selected focus treatment, summary spacing and internal seat-map scroll were
  refined without a two-seat special case.
- Contact/countdown and payment layouts remain intact. Ticket/status labels use
  Vietnamese strings while retaining enums internally. Ticket codes have a
  selectable monospace block that wraps without clipping; QR uses exact qrData.
- Booking history/detail and profile use the same spacing, cards, statuses and
  date utilities. Backend test names are displayed without stripping prefixes.

## Verification results

| Check | Result |
| --- | --- |
| Dependency installation | Passed; 122 packages audited, zero reported vulnerabilities at installation |
| TypeScript strict check | Passed after polish |
| `npm run build` | Passed after polish; production route chunks generated |
| `git diff --check` | Passed; only platform LF/CRLF conversion notices |
| Frontend lint / test scripts | Not configured in the original project; no large framework added |
| Focused Node checks | 30 passing assertions for date/time rollover, safe return URLs, draft validation/expiry/journey matching, unavailable-seat removal |
| Previous `mvn test` | 23 tests, zero failures/errors/skips, JDK 17 |
| Previous `mvn verify -Pmysql-integration` | 116 integration tests, zero failures/errors/skips; also runs the 23 unit tests |

Initial Windows sandbox failures required the normal approval mechanism for
esbuild and Maven. An installed JDK 17 resolved the default JRE limitation.
The older `.tools/test-db.json` credentials failed MySQL authentication; the
current Compose `.env` credentials succeeded. Secrets were not printed. The
later approval-capacity interruption was resolved before the final build.
The full backend suite was not repeated for this frontend-only polish pass.

Browser checks used the live backend and existing development records:

- Registration/login, backend autocomplete, search URL construction, seat
  selection and displayed totals, hold creation and recovery after reload.
- Anonymous A02 selection → login → restored A02 after fresh availability →
  explicit Continue → hold → contact booking → mock payment → confirmed ticket.
- Existing pending booking resumed from history/detail, then paid successfully.
  Revisiting its payment page offered the existing ticket rather than another
  payment. Reloading the ticket retained its server-issued code and QR.
- Profile GET/update succeeded. Booking history/detail and status presentation
  were inspected. Password-change submission was not repeated during polish.
- Mobile filter open/apply/clear, real zero-result response and focus return.
- All eleven routes were checked at 1440, 1024 and 390px: no document horizontal
  overflow. Ticket QR remained 152px and code stayed within its container. Visual
  screenshots reviewed home, search, seats, contact checkout, payment, ticket,
  history/detail, profile and authentication layouts.

The local QA account and two confirmed demo bookings remain as test evidence.
No cancellation, refund or unrelated data cleanup was performed.

## Limits and follow-up

- Realistic 22–40-seat demo data was not present; live seat testing used the
  existing two-seat snapshot. The component uses dynamic grid coordinates,
  floor grouping and horizontal scrolling; it contains no fixed seat map.
- The unavailable-after-login branch has focused logic checks; the live browser
  restoration test covered a seat that remained available.
- No public operator/bus-type catalogue exists, so those filter controls are not
  fabricated from partial result pages. Supported price/time filters are exposed.
- Booking creation is not idempotent; a lost response advises checking history
  before another attempt. Prices, ownership, expiry and mutations remain backend
  decisions. Changing the backend's configured five-seat limit would require
  updating the matching frontend limit.
- Vite reports non-fatal third-party Zod annotation warnings and an initial
  bundle-size advisory. Production build succeeds; feature routes are lazy-loaded.
- No real payment, cancellation/refund, operator/admin UI or PDF generation.

Changed files are confined to frontend source/package files and README/development
plan/UI/verification documentation. No commit, merge, push, reset or clean ran.
