# M24B — Operator-funded promotions and vouchers

Status: **COMPLETE**, acceptance closed 13 October 2026. Ready for human review and commit; no commit, push, merge or tag performed. Branch `feature/v2-operator-promotions`, base/current HEAD `96f72da9bbecd383661f31ca638fa9aeb3f16743`.

## Pre-implementation audit and scope

The starting tree contained M20 modification, M21 partial cancellation, M23 notifications, M24A marketplace, M25 trip operations and M26 operator governance. Existing booking `total_amount` and item `unit_price` were payable amounts, and M20/M21 already reconciled payments minus refunds against those amounts. They remain net amounts. No earlier migration is edited. The unrelated untracked root file `tatus --short` is excluded from all implementation work and remains untouched and unstaged.

Only operator-funded vouchers for CUSTOMER WEB bookings are implemented. OPERATOR_ADMIN manages its own campaigns; OPERATOR_STAFF can read them. SYSTEM_ADMIN receives no voucher management bypass. Existing PHONE/assisted bookings remain unchanged and have zero discount. Existing M26 commerce eligibility remains authoritative; cancellation/refund availability under suspension is preserved.

## Business rules and money

- One optional voucher per booking, identified within the trip's operator. Codes are stripped, uppercased and validated as 3–40 ASCII letters/digits/underscore/hyphen, starting with a letter or digit. Codes are unique per operator; different operators may reuse a code.
- PERCENT (up to 100) and FIXED_AMOUNT support optional maximum discount, minimum gross spend, start/end timestamps, optional owning route, total limit and per-customer limit. Start is inclusive; end is exclusive. API timestamps have offsets and storage is UTC.
- Preview validates the owned unexpired hold and current eligibility without reserving capacity. Booking creation revalidates in its transaction. A quote is not a price or capacity guarantee.
- BigDecimal calculations use two decimal places. Percent discounts round HALF_UP, then apply the configured cap. Discount is prorated over items, rounding each share down and assigning residual cents in stable input order. Booking/item gross minus discount equals net; booking totals equal remaining active item totals.
- Existing positive item-price requirements are preserved: a voucher must leave at least 0.01 payable per seat. Zero/free bookings are rejected. Gross 500,000 with 25% capped at 100,000 gives discount 100,000 and net 400,000; two equal seats each retain gross 250,000, discount 50,000 and net 200,000. Discount 1.00 over three equal items allocates 0.34/0.33/0.33.
- Original gross/discount and voucher identity/name are immutable application snapshots. Current amounts may change through M20/M21. Immutability is enforced by application/JPA updates, not against privileged direct SQL.
- Terms can be edited only before any usage history exists, including released usage. Afterwards only pause/reactivation is allowed. Pausing or expiry does not reprice an existing booking.

## Usage lifecycle and compatibility

Creating a voucher starts in DRAFT; activation and pause persist ACTIVE/PAUSED. Read models derive SCHEDULED, EXPIRED and EXHAUSTED. List filtering uses persisted DRAFT/ACTIVE/PAUSED states. Actor, event and timestamp are recorded for management changes.

Booking creation inserts exactly one RESERVED usage in the same transaction as booking/items/inventory. Capacity counts REDEEMED plus unexpired RESERVED rows, including the customer's matching rows. Expired reservations stop consuming capacity immediately; the existing booking expiry workflow subsequently marks them RELEASED. Unpaid full cancellation also releases usage. Failed or rolled-back booking creation consumes neither hold nor capacity.

MOCK_ONLINE confirmation collects exactly the stored net amount and changes RESERVED to REDEEMED atomically. Repeated confirmation cannot create another usage/payment. Paid full or partial cancellation never restores the campaign use: REDEEMED remains historical. Unpaid partial cancellation keeps the same reservation. No voucher is reapplied during modifications.

M20 same-trip seat changes retain each item's current discount. Trip changes carry the original fixed per-item discount entitlement, capped at new gross minus 0.01. A later higher fare may restore that original entitlement. For original gross 200/discount 50/net 150, changing to gross 20 yields discount 19.99/net 0.01; later gross 300 yields discount 50/net 250. Net differences feed existing collection/refund calculations. Modified payment deadlines update pending usage deadlines. Modification history stores old/new gross and discount as well as net.

M21 removes selected items' current gross, discount and net from current totals. Refunds use discounted item net amounts; the removed discount is not redistributed. Original snapshots remain. M17 full cancellation refunds the remaining net collected balance through existing payment/refund accounting. Fully cancelled bookings retain the existing historical amount semantics. No new payment rail, fee, commission or settlement is introduced.

## Concurrency and lock ordering

Existing commerce paths acquire trip locks (ordered for a trip change), operator, existing booking/payment/item/inventory locks as required by that operation, then voucher and usage. New booking creation locks trip → operator → held inventory → voucher/usage before inserting its new booking/items/usage. It does not acquire another existing booking or trip lock after the voucher. Management acquires operator → voucher → usage. Voucher helpers never acquire trip or booking locks. Terminal payment/cancellation hooks acquire voucher locks after their existing commerce work.

The operator lock serializes voucher writes across different trips as well as pause, rename and suspension. Authoritative code lookup, voucher reads and capacity reads use locking/current reads, avoiding an older MySQL REPEATABLE READ snapshot after lock contention. Unique booking usage and operator/code constraints provide additional protection. Preview/list are advisory read-only snapshots. Capacity aggregation is bounded in application memory, although work still grows with matching usage history.

Eight concurrency tests cover competing bookings for the final use across trips, duplicate attempts, pause versus booking, payment versus cancellation, injected transaction rollback, expiry/payment, suspension/pause, and a renamed code observed from an older repeatable-read snapshot. All passed.

## Database: Flyway V24

`backend/src/main/resources/db/migration/V24__operator_promotions.sql` adds:

| Object | Additions |
| --- | --- |
| `operator_vouchers` | Operator ownership, OPERATOR funding source, normalized code, terms, persisted state, route, actor and timestamps; operator/code uniqueness and term checks |
| `voucher_usages` | Voucher/operator/customer/booking, reservation deadline, state and transition timestamps; unique booking, composite voucher/operator FK, capacity index and state checks |
| `voucher_management_history` | Voucher, actor, event and timestamp |
| `bookings` | `gross_amount`, `discount_amount`, `original_gross_amount`, `original_discount_amount`, `voucher_id`, `voucher_code`, `voucher_name` |
| `booking_items` | Current and original gross/discount; existing `unit_price` remains net |
| `booking_modification_items` | `old_gross`, `new_gross`, `old_discount`, `new_discount` |

Legacy discounts backfill to zero, leaving existing net amounts and payment/refund records unchanged. Original item gross is recovered from the earliest completed modification's old fare when available, otherwise its current fare; original booking gross sums original items including cancelled items. Current gross equals legacy net. Database checks enforce current gross − discount = net and valid original discounts. V1–V23 remain unchanged. Both clean V1–V24 migration/Hibernate validation and populated V23 → V24 upgrade passed on isolated MySQL 9.2.0 schemas. No destructive development database reset was performed.

## API contract

All paths below start with `/api/v1`; existing authentication and response/error envelopes apply.

| Method/path | Contract |
| --- | --- |
| GET `/operator/vouchers` | Own operator, admin/staff; `q` max 120, optional `state`, zero-based `page`, `size` 1–100 (default 20); paged response |
| POST `/operator/vouchers` | Admin; full Terms body; creates DRAFT, HTTP 201 |
| GET `/operator/vouchers/{id}` | Admin/staff; own detail only |
| PATCH `/operator/vouchers/{id}` | Admin; full Terms body; rejects previously used terms |
| POST `/operator/vouchers/{id}/activate` | Admin; activate/reactivate unless expired/exhausted |
| POST `/operator/vouchers/{id}/pause` | Admin; pause |
| POST `/bookings/voucher-quote` | CUSTOMER; `{holdToken, voucherCode}`; read-only authoritative preview |
| POST `/bookings` | Existing body plus optional `voucherCode`; transactional validation and reservation |

Terms fields are `code`, `name`, optional `description`, `discountType`, `discountValue`, optional `maxDiscount`, `minimumSpend`, `startsAt`, `endsAt`, optional `totalLimit`, `customerLimit`, `operatorRouteId`. Nullable limits mean unlimited. Numeric inputs have at most two decimal places and ten integer digits. Management response fields use database snake_case plus `reserved`, `redeemed`, `editable`, `effective_state`; no customer usage history is exposed.

Successful quote fields: `grossAmount`, `voucherCode`, `voucherName`, `discountType`, `discountValue`, `discountAmount`, `totalAmount`, `eligible=true`, `reasonCode=null`. Ineligible requests return the standard error envelope rather than an `eligible=false` success. Voucher business conflicts use HTTP 409 with codes including `VOUCHER_CODE_INVALID`, `VOUCHER_CODE_EXISTS`, `VOUCHER_TERMS_INVALID`, `VOUCHER_TERMS_LOCKED`, `VOUCHER_STATE_INVALID`, `VOUCHER_UNAVAILABLE`, `VOUCHER_NOT_ELIGIBLE`, `VOUCHER_INACTIVE`, `VOUCHER_NOT_STARTED`, `VOUCHER_EXPIRED`, `VOUCHER_ROUTE_MISMATCH`, `VOUCHER_MINIMUM_SPEND`, `VOUCHER_LIMIT_REACHED`, `VOUCHER_ZERO_PAYABLE`. Bean validation, role and ownership failures retain normal 400/403/404 handling.

Booking, operator booking detail and ticket bundle include `price`: current gross/discount/net, original gross/discount and voucher code/name. Booking seats and modification items expose corresponding item amounts. Clients never submit an authoritative discount or payable amount.

## Frontend

`/operator/vouchers` provides list/search/state/pagination, draft creation, unused-term editing, owning-route selection, activate/pause confirmations, usage summaries and disabled used terms. Staff access is read-only. Checkout applies/removes a code, handles specific rejection messages, shows the authoritative quote and blocks creation while applied-code validation is unresolved. Query state is tied to user/hold/code and resets with session or hold changes. Payment, booking detail, operator detail and receipt render committed gross/discount/net. Modification shows item price changes. VND formatting preserves meaningful cents rather than hiding residual amounts.

## Verification evidence

Counts below describe separate runs with overlapping tests, not an additive unique-test total. Raw logs remain in ignored `.tools/` locally.

| Check | Exact result / evidence |
| --- | --- |
| Expanded backend regression | 118 passed, 0 failures/errors/skips; `.tools/m24b-regressions.log` |
| M20 within that run | 28 modification + 5 concurrency passed |
| M21 within that run | 15 partial cancellation + 6 concurrency passed |
| M26 suspension within that run | 9 passed |
| Broad Maven verify | 61 unit passed; 468 integration executed, 467 passed and 1 failed; `.tools/m24b-full-verify.log` |
| Sole broad-run failure resolution | `CoreDatabaseIT` expected schema inventory omitted the three new tables. Updated expectation, then 44/44 passed; `.tools/m24b-core-recheck.log` |
| Final voucher focused run | 37/37: rules 25, concurrency 8, API 4; `.tools/m24b-final-focused.log` |
| Final receipt/payment recheck | 32/32: M9 payment concurrency/ticket and M24B rules; `.tools/m24b-ticket-recheck.log` |
| Historical migration | 1/1 V23 → V24 populated upgrade passed; `.tools/m24b-upgrade.log`; also passed in broad run |
| Clean migration | V1–V24 and Hibernate validation passed on fresh isolated schema; `.tools/m24b-focused.log` |
| Frontend final | 139/139 passed (supersedes earlier 135/135); `.tools/m24b-frontend-final-tests.log` |
| Frontend production build | Passed; `.tools/m24b-frontend-final-build.log` |

The broad run also passed existing M25 and M26 onboarding/concurrency tests. The entire broad suite was not rerun after the schema-test expectation fix; the failed class and subsequently affected code were rechecked specifically. It would be inaccurate to describe the original broad run as entirely green.

Reproduction commands (configure the isolated test DB/JDK first):

```powershell
mvn -o -f backend/pom.xml verify -Pmysql-integration '-Dspring-boot.repackage.skip=true'
mvn -o -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify -Pmysql-integration '-Dit.test=CoreDatabaseIT'
mvn -o -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify -Pmysql-integration '-Dit.test=M24BPromotionsIT,M24BConcurrencyIT,M24BApiIT'
mvn -o -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify -Pmysql-integration '-Dit.test=M9Payment*,M24BPromotionsIT'
```

Use the frontend package's existing test/build scripts from `frontend`. Do not rerun expensive suites solely to duplicate this evidence.

### Browser acceptance

Local isolated acceptance database `busgo_m24b_acceptance`, MySQL port 3323; backend 8097 and frontend 5174. Backend launch was fixed by providing a valid generated JWT secret and explicitly passing the local empty datasource password, which PowerShell would otherwise remove from environment variables. Startup uses existing `dev,demo` create-only seeding, preserving data. No secrets are included here.

- Operator created BROWSER25, edited unused cap to 100,000, activated, paused/reactivated, searched for missing/existing codes and opened details. Desktop 1440px and mobile 390×844 layouts passed; mobile document width 375 within viewport 390, with fields in bounds.
- Customer selected two An Phu seats on 14 October 2026, rejected an invalid code, applied lowercase `browser25`, removed it (gross restored), then reapplied. Quote: gross 500,000 − discount 100,000 = payable 400,000. Checkout mobile fit without horizontal overflow.
- Initial booking 1 expired during an external approval/usage-limit interruption. Its payment attempt correctly refused a cancelled booking. It was not reset or revived; capacity was available for a fresh booking.
- Fresh booking **2**, code **BG-C8C43F022565418D**, paid successfully through the mock-payment browser button on 13 October. Receipt displayed paid 400,000 and persisted 500,000/100,000/400,000, with tickets for L05 and L06. Reload fetched the same receipt and ticket identifiers, demonstrating persisted state.
- Operator BROWSER25 subsequently showed **redeemed 1, reserved 0, limit 10**. Details stated that booking history locks terms; all term controls were disabled.
- Console inspection found no captured errors, only the existing React Router `No HydrateFallback` warning. The warning is not introduced or fixed by this milestone. Temporary mobile viewport override was reset. No additional screenshots were taken during final acceptance closure at the user's request.

## Limitations and future work

Only mock payments and operator funding are supported. `funding_source` is explicit and constrained to OPERATOR: platform funding requires a future migration, authorization and liability allocation design; it cannot currently be submitted or activated. M22 must settle against committed net amounts and couple authoritative payment events to idempotent redemption. M27 must distinguish operator-funded discounts from future platform subsidies before commission/receivable/settlement calculations. No payout account, gateway credentials, commission policy, stacking, marketing campaign delivery or generic audit platform is added.

Original values for old bookings are reconstructed only from available historical data. Effective status and preview can become stale; final transactional validation is authoritative. Operator-level serialization favors correctness over campaign write throughput. Integration was exercised on local MySQL 9.2.0, not a deployed production gateway. An earlier executable-jar packaging attempt met a Windows file lock from an existing process; broad verification used repackage skip, and current application startup was verified using compiled classes via `spring-boot:run`. No claim of a newly verified executable release jar is made.

## Changed-file handoff

New backend files: `promotion/{PromotionMoney,VoucherDtos,VoucherService,VoucherController}.java`, migration V24, and `M24BApiIT`, `M24BConcurrencyIT`, `M24BMigrationIT`, `M24BPromotionsIT`, `PromotionMoneyTest`.

Modified backend files: booking controller/DTO/inventory/service; cancellation, modification and partial-cancellation services/DTOs; operator booking DTO/service; Booking and BookingItem entities; SecurityConfig; PaymentTicketDtos/Service; CoreDatabaseIT and M24AMarketplaceIT fixture/schema expectations.

New frontend files: `features/operator/Vouchers.tsx`, `features/booking/PromotionPrice.tsx`, `tests/promotions.test.mjs`. Modified: API errors, auth access, BookingSummary, Modification and its model, OperatorLayout, BookingDetailPage, BookingPage, TicketPage, OperatorBookingsPages, router, customer/operator types and money formatter.

Documentation: this report plus API contract, database design, development plan and product roadmap addenda. Final `git diff --check` passed. Git status has 35 modified tracked files and 14 new milestone files, all unstaged, plus the unrelated untracked `tatus --short`. The staged diff is empty. Branch and HEAD remain as recorded above; publishing remains under the user's control.
