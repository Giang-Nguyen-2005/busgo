# M31.3 — Operators & Fleet

Ngày thực hiện:08/11/2026 (Việt Nam). Tiếp tục working tree M31.1/M31.2 trên `main`, HEAD `df8f0b7812c061774c3d1285d5311d9b776cf31f`. Không làm lại catalogue, không sửa migration/seeder cũ, không commit/push/merge/deploy/reset/stash hoặc đọc/sửa/xóa `tatus --short`.

Các counts/schedules planned trong báo cáo này là checkpoint M31.3. Kết quả persist associations/fares/trips và booking smoke ở bước tiếp nối được ghi riêng trong [M31.4 — Schedules & Pricing](m31-4-schedules-pricing.md);19 xe và catalogue vẫn được giữ nguyên.

## 1. Phạm vi và baseline

**Chỉ3 nhà xe hiện tại**, không thêm nhà xe thứ tư: An Phú Express, Minh Thành Limousine, Tây Nguyên Travel. Giữ3 bus types và96 seat templates. Manifest M31.3 chọn **19 xe tổng**, gồm7 legacy +12 xe mới, để chuẩn bị khoảng26 departures/ngày trong tối đa14 ngày. M31.3 chưa tạo associations, fares, trips, crew, inventory hoặc media mới.

Đã đọc [M31.1](m31-1-data-audit-and-expansion-plan.md), [M31.2](m31-2-locations-routes.md), module catalogue, DemoDataSeeder, operator context/staff, auth/password policy, fleet/type/layout/maintenance và marketplace profile APIs. Git diff M31.1/M31.2 giữ nguyên; không có AGENTS.md áp dụng trong repo đã kiểm tra.

Tiếp tục **schema test M31.2 hiện có**, không tạo lại catalogue hoặc reset dữ liệu:

| Mục | Giá trị xác minh trước M31.3 |
| --- | --- |
| Container / Compose |`busgo-m312-catalogue-mysql-1` / `busgo-m312-catalogue`|
| Volume |`busgo-m312-catalogue_m312_data` → `/var/lib/mysql`|
| MySQL / bind |8.4.11 /127.0.0.1:3352|
| Schema |`busgo_m312_test`|
| Flyway |28 successful migrations, max28,0 failed|
| Catalogue |12 locations,25 routes,52 route stops|
| Legacy |3 operators,7 buses,3 operator accounts/memberships,21 trips,882 inventory rows|
| Managed media |0 rows trong schema test này; không suy ra DB ứng dụng không có ảnh|

Chỉ container test này được khởi động lại sau khi kiểm tra labels/mounts/port. Database/container ứng dụng không được khởi động hoặc ghi dữ liệu. Số liệu test không phải số liệu ứng dụng thực tế.

## 2. Hồ sơ3 nhà xe

Mỗi operator có description đề xuất trong `M31Fleet.operators()`: giới thiệu tự nhiên về loại xe và các khu vực hành trình mẫu, đồng thời nói rõ dữ liệu demo, thanh toán mô phỏng, tuyến dự kiến chưa phải dịch vụ mở bán, không tiếp nhận vận chuyển thật và không chứng thực pháp lý/tiện nghi VIP.

Mặc định chỉ **PROPOSE**, không tự sửa profile cũ. Tùy chọn riêng `--busgo.m31.fleet.fill-blank-profiles=true` cho phép điền **public_description đang NULL/trống** dưới operator row lock. Field đã có nội dung khác được PRESERVE, kể cả nội dung người dùng sửa sau lần apply. Rerun nội dung đúng manifest là REUSE. Chỉ description được điền; tên/code/contact/address/status/legal fields/onboarded state/logo và mọi `operator_media` giữ nguyên. Không dùng PUT hồ sơ cũ có tác dụng thay logo.

Phone/address/email legacy nhìn như thông tin liên hệ thật nhưng chưa được xác minh; chúng được giữ để không phá identity/seeder và không được báo cáo này chứng thực. Không tạo hotline, legal code, website hoặc địa chỉ vận hành mới. M31.5 mới xử lý bổ sung media.

## 3. Accounts và quyền

| Operator | Có sẵn, giữ nguyên | Account mới trong manifest |
| --- | --- | --- |
| An Phú |`operator.admin@anphu-demo.example` + `operator.staff@anphu-demo.example`|Không thêm|
| Minh Thành |`operator.admin@minhthanh-demo.example`|`operator.staff@minhthanh-demo.example`, OPERATOR_STAFF, code M31-MT-STAFF|
| Tây Nguyên |Chưa có account legacy do seed tạo|`operator.admin@taynguyen-demo.example`, OPERATOR_ADMIN, code M31-TN-ADMIN; `operator.staff@taynguyen-demo.example`, OPERATOR_STAFF, code M31-TN-STAFF|

Mỗi account mới có đúng1 role và1 membership của owner. Không SYSTEM_ADMIN/mixed role/multiple tenants. Phone bắt buộc của account dùng text `DEMO-NOT-DIALABLE`, không phải số liên hệ vận hành; profile operator không quảng bá nó như hotline.

Credential mới chỉ nhận qua cấu hình bên ngoài source:

| Account | Property / environment tương ứng |
| --- | --- |
| Minh Thành staff |`busgo.m31.fleet.minhthanh-staff-password` / `BUSGO_M31_FLEET_MINHTHANH_STAFF_PASSWORD`|
| Tây Nguyên admin |`busgo.m31.fleet.taynguyen-admin-password` / `BUSGO_M31_FLEET_TAYNGUYEN_ADMIN_PASSWORD`|
| Tây Nguyên staff |`busgo.m31.fleet.taynguyen-staff-password` / `BUSGO_M31_FLEET_TAYNGUYEN_STAFF_PASSWORD`|

Không có default password trong module. `PasswordPolicy` yêu cầu ít nhất8 ký tự, tối đa72 byte UTF-8; lưu bcrypt12 như ứng dụng. Không ghi secret vào report/log/tài liệu/Git hoặc command-line arguments. Credential cho rehearsal được sinh ngẫu nhiên, lưu local ignored `.tools/m31-3/fleet-secrets.local.json`; giá trị không được in trong bàn giao. Khi chạy ở môi trường demo khác, cấp secret riêng qua secret/config phù hợp.

Reuse không yêu cầu secret và không so/đổi password hash. Legacy account được phép giữ tên/phone/password/status đã được người dùng chỉnh, nhưng role/membership phải đúng identity dành riêng. Missing legacy account, staff-code/email collision, sai owner/mixed role hoặc account mới bị đổi identifying fields → CONFLICT. Không khôi phục account/membership bị khóa/suspend. Không đủ dedicated active admin thì bỏ qua import operator đó, không tạo quyền bypass.

Staff đọc các màn hình vận hành được contract cho phép, không quản lý fleet/accounts/finance. Fleet management API hiện dành cho admin; không mở quyền mới cho staff chỉ để demo.

## 4. Fleet manifest và phân bổ

Các plate mới dạng `DEMO-M31-*` là **định danh giả lập**, không phải biển xe hoạt động ngoài đời. Bus mới tạo qua `BusService.create`: resolve owner từ dedicated active admin, resolve bus type hiện hữu, validate layout bằng `BusTypeService.requireUsable`, unique plate, status AVAILABLE. Không gán image_url hoặc tạo layout/inventory mới.

| Operator | Legacy | Xe mới | Tổng đích | Limousine22 / Sleeper34 / Standard40 |
| --- | --- | --- | --- | --- |
| An Phú |3|4|7|0 /4 /3|
| Minh Thành |2|4|6|3 /2 /1|
| Tây Nguyên |2|4|6|1 /3 /2|
| **Tổng** |**7**|**12**|**19**|**4 /9 /6**|

| Operator | Plate mới | Type | Vai trò dự kiến |
| --- | --- | --- | --- |
| An Phú |DEMO-M31-AP-01|Sleeper34|HCM↔Nha Trang|
| An Phú |DEMO-M31-AP-02|Standard40|HCM↔Phan Thiết|
| An Phú |DEMO-M31-AP-03|Sleeper34|Dự phòng|
| An Phú |DEMO-M31-AP-04|Standard40|Dự phòng|
| Minh Thành |DEMO-M31-MT-01|Limo22|HCM↔Vũng Tàu|
| Minh Thành |DEMO-M31-MT-02|Standard40|Đà Nẵng↔Huế|
| Minh Thành |DEMO-M31-MT-03|Limo22|Dự phòng|
| Minh Thành |DEMO-M31-MT-04|Sleeper34|Dự phòng|
| Tây Nguyên |DEMO-M31-TN-01|Sleeper34|Nha Trang↔Quy Nhơn|
| Tây Nguyên |DEMO-M31-TN-02|Sleeper34|Quy Nhơn↔Đà Nẵng|
| Tây Nguyên |DEMO-M31-TN-03|Standard40|Đà Nẵng↔Quảng Ngãi|
| Tây Nguyên |DEMO-M31-TN-04|Sleeper34|Dự phòng|

Legacy buses không đổi plate/type/owner/status/image/deleted_at. Unavailable hoặc soft-deleted bus trùng identity vẫn được giữ nguyên và report là chưa sẵn sàng, không tạo bản thay thế tự động. Plate trùng sai owner/type là conflict, không chiếm dụng xe của nhà xe khác.

## 5. Vòng xe và operator-route matrix cho M31.4

Giả định mô phỏng: mỗi block dùng1 bus riêng, buffer tối thiểu90 phút giữa2 lượt và trước lượt kế tiếp; phút hành trình lấy catalogue M31.2, không phải cam kết vận hành thật. Đây là nhu cầu tối thiểu **theo cách phân block này**, không phải bài toán tối ưu toàn hệ thống. Cần13 primary buses,5 spare và1 legacy coastal giữ riêng =19.

| Owner / bus | Vòng đi–về | Type | Phút/chiều | Đi / về (giờ VN) | Số xe block |
| --- | --- | --- | --- | --- | --- |
| AP /51B-770.01|HCM↔Đà Lạt|Sleeper34|420|06:30 /15:00|1|
| AP /DEMO-M31-AP-01|HCM↔Nha Trang|Sleeper34|540|06:00 /16:30; về HCM01:30 hôm sau|1|
| AP /51B-770.02|HCM↔Cần Thơ|Standard40|240|06:00 /11:30|1|
| AP /DEMO-M31-AP-02|HCM↔Phan Thiết|Standard40|270|06:00 /12:00|1|
| MT /50F-880.01|HCM↔Đà Lạt|Limo22|420|08:00 /16:30|1|
| MT /DEMO-M31-MT-01|HCM↔Vũng Tàu|Limo22|150|06:00 /10:00|1|
| MT /50F-880.02|Đà Lạt↔Nha Trang|Sleeper34|210|06:00 /11:00|1|
| MT /DEMO-M31-MT-02|Đà Nẵng↔Huế|Standard40|180|06:00 /10:30|1|
| TN /47B-660.01|HCM↔BMT|Standard40|480|06:00 /15:30|1|
| TN /47B-660.02|BMT↔Pleiku|Limo22|240|06:00 /11:30|1|
| TN /DEMO-M31-TN-01|Nha Trang↔Quy Nhơn|Sleeper34|300|06:00 /12:30|1|
| TN /DEMO-M31-TN-02|Quy Nhơn↔Đà Nẵng|Sleeper34|420|18:00 /02:30 ngày sau outbound|1|
| TN /DEMO-M31-TN-03|Đà Nẵng↔Quảng Ngãi|Standard40|180|06:00 /10:30|1|

13 blocks×2 legs = **26 departures/ngày**, 364 proposed departures cho14 ngày. Code `M31Fleet.plan(anchor,days)` là phép tính thuần, **không persist trips**. `validate()` kiểm tra owner, uniqueness, chronological overlap, vị trí nối tiếp và90 phút buffer trên14 ngày. Có arrival qua ngày; không cộng/trừ ngày bằng text giờ đơn thuần.

Khởi tạo vị trí mô phỏng phải explicit: xe bắt đầu ở origin của leg đầu trong cửa sổ; riêng TN-02 bắt đầu Đà Nẵng để chạy lượt về02:30, đến Quy Nhơn09:30 rồi đi18:00. Sau đó mọi leg đều nối đúng vị trí, không teleport. Không suy ra vị trí thực từ bảng buses vì schema không có trường vị trí hiện tại. Nếu M31.4 chỉ bắt đầu từ Quy Nhơn cho TN-02 thì ngày đầu thiếu lượt về và phải báo giảm số departure, không backdate một chuyến giả.

Ngày cuối có thể đến sau cửa sổ; không tạo departure ngoài14 ngày để ép đủ cặp. Dừng xe ở destination của lượt cuối hoặc ghi rõ kế hoạch nghỉ. Nhân sự/giờ lái/nghỉ của con người chưa được lập; chặng dài cần bố trí ca/crew phù hợp trước demo boarding, không coi buffer xe là chứng minh đủ crew.

`51B-770.03` giữ các trips COASTAL legacy, không đưa vào26 departures/ngày mới. COASTAL có chiều đi21h nhưng chưa có reverse route; không lặp HCM departure mỗi ngày trên xe này bằng giả định tự quay về. Muốn mở rộng coastal phải có thiết kế chiều về/điều chuyển explicit và đủ thời gian, là quyết định M31.4 riêng.

**Legacy trips vẫn tồn tại và không được sửa.** M31.4 chỉ apply sau khi đối chiếu lịch/bảo trì thật; chọn anchor sau lịch legacy đang chiếm xe và xác nhận initial base positioning, hoặc giảm coverage/skip slot. Không ép lịch26/day chồng lên21 trips cũ. Xe MAINTENANCE/INACTIVE/deleted không được schedule;5 spare không có nghĩa mọi layout đều có dự phòng đầy đủ trong từng operator.

Matrix đề xuất tương đương26 associations direct cho24 directional routes, trong đó HCM↔Đà Lạt có2 nhà xe AP/MT và khác giờ/type. Các tuyến còn lại1 nhà xe theo bảng. Legacy TN HCM↔Đà Lạt vẫn giữ nhưng không bắt buộc chạy thêm trong lịch26/day này.

**Persisted operator_routes hiện giữ13 legacy**, không thêm association ở M31.3. Trong26 associations dự kiến,10 đã có và16 mới chỉ nằm trong kế hoạch. Nếu M31.4 apply tất cả16 mà không đổi legacy thì tổng29 associations (gồm coastal và TN Đà Lạt legacy ngoài daily plan). Cần apply association cùng exact forward fares và eligibility phù hợp trước khi public discovery quảng bá; M31.3 không thêm CTA/tuyến công khai chưa có chuyến.

## 6. Module và cách chạy an toàn

File mới dưới `demo/m31/`:

- `M31Fleet`: version m31.3-v1,3 operator descriptions,6 account identities,19 vehicles và13 rotation proposals.
- `M31FleetService`: preflight/target guard kế thừa catalogue, resolve existing layouts/owners, per-operator conflict report, create-only accounts/fleet và explicit blank-only profile fill.
- `M31FleetRunner`: standalone command context, không component scan làm startup runner. Không Flyway, legacy seeder, bootstrap admin, web hoặc scheduled jobs; tự đóng context sau report.

Dùng lại schema guard M31.2: dev, không demo/prod/production, loopback explicit port, `busgo_m312_*` khớp `busgo.m31.catalogue.verified-schema`, V28, catalogue FULL chỉ REUSE. Không có tác dụng auto hoàn thiện M31.2. Layout hỏng/khác3 type mong đợi phải fail preflight, không sửa. Apply serialize bằng role CUSTOMER lock chung M31.2 và operator row locks theo ID; refresh operator sau lock để không ghi profile từ bản đọc cũ.

Nếu một account/bus identity trong operator conflict hoặc thiếu credential hợp lệ cho account cần tạo, bỏ qua **toàn operator đó** để tránh tạo nửa bộ quyền/fleet. Các operator độc lập hợp lệ có thể apply; report có SKIP/CONFLICT/NEEDS_SECRET phải được đọc, không xem partial import là complete. Unique-constraint race/unexpected exception rollback transaction, không upsert/repair. Không chạy đồng thời command catalogue, fleet và công cụ quản trị ghi cùng bộ demo.

Tạo account dùng cùng PasswordPolicy/bcrypt, JPA roles/membership constraints và create-only identity checks; bootstrap admin đầu tiên của Tây Nguyên chỉ nằm trong importer đã guard, không thêm API cấp quyền hoặc SYSTEM_ADMIN. Tạo bus qua BusService với actor là dedicated active admin được resolve từ DB; context tạm chỉ trong import và được restore, không thay role của user hay bỏ owner/layout guards.

Từ project root, JDK17, DB env đã trỏ đúng schema test; cấp3 secret environment ở trên bằng kênh local riêng, không in giá trị:

```powershell
mvn.cmd -o -f backend/pom.xml spring-boot:run `
  '-Dspring-boot.run.main-class=com.busgo.demo.m31.M31FleetRunner' `
  '-Dspring-boot.run.profiles=dev' `
  '-Dspring-boot.run.arguments=--busgo.m31.fleet.enabled=true --busgo.m31.catalogue.verified-schema=busgo_m312_test --busgo.m31.fleet.fill-blank-profiles=true'
```

Mặc định dry-run=true; profile flag vẫn chỉ đề xuất FILL_BLANK trong dry-run. Xem report `M31_FLEET_REPORT=` và xác minh target độc lập trước apply. Apply dùng cùng command thêm `--busgo.m31.fleet.dry-run=false`. Rerun phải0 CREATED/FILLED_BLANK; không cần cấp secret nếu các account đã tồn tại đúng identity, không reset password khi secret cấu hình thay đổi.

## 7. Kết quả dry-run / apply / rerun

Đã apply và commit thật **trong schema test hiện hữu `busgo_m312_test`**, sau focused tests rollback và standalone dry-run. Không reset/reseed M31.2. Receipt local ignored: `backend/target/m31-3-acceptance.json`.

| Dữ liệu | Trước | Sau persist / rerun |
| --- | --- | --- |
| Operators |3|3, cùng code/name/contact/status/legal/logo|
| Description có nội dung |0|3, chỉ điền NULL/trống với explicit flag|
| Buses AP /MT /TN |3 /2 /2|7 /6 /6|
| Tổng buses |7|19 =7 legacy +12 mới|
| Types / seat templates |3 /96|3 /96, snapshot nội dung không đổi|
| Users / user_roles / operator_staff |3 /3 /3|6 /6 /6|
| Locations / routes |12 /25|12 /25|
| Operator routes / fares |13 /18|13 /18|
| Trips / inventory |21 /882|21 /882|
| Bookings / payments / tickets |0 /0 /0|0 /0 /0|
| Managed media |0|0; không upload/sửa ảnh|

Standalone dry-run trả12 BUS CREATE,3 ACCOUNT CREATE,3 PROFILE FILL_BLANK; count sau lệnh vẫn7 buses/3 accounts. Acceptance apply tạo đúng12 buses +3 accounts, điền3 description. Rerun trả **6 ACCOUNT REUSE,19 BUS REUSE,3 PROFILE REUSE**,0 CREATED/FILLED_BLANK/CONFLICT; snapshot toàn schema trước/sau rerun trùng nhau.

Đã đối chiếu SQL sau apply: type distribution4 Limo22/9 Sleeper34/6 Standard40 như bảng mục4; cả3 operator ACTIVE, description có nội dung, logo_url vẫnNULL. Test snapshots còn so sánh legacy rows/credentials và toàn bộ bảng không thuộc phạm vi import; không chỉ so sánh counts. Baseline commerce trống trong schema này, không tuyên bố đã có populated booking history ở ứng dụng thực tế.

Đây là **test database**, không phải database ứng dụng. Manifest circulation26/day vẫn chỉ là kế hoạch: không có364 trips mới trong DB, không persist16 associations dự kiến hoặc giá vé mới.

## 8. Kiểm chứng và giới hạn

Tests tập trung: pure circulation/buffer/19-vehicle plan; dry-run snapshot toàn schema; create-only/rerun, preserve credentials/legacy commerce/catalogue; collision owner/account; custom profile/media/unavailable bus; login account mới và admin/staff/tenant fleet APIs. Dữ liệu test thông thường rollback. Acceptance persist là opt-in riêng trên baseline M31.2 chưa mở fleet; không reset để chạy lại.

```powershell
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration `
  '-Dbusgo.m31.fleet.integration=true' `
  '-Dtest=M31FleetTest,M31CatalogueTest' '-Dit.test=M31FleetIT'
# Sau focused tests, chỉ trên schema test baseline đã xác minh:
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration `
  '-Dbusgo.m31.fleet.acceptance=true' `
  '-Dtest=M31FleetTest' '-Dit.test=M31FleetAcceptanceIT'
```

| Kiểm chứng | Kết quả | Evidence local ignored |
| --- | --- | --- |
| Compile |PASS|`.tools/m31-3/compile.log`|
| Focused unit |3/3: Fleet plan1 + catalogue target guard2|`focused.log`|
| Focused MySQL integration |4/4: dry/create/profile/rerun/preservation; owner/account collisions; custom profile/media/unavailable bus; login/role/tenant API|`focused.log`|
| Persistent acceptance |1/1 unit plan lặp lại +1/1 MySQL acceptance, BUILD SUCCESS|`acceptance.log`, receipt|
| Standalone dry-run |PASS, tự đóng context, không ghi|`command-dry.log`|
| Standalone apply rerun |PASS khi không cấp lại account secrets;28 entries đều REUSE,0 create/fill/conflict|`command-rerun.log`|
| Credential hygiene |Không tìm thấy plaintext secret mới trong13 source/log/report artifacts đã scan; secret file được Git ignore|Kiểm tra cuối task, không in giá trị|
| Static / docs |`git diff --check`, local links và whitespace PASS|Kiểm tra cuối task|

Không cộng repeated unit plan thành test mới. Các test M31.3 opt-in riêng, không tự persist fleet khi chạy full suite trên schema khác. Plaintext credential không xuất hiện trong report; các test login dùng secret sinh ngẫu nhiên và không in token/password.

Media preservation dùng một row bytes fixture trong transaction rollback để kiểm tra importer không chạm bytes/metadata, không phải lượt upload M30.5 E2E mới. API smoke là Spring Security/MockMvc/MySQL thật: fleet list đúng7/6/6, foreign bus404, staff đọc trips200 nhưng mutation fleet/staff management403. Account mới đăng nhập qua AuthService thật; legacy password hash/contact giữ nguyên bằng snapshot assertions.

Không sửa frontend nên không cần frontend build mới. Không chạy lại toàn bộ M30.6, không tuyên bố browser E2E hoặc production acceptance.

## 9. Việc dành cho M31.4

Chốt anchor/horizon≤14 ngày, availability/maintenance và initial placement từng xe; resolve routes/associations/forward fares từ đúng generated IDs; apply16 associations còn thiếu nếu kế hoạch này được duyệt; tạo snapshots qua TripAggregateCreator, không raw SQL trips/inventory. Giá vẫn theo operator/journey, không hứa per-type pricing cùng association.

Kế hoạch26 departures/day và364 slots/14 ngày là trần đề xuất hiện tại, chưa có trip mới tương ứng trong DB. Đối chiếu legacy và crew trước khi xác nhận lịch; giảm tần suất khi không đủ điều kiện thay vì thêm xe/nhà xe ngoài scope. Giữ nguyênM31.1/M31.2 và dữ liệu giao dịch, không tự triển khai M31.4 trước review.

## 10. Trạng thái bàn giao

**M31.3 hoàn tất trong phạm vi source và schema test đã xác minh.** Chỉ bổ sung3 production files và3 test files M31.3, tài liệu này và ghi chú nối tiếp ở các báo cáo M31 trước; các thay đổi M31.1/M31.2 được giữ nguyên. Không sửa frontend, migrations, DemoDataSeeder, nghiệp vụ commerce hoặc media.

Container test đã được dừng sau kiểm chứng; giữ volume `busgo-m312-catalogue_m312_data`, secret file local ignored, logs và receipt. Các container/database ứng dụng không thay đổi. Không có web server/scheduler M31 để lại chạy nền.

Dừng để người dùng review trước **M31.4 — Schedules & Pricing**. Không tự tạo lịch, fare hoặc association mới trong lượt này.
