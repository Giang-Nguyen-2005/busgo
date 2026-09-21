# M10.5 realistic demo data

The demo dataset is a development/portfolio fixture. The operator names, contact
details, addresses, and licence plates are fictional and do not represent private
bus companies.

## Enable and seed

Start MySQL normally, export `DB_PASSWORD` and `JWT_SECRET`, then run from
`backend/`:

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

Both profile names are intentional. `dev` supplies local datasource defaults;
`demo` is the explicit opt-in that activates `DemoDataSeeder`. The ordinary `dev`
profile, tests, and production configuration do not activate it. No fictional data
is present in a Flyway migration. The component also excludes the conventional
`prod` and `production` profiles even if `demo` is mistakenly combined with one.

The seeder is idempotent. Operators use stable `DEMO-*` codes, buses use stable
plates, and other catalogue records use stable exact natural keys. A trip is reused
when operator route, bus, and UTC departure snapshot already match. Starting the
same demo date twice creates no duplicate records. On a later day the seeder adds
only the missing rolling future window; it keeps past trips as useful history.

## Recommended browser search

Search:

- Pickup: **Bến xe TP. Hồ Chí Minh**
- Drop-off: **Bến xe Đà Lạt**
- Date: **tomorrow in Asia/Ho_Chi_Minh** (the backend startup log prints the date)

That date has six scheduled results at 06:30, 08:00, 09:30, 13:00, 18:30,
and 22:00 local time. The results span three operators, all three bus types,
220,000–300,000 VND fares, and different available-seat counts. Price/time filters
and all four existing sorts therefore produce visible changes.

## Fictional catalogue

Operators:

- An Phú Express (`DEMO-ANPHU`)
- Minh Thành Limousine (`DEMO-MINHTHANH`)
- Tây Nguyên Travel (`DEMO-TAYNGUYEN`)

Locations are generic public descriptions for TP. Hồ Chí Minh, Đà Lạt,
Buôn Ma Thuột, Nha Trang, Đà Nẵng, and Huế. Direction-specific routes cover
TP.HCM ↔ Đà Lạt, TP.HCM ↔ Buôn Ma Thuột, TP.HCM ↔ Nha Trang, and Đà Nẵng ↔ Huế.
A ninth route snapshots TP.HCM → Nha Trang → Đà Nẵng → Huế.

Representative layouts:

- **Limousine 22 chỗ:** one floor, 11 rows, columns 1 and 3 (centre aisle), A01–A22.
- **Giường nằm 34 chỗ:** two real floors with 17 berths each, columns 1/3/5,
  lower L01–L17 and upper U01–U17.
- **Ghế ngồi 40 chỗ:** one floor, ten 2+2 rows using columns 1/2 and 4/5,
  S01–S40.

Six stable fictional buses give each operator two vehicles. The primary search
contains at least one trip for every representative layout. Coordinates, not CSS
workarounds, create the aisle/floor presentation.

## Fares, inventory, and segment reuse

TP.HCM → Đà Lạt fares are 250,000 VND for An Phú Express, 300,000 VND for
Minh Thành Limousine, and 220,000 VND for Tây Nguyên Travel. The other direct
routes have plausible operator-specific fares. The multi-stop route has all six
forward fare pairs, including origin/intermediate, intermediate/destination, and
origin/destination journeys.

All trips are built by the same `TripAggregateCreator` used by the operator trip
API. Each therefore has validated route-stop snapshots, consecutive segments,
seat snapshots, and exactly one inventory row for every seat × segment pair.

Selected seats are `BLOCKED` to make availability visually varied. No fake `HELD`
or `BOOKED` row is created, and no booking relationship is bypassed. On the coastal
trip, L01 is blocked only on TP.HCM → Nha Trang and is still available from Nha
Trang onward. L02 is blocked only on Nha Trang → Đà Nẵng. This demonstrates safe
non-overlapping segment reuse without manufacturing a booking.

## Safe reseed and reset

Normal reseeding is simply another start with `dev,demo`; existing users,
bookings, payments, tickets, Trip 245, and all non-demo data remain untouched.

To replace only unbooked trips belonging to the three stable demo operator codes,
start once with the explicit reset flag:

```sh
BUSGO_DEMO_RESET_UNBOOKED_TRIPS=true \
  mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

PowerShell equivalent:

```powershell
$env:BUSGO_DEMO_RESET_UNBOOKED_TRIPS = 'true'
mvn spring-boot:run '-Dspring-boot.run.profiles=dev,demo'
Remove-Item Env:BUSGO_DEMO_RESET_UNBOOKED_TRIPS
```

Reset selects trips through `DEMO-ANPHU`, `DEMO-MINHTHANH`, and
`DEMO-TAYNGUYEN`, refuses to select any trip referenced by a booking, removes the
unbooked trip aggregates in foreign-key order, and immediately reseeds the rolling
window. It does not delete catalogue records, users, bookings, payments, or tickets.
Do not use volume deletion as a demo reset when the database contains development
history.

## Verification checklist

1. Repeat startup and confirm the log reports `0 trips created` for the same date.
2. Search the recommended journey and confirm exactly six demo results (additional
   user-created trips may increase the total).
3. Sort by price and departure; filter by 230,000–290,000 VND and by a time range.
4. Open representative results and inspect 22-, 34-, and 40-seat maps, unavailable
   seats, multi-select, and the price summary on desktop and narrow viewports.
5. Register/login as a normal customer and complete seat hold → booking → mock QR
   payment → ticket. The seeder intentionally creates no customer account.
6. On the coastal trip (tomorrow + 2), compare TP.HCM → Nha Trang with
   Nha Trang → Huế to observe L01 segment reuse.
