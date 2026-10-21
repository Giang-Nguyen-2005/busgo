# M27A — Financial management, commission and internal reconciliation

Handoff date: 21 October 2026. **M27A COMPLETE — ready for human review, uncommitted.** Critical acceptance requirements are satisfied by the evidence below. This report continues the existing implementation; it does not replace earlier milestone reports.

## Baseline and verified source model

The implementation baseline is M28 at `e1e6178706a77c5e7f84d1cde7ac182558643a27`, on `feature/v2-commission-reconciliation`. The source audit identified persisted payments and refunds as the cash-event authority, rather than current booking status. A paid payment subsequently marked REFUNDED still represents its original collection. Pending, failed and expired unpaid payments contribute no collection.

M17 full cancellation, M20 modification, M21 partial cancellation and M24B operator-funded vouchers already own payment/refund, item, inventory and voucher changes. M27A adds posting at the end of those existing transactions. Assisted collection reaches the same PaymentTicketService. No support decision, system-admin action or finance endpoint gains authority to execute commerce transactions.

Booking/item gross minus operator-funded discount equals net fare. Commission uses the actual persisted payment amount, including discounted principal and separate positive M20 adjustments. Refund events use persisted refunds, including M20/M21 allocations. Seat changes with no money movement create no financial event. Gross/discount are explanatory context, not additional cash. Principal context is recorded only when original gross minus original discount matches the payment; otherwise it remains null and reconciliation reports FARE_CONTEXT_UNAVAILABLE.

## Commercial assumptions and classification

All events are VND, verification SIMULATED. These labels describe the current application workflow; none verifies bank custody or provider settlement.

| Persisted workflow | Channel | Interpretation |
| --- | --- | --- |
| WEB principal, MOCK_ONLINE | PLATFORM_MEDIATED_SIMULATED | Simulated platform collection |
| PHONE principal, PAY_ON_BOARD or QR_TRANSFER | OPERATOR_RECORDED_SIMULATED | Operator-recorded simulated collection |
| WEB modification with CUSTOMER actor | PLATFORM_MEDIATED_SIMULATED | Simulated customer adjustment |
| Other combinations, including assisted modifications without established custody | UNKNOWN_OR_UNVERIFIED | No inferred custody allocation |

The method name alone is insufficient to classify modification custody. Refunds retain the original collection's channel and commission snapshot. Missing policy and unknown channel are independent uncertainties; neither is silently converted into a zero-rate or a known collector.

## Policies, commission and allocations

SYSTEM_ADMIN creates reasoned DRAFT policies for one operator, then explicitly activates them. Rate is 0–100 percent with up to four decimal places; zero is a valid explicit policy. There is no fictional default. Start must not be in the past. Validity is `[validFrom, validTo)`, with an optional open end. Activation may close one predecessor at the new future start; conflicting future schedules are rejected. Database triggers prohibit deletion and changes to historical terms; only future shortening of an end is permitted. Creator, reason and activation evidence are retained, with M28 audit metadata.

A collection snapshots the applicable policy ID, rate and rounded commission at paid_at. The policy must already have been activated by that instant. Later activation or backfill never retroactively prices history. Without applicable evidence, policy/rate/commission remain null.

Amounts use BigDecimal and HALF_UP to two decimal places. For principal `P` and percentage `r`, accrual is `round(P*r/100, 2)`. Each refund reverses the difference between the rounded cumulative refunded commission and commission already reversed. The final full refund reverses the exact remaining original accrual. Over-refund and over-reversal are rejected. Collections are positive and refunds/reversals negative; the original collection is never rewritten.

For each priced, classified signed event with amount `A` and commission `C`:

| Channel | Operator payable | Platform receivable |
| --- | --- | --- |
| PLATFORM_MEDIATED_SIMULATED | A − C | 0 |
| OPERATOR_RECORDED_SIMULATED | 0 | C |
| Unknown or unpriced | null | null |

Payable and receivable remain separate. Aggregate commission/allocation figures are known subtotals when uncertainty exists, not confirmed settlement balances. Unknown/unpriced counts remain visible even when signed amounts net to zero.

## V26 ledger and transaction safety

V26 adds finance_mutex, commission_policies, financial_events, financial_runs, financial_exceptions and financial_statements. V1–V25 are unchanged. Money is DECIMAL(12,2), rate DECIMAL(7,4), ledger timestamps UTC DATETIME(6). Existing source timestamps use the established JpaJdbcTime bridge; finance timestamps use explicit UTC conversion.

Each event has a unique `payment:<id>` or `refund:<id>` source key. Additional unique collection-payment and refund constraints enforce one projection per source. UPDATE/DELETE triggers protect events and issued statements. Source identifiers are logical references deliberately without commerce foreign keys: implicit parent locks would invert the established commerce lock order. Reconciliation checks source identity and orphan projections. Exception and statement tables reference finance runs with foreign keys.

Posting requires the caller's existing transaction (`Propagation.MANDATORY`). Call sites flush and finish existing inventory/voucher operations before posting. Established order remains trip(s in deterministic order), operator, booking/payment/inventory, voucher, then finance_mutex and finance policy/event rows. No commerce locking read or commerce mutation is acquired after finance entry. M28 audit metadata has no commerce FK that would reintroduce that inversion.

The per-operator database mutex serializes posting, policy activation and reconciliation across application instances; there is no JVM-only correctness lock. Source reads are nonlocking. A finance failure rolls back the original transaction and its source changes. Reconciliation uses READ_COMMITTED under the mutex, so a commerce transaction waiting to post is not partly visible as committed. Dashboard/comparison/filtered event reads use read-only REPEATABLE_READ for coherent multi-query snapshots. Race tests cover duplicate confirmation, refund versus projection, activation versus payment, competing activation, policy boundaries and rollback.

## Reconciliation, backfill and statements

Dashboard reads do not run backfill. Independent source collection/refund totals and missing counts expose incomplete projections. A platform administrator starts a synchronous operator-specific run, optionally setting projectMissing=true. Reporting dates are inclusive Asia/Ho_Chi_Minh calendar dates converted to a half-open UTC interval, at most 93 days. Source enumeration is capped at 5,000 parent payments; narrow the period if exceeded.

Refunds in a period bring their original parent payment into consideration even if collected earlier. Backfill projects that parent's complete refund history to preserve cumulative reversal arithmetic; consequently projection dependencies may lie outside the requested report period. Period totals still include only events occurring in that period. Backfill is idempotent and never modifies source commerce rows or invents historic rates. Existing mismatches are reported, not overwritten.

Checks include missing events, source identity/amount/time, classification, policy snapshots, refund allocation and payable/receivable balance. Exception reasons include SOURCE_LINK_MISMATCH, SOURCE_AMOUNT_OR_TIME_MISMATCH, CHANNEL_MISMATCH, CHANNEL_UNVERIFIED, COMMISSION_UNPRICED, FARE_CONTEXT_UNAVAILABLE, POLICY_SNAPSHOT_MISMATCH, REFUND_ALLOCATION_MISMATCH and ALLOCATION_IMBALANCE. Runs persist CLEAN or EXCEPTIONS, source/projected/exception counts, start/completion timestamps and a frozen summary. A failed transaction leaves no falsely completed run.

Issuing a statement is idempotent per run and copies its frozen summary. New evidence requires a new run/statement, not editing the earlier one. Statement issuance is not payout, receivable collection or settlement. No mismatch repair or ledger correction API is included.

## APIs and permissions

Base is `/api/v1/admin/finance` for SYSTEM_ADMIN or `/api/v1/operator/finance` for own-operator OPERATOR_ADMIN. Staff/customer/anonymous access is denied. Foreign requested operator IDs and foreign run/statement IDs are rejected. The operator workspace rejects system-admin mixed-role bypass through the existing operator context rules. Platform roles gain financial oversight only.

| Method and suffix | Contract |
| --- | --- |
| GET /overview | from,to; optional platform operatorId; summary and independent source totals |
| GET /ledger (alias /transactions) | from,to, optional operatorId,bookingId,channel; paginated events |
| GET /policies (alias /commission-policy) | optional operatorId; paginated policy history |
| GET /operators | Platform only; paginated operator selector |
| GET /operator-comparison | Platform only; from,to and paginated operator totals |
| POST /policies | Platform only; operatorId,rate,validFrom,optional validTo,reason |
| POST /policies/{id}/activate | Platform only; activate draft |
| POST /reconciliations | Platform only; operatorId,from,to,projectMissing |
| GET /reconciliations | Scoped paginated runs |
| GET /reconciliations/{id} | Scoped frozen run |
| GET /reconciliations/{id}/exceptions | Scoped paginated exception reasons |
| POST /reconciliations/{id}/statement | Platform only; idempotent issuance |
| GET /statements and /statements/{id} | Scoped list/detail |

Responses use ApiResponse; pages contain data,page,size,total. Page defaults to 0, size to 20; supported page 0–10000, size 1–100. Policy timestamps accept offsets and return UTC. Reason is required, maximum 500 characters. Invalid periods, pages, channels and terms return validation/business errors; denied access returns 403. The DTO definitions are the authoritative field inventory. Ordinary overview status NOT_CHECKED is not a reconciliation certificate.

## Frontend

Routes `/admin/finance` and `/operator/finance` add navigation, Vietnamese simulation labels, period filtering, summary/source/uncertainty cards, paginated ledger with booking/channel filters, policy history, reconciliations/exceptions and statements. Platform users additionally get operator comparison and explicit policy activation confirmation. Operator policy access is read-only. Query keys include identity, roles, session version, workspace, operator and filters; there is no polling. Dates display Vietnam time with four-digit years. Tables scroll within their containers at 390px. This is scoped finance UI, not an M29 redesign.

## Verification evidence

Evidence files below are local ignored artifacts under `.tools`; this report preserves their results without treating logs as tracked deliverables.

| Check | Result / evidence |
| --- | --- |
| Broad backend regression | 282 integration tests, zero failures/errors/skips; `.tools/m27a-regression.log`, BUILD SUCCESS |
| Commission calculations | 5 tests passed, included in that Maven run; not added to integration count |
| Migration | 2 tests included in 282: clean V1–V26 and populated V25→V26 upgrade; historical source preservation, idempotent unpriced backfill; Hibernate validation passed |
| Final backend refinements | 18 targeted tests executed: 17 passed, one fixture error; corrected test rerun passed 1/1. `.tools/m27a-final-targeted-agent.log` and `.tools/m27a-final-recheck.log` (BUILD SUCCESS). All 18 targeted cases now have passing evidence; not a claim that the initial 18-test invocation succeeded |
| Frontend | 146/146 passed, zero failed/skipped; `.tools/m27a-frontend-tests.log` |
| Production frontend build | Passed; `.tools/m27a-frontend-build.log` |
| Browser acceptance | `.tools/m27a-browser/results.json`: passed=true; both admin workspaces at 1440px and 390px |
| Whitespace | git diff --check passed after documentation edits |

The broad run covers M17, M18A, M20, M21, M23, M24B, M25, M26, M27A and M28 regression/concurrency suites. Browser evidence includes UI policy creation/activation, discounted WEB payment, reconciliation and statement; API-backed assertions verify refund reversal, PHONE receivable and tenant restrictions. All finance tabs, operator read-only policy and staff direct-route denial passed. Screenshots are platform-1440.png, platform-390.png, operator-1440.png and operator-390.png in the browser evidence directory. The browser harness assumes a fresh dedicated demo schema; it is not a general repeatable acceptance-data reset tool.

The final fixture error was `Invalid refund payment snapshot or remaining balance`: the new discrepancy test attempted a partial source refund without an M20/M21 parent record. The existing V19 trigger correctly rejected it. The fixture now inserts a valid full refund and an intentionally incorrect ledger commission reversal. Its passing rerun verifies POLICY_SNAPSHOT_MISMATCH and REFUND_ALLOCATION_MISMATCH without weakening source constraints or changing production logic. The other 17 passing cases include all six concurrency checks and the strengthened mixed-role namespace restrictions.

The browser run predates final backend reconciliation/authorization refinements and the explicit four-digit date formatter; those changes have focused backend/frontend checks rather than a repeated full browser scenario. Earlier reported frontend 143 was superseded by 146 after three finance tests. Repeated runs are not summed into unique coverage.

A subsequent Maven package attempt compiled and passed the five unit tests but failed to rename the JAR then held by the acceptance JVM; its integration phase did not execute. The process-termination command was declined by automatic approval review and was not retried. Final focused verification invokes test-compile and Failsafe directly, avoiding JAR replacement. No termination is needed. On resumption, the previous acceptance servers and isolated MySQL listener were no longer running. Only the existing dedicated M27A MySQL instance was started, on localhost:3327. The first sandboxed test attempt stalled in Mockito dynamic agent attachment before executing tests; the replacement invocation loads Mockito at startup. Neither stalled output nor the older packaged artifact is counted as final verification.

The focused command uses JDK 17, DB_URL `jdbc:mysql://127.0.0.1:3327/busgo_m27a_test?connectionTimeZone=UTC`, the isolated local root account and offline Maven:

```text
mvn -o -f backend/pom.xml -Pmysql-integration test-compile failsafe:integration-test failsafe:verify -Dit.test=M27AFinanceIT,M27AConcurrencyIT -DargLine=-javaagent:C:/Users/Giang/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar -Dspring.datasource.password=
```

An initial frontend build sandbox path error was resolved by permitted build execution; a new date assertion exposed two-digit locale years and the formatter was corrected.

Acceptance closure maps to the requested definition of done:

| Requirements | Evidence |
| --- | --- |
| 1–2: stable configurable policy, no invented rate | Policy lifecycle/overlap/zero/unpriced tests; immutable policy trigger |
| 3–5: persisted sources, distinct refunds, voucher economics | Source-linked projections, discounted payment/refund tests and browser scenario |
| 6–7: M20/M21 integrity, exact idempotent reversals | Broad commerce regressions, commission unit tests, refund/idempotency/race tests |
| 8–10: WEB/PHONE, explicit unknowns, correct allocations | Finance classification tests, browser PHONE assertions, allocation and uncertainty checks |
| 11–12: discrepancy detection, no fabricated external money | Reconciliation tests including final corruption fixture; SIMULATED schema/UI, no payout endpoint |
| 13–14: tenant isolation and governance-only system admin | Scoped API tests, mixed-role checks, browser foreign-operator/staff denial, unchanged commerce guards |
| 15–16: migrations and backend regression | Clean/populated migration evidence and backend results above |
| 17–18: frontend/build/browser | 146 frontend passes, production build and desktop/mobile acceptance evidence |
| 19–20: future compatibility and whitespace | M22/M27B strategy below; git diff --check passed, new untracked milestone files also checked against an empty file |

The expensive regression/migration/browser suites were not repeated during final closure because their covered production implementation was unchanged. Final refinements received the focused checks described above.

## Future M22 and M27B compatibility / limits

M22 must introduce verified provider transaction identities, callback validation and confirmation evidence before recognizing real collections. Extend classification and verification with an additive migration; do not relabel historical simulated rows as real. Persist confirmed payment/refund sources atomically with financial posting, retaining source uniqueness, snapshot and refund residual rules. Do not equate provider fees with operator-funded discount or commission without explicit commercial rules. Current V26 intentionally permits only simulated verification, so real-provider support requires that schema extension.

M27B should introduce separate settlement batches, allocations and external payout/collection evidence referencing ledger events and immutable statements. Settlement status must not overwrite collection/refund history, policy snapshots or issued statements. Payable/receivable arithmetic supplies an input to that work, not proof that any money moved. Provider fees, taxes, multi-currency, platform-funded promotions and real bank custody remain outside this milestone.

Unknown/unpriced historical records remain visible; there is no retroactive default policy, silent repair or fabricated settlement. Reconciliation is synchronous and bounded, not an asynchronous large-volume accounting engine. Race tests demonstrate specific invariants, not production load certification. MySQL 9.2 was used locally; Flyway emits a newer-than-tested-server warning, while actual migration checks passed. Local acceptance data is preserved; no production deployment or process cleanup is implied.

## File inventory and Git handoff

Modified commerce files: `backend/src/main/java/com/busgo/booking/{CancellationService,ModificationService,PartialCancellationService}.java` and `backend/src/main/java/com/busgo/payment/PaymentTicketService.java`.

New backend files: `backend/src/main/java/com/busgo/finance/{CommissionMoney,FinanceDtos,FinancialLedger,FinanceService,FinanceController}.java`; `backend/src/main/resources/db/migration/V26__commission_financial_ledger.sql`; `backend/src/test/java/com/busgo/{M27AFinanceIT,M27AConcurrencyIT,M27AMigrationIT}.java`; `backend/src/test/java/com/busgo/finance/CommissionMoneyTest.java`.

Modified frontend files: `frontend/src/features/auth/access.ts`, `frontend/src/layouts/{AdminLayout,OperatorLayout}.tsx`, `frontend/src/routes/router.tsx`. New frontend files: `frontend/src/features/finance/{Finance.tsx,financeModel.ts,finance.css}`, `frontend/tests/{finance.test.mjs,m27a-browser-check.mjs}`.

Documentation: this report plus `docs/api-contract.md`, `docs/database-design.md`, `docs/development-plan.md`, `docs/product-roadmap.md`. Earlier milestone completion notes are retained.

All milestone changes remain unstaged/uncommitted on the branch and HEAD stated above: 12 modified tracked files, 16 new milestone files, plus the unrelated untracked `tatus --short`. The index is empty. The unrelated file is preserved (handoff SHA256 `7308CA0DD5760406DAAD3FCDDEB3BEE1124ABA2A9C6ED648858DD0CE7EC900F3`). No commit, push, merge, tag or Git identity change was performed. Human review should use this source diff and its evidence; deployment and a fresh packaged artifact are separate steps.
