# M31.4 — Schedules & Pricing

Ngày thực hiện:08/11/2026, giờ Việt Nam. Tiếp tục working tree M31.1–M31.3 trên `main`, HEAD `df8f0b7812c061774c3d1285d5311d9b776cf31f`. Không làm lại catalogue/fleet, không commit/push/merge/deploy/reset/stash hoặc đọc/sửa/xóa `tatus --short`.

## 1. Baseline và môi trường test

Đã đọc [M31.1](m31-1-data-audit-and-expansion-plan.md), [M31.2](m31-2-locations-routes.md), [M31.3](m31-3-operators-fleet.md), manifest/fleet rotations, route/association/fare services, TripAggregateCreator, time bridge, search/holds/booking/payment/ticket contracts. Không thay customer UI hoặc pricing engine.

Xác minh container trước khi resume, rồi đọc lại số liệu thật:

| Thuộc tính | Giá trị |
| --- | --- |
| Container / Compose |`busgo-m312-catalogue-mysql-1` / `busgo-m312-catalogue`|
| Volume |`busgo-m312-catalogue_m312_data` →`/var/lib/mysql`|
| MySQL / bind |8.4.11 /127.0.0.1:3352|
| Schema |`busgo_m312_test`|
| Flyway |28 successful migrations, version28,0 failed|
| Supply baseline |3 operators,19 buses,3 types,96 templates,12 locations,25 routes,52 stops|
| Pricing baseline |13 associations,18 fares|
| Trip baseline |21 trips,48 trip stops,27 segments,678 trip seats,882 inventory rows|
| Commerce baseline |0 bookings/payments/tickets;6 operator users|

Chỉ schema test hiện hữu được sử dụng, không reset/recreate hoặc chạy legacy seeder. Container/database ứng dụng không được khởi động/ghi. Lệnh đọc đầu sau `docker start` gặp socket chưa sẵn sàng; đọc lại sau startup đã xác nhận baseline trên, trước mọi apply.

## 2. Anchor và horizon

**Anchor cố định14/11/2026, horizon14 ngày:14–27/11/2026 theo Asia/Ho_Chi_Minh.** Departure ngoài cửa sổ không được tạo. Arrival của chuyến đêm ngày27 có thể sang28/11; không tự thêm chuyến về ngoài horizon.

Window được ghi trong local ignored `.tools/m31-4/window.json` và truyền explicit vào command/test. Không tự lấy “ngày mai” mỗi startup, không gia hạn cửa sổ vô hạn. Từng slot có key `m31.4-v1:<plate>:<directional-route-key>:<local-departure>`; generated IDs được resolve từ DB, không hardcode.

Đã đối chiếu lịch hiện hữu từng bus trước khi ghi. Anchor nằm sau lịch legacy của các xe dùng trong rotation; nếu vị trí cuối legacy khác initial base thì importer yêu cầu gap tối thiểu24 giờ cùng acknowledgement explicit, không diễn giải AVAILABLE thành vị trí xe. Không sửa/xóa/backdate legacy để đạt364.

Time lưu qua `BusGoTime` và **TripAggregateCreator hiện có**. Đọc/query JDBC liên quan timestamps dùng contract/time bridge hiện hữu; không đổi JVM timezone, schema timestamp hoặc pricing/search timezone engine. HTTP tests đối chiếu Instant trả về với lịch VN, gồm midnight và ngày cuối.

## 3. Fare matrix đã áp dụng

Đơn vịVND, giá gross mỗi chỗ. **Mọi giá mới là mô phỏng, chưa phải báo giá doanh nghiệp được xác minh.** Giá chỉ gắn operator/journey, không thêm bus_type_id hoặc fare per-trip/per-date. Không cộng voucher vào giá gốc.

| Owner | Cặp tuyến (cả2 chiều) | Giá gross | Tác động |
| --- | --- | --- | --- |
| An Phú |HCM↔Đà Lạt|250.000|REUSE legacy, không sửa|
| An Phú |HCM↔Nha Trang|290.000|REUSE legacy, không sửa|
| An Phú |HCM↔Cần Thơ|160.000|2 fares mới|
| An Phú |HCM↔Phan Thiết|180.000|2 fares mới|
| Minh Thành |HCM↔Đà Lạt|300.000|REUSE legacy, không sửa|
| Minh Thành |HCM↔Vũng Tàu|150.000|2 fares mới|
| Minh Thành |Đà Lạt↔Nha Trang|200.000|2 fares mới|
| Minh Thành |Đà Nẵng↔Huế|140.000|REUSE legacy, không sửa|
| Tây Nguyên |HCM↔Buôn Ma Thuột|260.000|REUSE legacy, không sửa|
| Tây Nguyên |Buôn Ma Thuột↔Pleiku|160.000|2 fares mới|
| Tây Nguyên |Nha Trang↔Quy Nhơn|210.000|2 fares mới|
| Tây Nguyên |Quy Nhơn↔Đà Nẵng|280.000|2 fares mới|
| Tây Nguyên |Đà Nẵng↔Quảng Ngãi|120.000|2 fares mới|

Mỗi direct association có1 fare đúng stop đầu→cuối, ACTIVE và >0. Tất cả18 fares legacy giữ nguyên, kể cả8 fares ngoài daily matrix (Tây Nguyên HCM↔Đà Lạt và6 coastal pairs). Importer dùng giá ACTIVE hiện hữu nếu người dùng đã đổi hợp lệ; test giá123.456 xác nhận không ép về manifest. Duplicate/inactive/wrong-stop/giá không usable là conflict, không reprice/reactivate.

Association mới được tạo bằng OperatorRouteService.attach chỉ sau khi xác nhận chưa tồn tại. FareService.replace chỉ được gọi khi tập fares trống và journey không phải legacy; không dùng replace để ghi đè fares đã có.

## 4. Associations: planned và persisted

MatrixM31.3 gồm26 direct associations:10 legacy được reuse,16 mới tạo thật. Giữ3 associations legacy khác ngoài daily matrix. **Tổng sau apply29**, không phải26 toàn DB. Mỗi association mới thuộc đúng owner trong matrix; không tạo association cho mọi operator×route, không thay status record cũ.

Source catalogue vẫn24 directional direct routes +1 coastal legacy =25. Không thêm routes/locations/fleet trong M31.4.

## 5. Departures và coverage

13 rotation blocks×2 lượt/ngày×14 ngày =**364 departures mục tiêu**. Kết quả thực tế trên schema test: **364 CREATED,0 skipped/conflicted/rolled-back batches**, đủ26/ngày và24 direct routes. Chỉ2 nhà xe AP/MT chạy HCM↔Đà Lạt trong window này; association Tây Nguyên legacy được giữ nhưng không tự thêm lượt để vượt26/day.

| Owner / bus | Vòng đi–về | Giờ đi / về VN | Ghế |
| --- | --- | --- | --- |
| AP /51B-770.01|HCM↔Đà Lạt|06:30 /15:00|34|
| AP /DEMO-M31-AP-01|HCM↔Nha Trang|06:00 /16:30|34|
| AP /51B-770.02|HCM↔Cần Thơ|06:00 /11:30|40|
| AP /DEMO-M31-AP-02|HCM↔Phan Thiết|06:00 /12:00|40|
| MT /50F-880.01|HCM↔Đà Lạt|08:00 /16:30|22|
| MT /DEMO-M31-MT-01|HCM↔Vũng Tàu|06:00 /10:00|22|
| MT /50F-880.02|Đà Lạt↔Nha Trang|06:00 /11:00|34|
| MT /DEMO-M31-MT-02|Đà Nẵng↔Huế|06:00 /10:30|40|
| TN /47B-660.01|HCM↔BMT|06:00 /15:30|40|
| TN /47B-660.02|BMT↔Pleiku|06:00 /11:30|22|
| TN /DEMO-M31-TN-01|Nha Trang↔Quy Nhơn|06:00 /12:30|34|
| TN /DEMO-M31-TN-02|Quy Nhơn↔Đà Nẵng|18:00 /02:30|34|
| TN /DEMO-M31-TN-03|Đà Nẵng↔Quảng Ngãi|06:00 /10:30|40|

Tất cả24 directional routes đã được gọi Search qua HTTP ngày anchor: HCM→Đà Lạt và chiều ngược có2 kết quả;22 directions còn lại mỗi direction1 kết quả, tổng26. Đối chiếu từng kết quả với slot: đúng operator/journey/giờ đến–đi/gross fare và22/34/40 ghế ban đầu.

## 6. Bus rotation safety

Kế thừa M31Fleet.plan/validate: không overlap, nối đúng destination→origin, buffer tối thiểu90 phút giữa2 lượt. Lượt Nha Trang→HCM16:30 đến01:30 hôm sau; TN-02 đi18:00 đến01:00, về02:30 đến09:30. Không chỉ lặp giờ xuất phát từ cùng thành phố.

**Initial placement là giả định demo explicit**, được ghi trong mỗi Batch report và yêu cầu `acknowledge-initial-placement=true` khi tạo slot mới. TN-02 bắt đầu tại Đà Nẵng để chạy02:30 ngày đầu, sau đó đến Quy Nhơn09:30 và xuất phát18:00. Các xe khác bắt đầu ở origin của leg đầu trong bảng. Đây không phải vị trí thực đã xác minh từ DB.

Kiểm tra thực tế khi apply: đúng owner/type, bus AVAILABLE/chưa delete, active/usable layout, active operator/route/association/stops/fare và maintenance theo interval. Khóa bus trước operator, dùng cùng production guards. Existing manual trip chen trong span rotation hoặc vi phạm buffer làm skip nguyên rotation; không đổi bus để lách conflict.

Nếu một slot missing đã ở quá khứ, nếu predecessor đã CANCELLED trong rotation chưa hoàn chỉnh, hoặc cần nối vào lịch manual không khớp vị trí thì không tự tạo những leg rời gây teleport. Existing exact slots được reuse, không phục hồi lifecycle/inventory. COASTAL và5 spare không được nhân lịch mới.

## 7. Aggregate và DB row counts

Trips/stops/segments/seats/inventory mới đều qua **TripAggregateCreator**, không raw insert những bảng này. Mỗi direct trip có2 stops,1 segment và inventory=seat count. Initial status SCHEDULED, toàn bộ inventory mới AVAILABLE, không fake hold/booking.

436 ghế cho13 primary buses×2 lượt×14 ngày =**12.208 seats/inventory mới**. Counts thực tế:

| Bảng | Trước M31.4 | Sau import | Sau booking smoke |
| --- | --- | --- | --- |
| operators / buses / bus_types / seat_templates |3 /19 /3 /96|Giữ nguyên|Giữ nguyên|
| locations / routes / route_stops |12 /25 /52|Giữ nguyên|Giữ nguyên|
| operator_routes / operator_route_fares |13 /18|29 /34|29 /34|
| trips |21|385|385|
| trip_stops |48|776|776|
| trip_segments |27|391|391|
| trip_seats |678|12.886|12.886|
| trip_seat_segment_inventory |882|13.090|13.090|
| users |6|6|7, thêm đúng1 customer demo mới|
| bookings / payments / tickets |0 /0 /0|0 /0 /0|1 /1 /1|
| operator_media |0|0|0|

Import acceptance so sánh nội dung tất cả rows baseline với snapshot sau import: legacy rows vẫn tồn tại nguyên vẹn, không chỉ so count. Sau smoke chỉ inventory của ghế mới thuộc booking được BOOKED;882 rows inventory legacy không bị importer sửa. Không thay old fares, account/password/contact/profile, media hoặc các giao dịch cũ.

## 8. Module, dry-run và apply

Production files mới trong `demo/m31/`:

- `M31Schedule`: version m31.4-v1, price matrix, stable keys và report DTOs.
- `M31ScheduleBatchService`: preflight/resolvers/owner+time+position+maintenance checks; create association/fare và production aggregates cho1 rotation.
- `M31ScheduleService`: coordinator transaction riêng từng rotation, tối đa28 trips/batch. Batch lỗi rollback đầy đủ; batch đã commit không bị xóa. Report phân biệt CREATED/REUSE/SKIP và rolledBack, giữ business error code mà không dump payload/secret.
- `M31ScheduleRunner`: standalone opt-in command, dry-run mặc định, explicit anchor/horizon≤14, không Flyway/legacy seeder/bootstrap/web/schedulers. Runner phát report partial rồi trả lỗi nếu batch bị rollback bất ngờ.

TripRepository bổ sung read theo bus/date để đối chiếu existing history, không sửa production pricing/search/booking/payment rules. Target guard kế thừa M31.2: dev không demo/prod/production, loopback port explicit, schema `busgo_m312_*` khớp verified-schema, V28 và catalogue FULL REUSE. Không chạy nhiều importer hoặc management writer cạnh tranh cùng bộ dữ liệu demo.

Từ project root, JDK17 và DB env chỉ tới schema test đã xác minh:

```powershell
mvn.cmd -o -f backend/pom.xml spring-boot:run `
  '-Dspring-boot.run.main-class=com.busgo.demo.m31.M31ScheduleRunner' `
  '-Dspring-boot.run.profiles=dev' `
  '-Dspring-boot.run.arguments=--busgo.m31.schedule.enabled=true --busgo.m31.catalogue.verified-schema=busgo_m312_test --busgo.m31.schedule.anchor=2026-11-14 --busgo.m31.schedule.horizon=14 --busgo.m31.schedule.acknowledge-initial-placement=true'
```

Dry-run trả `M31_SCHEDULE_REPORT=` với anchor/horizon, association/fare actions/actual prices, slot keys, bus/type, local departure/arrival, endpoints, initial base, expected seats/inventory và conflict reasons. Sau review report và kiểm tra target, thêm `--busgo.m31.schedule.dry-run=false` để apply. Không đưa DB password hoặc account secrets vào command args/log/docs.

Không đổi anchor khi rerun để lấp slot; không tạo lịch mới ở ngày khác hoặc bus khác khi collision. Rerun cùng window có26 associations/fare reuse và364 trip REUSE, không động vào giá/lifecycle/inventory kể cả ghế BOOKED. Dry-run trước apply thực tế trả16 new associations,16 new fares,364 CREATE,12.208 expected inventory,0 conflicts.

## 9. Search / Trip / public data acceptance

Rehearsal chạy **HTTP thật** tới Spring Boot random port bind127.0.0.1, không MockMvc/API interception, dùng MySQL test ở3352. Đã kiểm tra:

- Search cả24 directions ngày14/11, đúng2 operators cho HCM↔Đà Lạt; tất cả26 rows đúng giá/giờ/available seats so manifest.
- Pagination size1/page0/page1 trả2 chuyến HCM→Đà Lạt khác nhau, gross250.000 rồi300.000 với PRICE_ASC.
- minPrice260.000 và departureFrom07:00 đều lọc còn đúng1 kết quả trong hành trình đó.
- Ngày quá khứ trả0; chiều về và chuyến overnight được tìm đúng ngày pickup.
- Nha Trang→HCM ngày27/11 đến ngày28/11 theo giờ Việt Nam, không tạo departure ngoài horizon.
- Trip Detail và SeatMap của chuyến mới HCM→Phan Thiết: đúng gross180.000 và40 ghế, có geometry/seat IDs hợp lệ; hold được chấp nhận.

Discovery Home vẫn giới hạn8 routes và6 upcoming cards; không tăng LIMIT hay redesign. Giới hạn này không cản Search theo location IDs của các tuyến mới. Active route listing không phải bảo đảm có chuyến mọi ngày; Search date/fare/seat filters vẫn quyết định kết quả. Không sửa frontend, không cần frontend build mới.

## 10. Booking smoke thật trên trip mới

HTTP Search→Trip Detail→Seats→Register/Login customer demo mới→Hold→base-price/voucher-options→Create booking→Mock payment→Ticket/QR.

Kết quả: trip**335**, booking**18**, owner**61 (An Phú)**, seat ID**10133**; gross/net/payment đều**180.000VND**, discount0, payment PAID, ticket VALID, qrData có nội dung. IDs là kết quả resolve DB của lượt test này, không hardcode trong importer. Không công bố raw QR/token/password trong report.

Không có voucher/campaign trong schema test nên dùng **base quote từ hold và `/bookings/voucher-options.grossAmount`**, options rỗng; đây là flow hiện có khi không chọn voucher. Không tự tạo promotion hoặc sửa voucher-quote contract để lấp bước. Booking snapshot gross đúng active fare, payment đúng net.

Một inventory row BOOKED sau payment, không còn hold_token của hold đã tiêu thụ. Giữ lại ghế đó lần nữa trả409. Mock-confirm lặp lại trả cùng payment ID, DB vẫn1 booking/1 payment/1 ticket. Vé khớp operator/trip/seat. Rerun364 slots sau payment giữ nguyên toàn schema snapshot, không mở lại ghế BOOKED.

Customer mới dùng `.example` và credential sinh ngẫu nhiên trong memory test; không giao dịch trên tài khoản khách thật. Tất cả payment/finance projections vẫn SIMULATED; không có chuyển tiền thật.

## 11. Dry-run / apply / rerun evidence

| Bước | Kết quả |
| --- | --- |
| Standalone dry-run |364 CREATE,0 SKIP;16 association CREATE,16 fare CREATE; BUILD SUCCESS|
| Persistent apply |364 CREATED,0 reused,0 skipped/rolled back;16 associations+16 fares mới|
| Same-anchor rerun trước smoke |364 REUSE,0 CREATED, snapshot không đổi|
| Same-anchor rerun sau paid booking |364 REUSE,0 CREATED, toàn schema snapshot không đổi|
| Standalone apply command cuối |364 REUSE,0 CREATED/SKIP/rolledBack, mọi association/fare đều REUSE; BUILD SUCCESS|

Artifacts local ignored: `.tools/m31-4/command-dry.log`, `window.json`, `backend/target/m31-4-import-receipt.json`, `backend/target/m31-4-acceptance.json`. Receipt có counts/coverage/IDs/booleans, không có password, access/refresh/hold token hoặc raw QR.

## 12. Focused test results

| Lượt | Kết quả | Evidence local ignored |
| --- | --- | --- |
| Compile |PASS|`.tools/m31-4/compile.log`|
| Focused unit |Schedule matrix1 + Fleet plan1,2/2 pass|`focused.log`|
| Focused MySQL integration |4/4 pass,0 fail/error/skip|`focused.log`|
| Persistent import + real HTTP smoke |1/1 schedule unit lặp lại +1/1 integration pass; BUILD SUCCESS|`acceptance.log` và receipts|
| Final unit + standalone rerun |2/2 Schedule unit pass (thêm partial-rollback report case), sau đó command rerun PASS|`final-command-rerun.log`|
| SQL hậu kiểm toàn bộ364 trips mới |0 aggregate/ownership failures;12.207 AVAILABLE +1 BOOKED; V28 vẫn28 success/0 failed|`.tools/m31-4/verification.sql` và kết quả đọc cuối|
| Static/docs |`git diff --check`, local document links/whitespace PASS|Kiểm tra cuối task|

4 integration cases: dry/create/rerun/legacy preservation/complete aggregates; legacy price changes retained; maintenance/manual overlap/past/placement conflicts; inactive association/wrong fare stops preserved. Tests thông thường rollback; acceptance persist chỉ bật explicit system property trên baseline21 trips, không reset để chạy lại. Không tính repeated unit thành test mới.

```powershell
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration `
  '-Dbusgo.m31.schedule.integration=true' `
  '-Dtest=M31ScheduleTest,M31FleetTest' '-Dit.test=M31ScheduleIT'
# Chỉ một lần trên baseline M31.3, sau dry-run:
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration `
  '-Dbusgo.m31.schedule.acceptance=true' '-Dbusgo.m31.schedule.anchor=2026-11-14' `
  '-Dtest=M31ScheduleTest' '-Dit.test=M31ScheduleAcceptanceIT'
```

Không chạy lại545 integration tests M30.6, không làm browser regression không liên quan. Kết quả này chứng minh API/DB và importer trong schema test, không phải browser E2E hoặc chứng nhận production.

## 13. Partial completion và rủi ro còn lại

Window này không có skipped slot. Khi gặp conflict, importer skip nguyên missing rotation thay vì tạo các lượt đứt quãng. Unexpected batch failure rollback cả batch và report0 CREATED cho batch đó; các rotation trước đã commit vẫn được giữ. Rerun cùng anchor kiểm tra lại exact tuples, không cleanup hay repair dữ liệu cũ.

Existing slot sai association/arrival hoặc thiếu stop/seat/segment/inventory theo các kiểm tra hiện có sẽ thành conflict, không sửa inventory. Không coi importer là bộ sửa mọi dạng corruption thủ công. Snapshot lifecycle/availability có thể được nghiệp vụ thay đổi sau seed; chỉ kiểm tra current bookability khi Search/Hold/Booking, không coi log import là lời hứa còn chỗ. Bounded batch không thay thế stress test mọi race/mạng/mất điện; chưa chạy fault injection giữa chừng trên MySQL hoặc load test production. Unit test partial completion xác nhận rollback được gọi cho failed rotation,24 slots thành công của12 rotations khác được đếm CREATED và2 failed slots chỉ là SKIP, giữ đúng business conflict code.

Initial positioning/reposition gap là giả định demo; crew/ca lái thực tế chưa được lập. COASTAL chưa có phương án chiều về, không mở rộng trong window này. Rolling schedule/retention, phân giá theo loại xe, promotion campaign và production rollout không nằm trong scope.

## 14. Chuẩn bị M31.5

Có thể tiếp tục pickup/dropoff supporting content, media đúng owner/type, employee/crew và voucher demo có điều kiện trên schema test. Không thay fare/historical booking để làm kịch bản đẹp hơn. Giữ3 operators/19 buses/3 layouts/25 routes đã chốt và phân biệt planned content với persisted records.

## 15. Bàn giao

**M31.4 hoàn tất trong phạm vi source và schema test đã xác minh**, đủ364 trips thật và luồng HTTP booking/payment/ticket/QR đã đạt. Database ứng dụng không thay đổi; mọi thay đổi chưa commit của M31.1–M31.3 được giữ. Container `busgo-m312-catalogue-mysql-1` đã dừng với exit0; giữ volume, window và receipts/logs. HTTP test server và standalone contexts đã đóng, không để service M31 chạy nền.

Không thêm operator/bus/type/location/route, không tạo migration, không chỉnh frontend hoặc engine tiền. Source M31.4 gồm4 module files, một read method TripRepository,3 test files và tài liệu này. Không commit/push/merge/deploy/reset/stash/xóa volume. Dừng để người dùng review trước M31.5.
