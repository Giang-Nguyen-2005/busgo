# BusGo V1 final demo — 6–8 minutes

## Prepare once, then rehearse

1. Follow README Java 17/database/secret setup. Use a dedicated local demo DB;
   preserve existing development history. Start backend with `dev,demo`, frontend
   with `npm run dev`. Check health and database-backed location/search responses.
2. Read seeder created/reused/skipped logs. Search the logged tomorrow date in
   Asia/Ho_Chi_Minh: Bến xe TP. Hồ Chí Minh → Bến xe Đà Lạt. Confirm an active
   An Phú operator, usable bus/fare, future SCHEDULED trip, complete inventory and
   two available seats. Existing occupied/inactive fixtures are preserved and may
   reduce results; no reset restores them.
3. Register CUSTOMER once, e.g. customer@busgo-demo.example, choosing a local
   password. The registration flow grants CUSTOMER only. Keep its credentials locally.
4. LOCAL DEMO ONLY: admin operator.admin@anphu-demo.example / DemoOperator!2026;
   staff operator.staff@anphu-demo.example / DemoStaff!2026. These are initial
   passwords only; later changes/status suspension are never reset by seeding.
   Verify both accounts and their sole An Phú memberships before presenting.
5. Bootstrap SYSTEM_ADMIN once using the five README environment variables;
   verify login, disable bootstrap, remove its password variable. Keep its locally
   chosen credentials outside Git. Demo never grants SYSTEM_ADMIN.
6. Use separate browser profiles or independent login tabs. Do not duplicate a tab
   containing refresh tokens; copied session storage can race single-use rotation.
7. Record the chosen departure date, route/bus and actual runtime trip ID in your
   local rehearsal notes. Application source has no hardcoded trip IDs. Choose
   Minh Thành as the SECONDARY operator for status management; its dedicated
   create-only admin is operator.admin@minhthanh-demo.example / DemoSecondary!2026
   (LOCAL DEMO ONLY, initial password). Verify this account is login-capable and
   its membership ACTIVE before suspension. Tây Nguyên has no seeded admin and
   must not be used for deactivate/reactivate until deliberately provisioned.
8. Check responsive views and rehearse once before recording. Existing real
   bookings persist; pick new available seats for a repeat performance.

## Run the demo

| Time | Role | Action and talking point |
| --- | --- | --- |
| 0:00–2:45 | Customer | Login (briefly show registration if needed), search the future date, select the An Phú trip and two seats. Enter contact details, create booking, confirm **mock** QR payment, show two electronic tickets and QR codes. No bank transfer occurs. |
| 2:45–4:45 | Operator admin | Open `/operator/trips`, choose the same future Vietnam business date, then the same trip. Show the two BOOKED seats, seat inspection/booking contact, confirmed passenger manifest and segment occupancy. Refresh if the view predates payment. |
| 4:45–5:45 | Operator staff | Login separately, open the same trip/booking. Show read-only seat/manifest/occupancy access; no lifecycle/create/fleet/staff management controls. Opening a management path is denied. Backend permissions remain authoritative. |
| 5:45–7:15 | System admin | Open operator directory/detail and read-only staff. Deactivate then reactivate only the SECONDARY operator, explaining suspended access/new commerce and preserved history. Restore ACTIVE before ending. |

Do not lifecycle-transition the main customer trip. The operator home defaults
to today, while seed trips begin tomorrow: navigate using the future date filter.
For a quick optional segment-reuse illustration, use the coastal service on its
dedicated 51B-770.03 bus: L01 is blocked on HCM → Nha Trang but available afterward.
This is illustrative fixture inventory, not a fabricated paid booking.

## If something fails

- Java: verify both `java -version` and `mvn -version` report 17.
- Database: verify actual DB_URL/user/password, Compose health and volume credentials.
- Startup collision: read the reserved identity error; start `dev` alone to inspect.
  Do not repair by deleting trips or broadening roles.
- Missing trips: inspect skip logs/operator/bus/fare/lifecycle state. Use a separate
  empty demo database or deliberately prepare through existing management flows.
- Seat conflict: refresh and choose available seats. A hold is temporary; start the
  payment flow before planned pickup departure.
- Lost booking response: check owned history before submitting again. Payment
  confirmation is idempotent; booking creation has no retry/idempotency guarantee.

Live acceptance checklist: customer owner isolation; foreign operator IDs denied;
staff writes denied; SYSTEM_ADMIN operator context denied; secondary suspension
blocks new commerce/operator access while historical tickets remain readable.
See m15-verification.md for which steps were actually executed.
