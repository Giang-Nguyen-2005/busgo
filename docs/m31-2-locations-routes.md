# M31.2 — Locations & Routes

Ngày thực hiện: 08/11/2026 (Việt Nam). Baseline `main`, HEAD `df8f0b7812c061774c3d1285d5311d9b776cf31f` — `Complete M30 and fix seat map hover UI`.

Số liệu ở đây là checkpoint M31.2 trước mở rộng fleet. Bước tiếp nối [M31.3 — Operators & Fleet](m31-3-operators-fleet.md) chốt đúng3 operators và báo cáo riêng fleet/accounts đã persist sau checkpoint này; catalogue M31.2 được giữ nguyên.

## 1. Phạm vi theo quyết định người dùng

M31 ưu tiên **3 nhà xe hiện tại**, tối đa4 nếu thực sự cần; giữ3 bus types, dự kiến16–24 xe tổng,20–30 chuyến/ngày, cửa sổ tối đa14 ngày, khoảng20–25 tuyến có hướng. Đã cập nhật toàn bộ mục tiêu, fleet/accounts/media estimates, volume calculations và roadmap trong [M31.1](m31-1-data-audit-and-expansion-plan.md). Không còn mục tiêu6 operators/48 xe/48 chuyến mỗi ngày.

M31.2 chỉ triển khai catalogue:12 điểm đến,24 direct routes và giữ1 COASTAL =25 routes. Không thêm operator/bus/type, association, fare, trip hoặc giao dịch. Catalogue rộng không yêu cầu mọi route đều có chuyến mỗi ngày; M31.4 phải bố trí tần suất luân phiên trong giới hạn fleet/chuyến đã chốt.

## 2. Manifest và nội dung

`M31Catalogue.VERSION=m31.2-v1`. Stable keys HCM/DALAT/BMT/NHATRANG/DANANG/HUE giữ nguyên tên/nhãn legacy để không phá seeder và ảnh. Sáu keys mới: CANTHO/VUNGTAU/PHANTHIET/QUYNHON/QUANGNGAI/PLEIKU, tên `Điểm đón demo …`. Province/district/address/latitude/longitude mới để NULL vì chưa xác minh. Không dùng tên bến/doanh nghiệp hoặc tọa độ tự bịa. Stable keys nằm trong manifest, không giả schema có cột code/provenance.

12 cặp hai chiều:

| Cặp | Km / phút mẫu mỗi chiều | Tình trạng |
| --- | --- | --- |
| HCM ↔ Đà Lạt |310 /420|Legacy, giữ nguyên|
| HCM ↔ Buôn Ma Thuột |350 /480|Legacy, giữ nguyên|
| HCM ↔ Nha Trang |430 /540|Legacy, giữ nguyên|
| Đà Nẵng ↔ Huế |100 /180|Legacy, giữ nguyên|
| HCM ↔ Cần Thơ |170 /240|Pilot mới|
| HCM ↔ Vũng Tàu |100 /150|Pilot mới|
| HCM ↔ Phan Thiết |200 /270|Full mới|
| Nha Trang ↔ Quy Nhơn |220 /300|Full mới|
| Quy Nhơn ↔ Đà Nẵng |320 /420|Full mới|
| Đà Nẵng ↔ Quảng Ngãi |130 /180|Full mới|
| Buôn Ma Thuột ↔ Pleiku |180 /240|Full mới|
| Đà Lạt ↔ Nha Trang |140 /210|Full mới|

Các km/phút mới là tham số **mô phỏng**, chưa xác minh báo giá/tuyến vận hành thật. Route mới có prefix `Demo ·`, ACTIVE để hiện trong catalogue quản lý, không có association/fare/trip nên chưa thể bán. Mỗi direct route có2 stops: đầu order1/offset0/pickup-only, cuối order2/offset=duration/dropoff-only. `RouteDefinitionService.requireUsable` kiểm tra geometry trước khi transaction commit. COASTAL chỉ đọc/đối chiếu và báo REUSE/CONFLICT, không được M31 tạo/sửa.

## 3. Module và an toàn dữ liệu

Package mới `backend/src/main/java/com/busgo/demo/m31/`:

- `M31Catalogue`: versioned specs, PILOT/FULL, validation keys/endpoints/duration.
- `M31CatalogueService`: preflight/resolution/plan/create-only/report, transaction READ_COMMITTED.
- `M31CatalogueRunner`: command riêng với minimal Spring context. Không được đăng ký như ApplicationRunner của app; không chạy khi startup thông thường. Command không load Flyway, web, seeder cũ, admin bootstrap hoặc scheduled jobs.

Không sửa migrations, DemoDataSeeder hay identity dữ liệu legacy. Repository bổ sung lookup candidates; chỉ persist entity mới bằng JPA. Stop/route mới đi qua production route geometry guard. Không raw insert catalogue bỏ qua validation.

Target phải đồng thời: profile dev, không demo/prod/production; JDBC loopback MySQL với port explicit; schema tên `busgo_m312_*`; property `busgo.m31.catalogue.verified-schema` khớp `SELECT DATABASE()`; đủ28 successful migrations và không failed history. Guard này không thay việc người vận hành xác minh container/project/volume/DB_URL độc lập. Không dùng health/localhost đơn thuần làm bằng chứng được phép ghi.

Apply serialize các run hợp tác bằng row lock role CUSTOMER (không sửa role). Dry-run không lấy write lock. Dữ liệu reuse giữ nguyên timestamps/status/credentials. Name/endpoint trùng nhưng khác identity, duplicate candidates, stop order/offset/flags thay đổi, thiếu baseline hoặc dependency chưa resolve → CONFLICT, bỏ qua an toàn và report. Các nhánh độc lập hợp lệ vẫn có thể được tạo; không gọi partial conflict là hoàn tất toàn bộ catalogue. Một lỗi unexpected/production geometry fail rollback cả batch. Không tự sửa record để hết collision.

Không có unique natural keys mới: lock chỉ serialize các M31 runner hợp tác. Công cụ ghi catalogue khác không dùng lock này phải ngừng trong lúc apply, hoặc bổ sung coordination ở task riêng nếu cần concurrency đa nguồn.

## 4. Môi trường kiểm chứng và catalogue trước/sau

Database ứng dụng hiện tại không được khởi động/đọc/ghi. Các container cũ `busgo-local-demo-mysql-1`, `busgo-mysql-1`, M30.5 và M30.6 vẫn exited trước khi chuẩn bị test. File `tatus --short` không được đọc/sửa/xóa.

Môi trường mới dành riêng cho task:

| Thuộc tính | Giá trị đã kiểm tra |
| --- | --- |
| MySQL |8.4.11, image mysql:8.4|
| Bind |127.0.0.1:3352 →3306|
| Container |busgo-m312-catalogue-mysql-1|
| Compose project |busgo-m312-catalogue|
| Volume |busgo-m312-catalogue_m312_data →/var/lib/mysql|
| Schema |busgo_m312_test|
| Trạng thái đầu |0 bảng, trước test migrations; không adopt volume cũ|
| Flyway |V1–V28 được áp dụng trên schema mới; không sửa migration|

Compose file/evidence local ignored: `.tools/m31-2/`. Credentials test chỉ dùng cho instance cô lập này, không đưa vào tài liệu hoặc command log.

Kết quả **đã tạo và commit thật trong schema test** qua acceptance rehearsal (không rollback lượt này):

| Bảng | Legacy baseline | Sau PILOT | Sau FULL / rerun |
| --- | --- | --- | --- |
| locations |6|8|12|
| routes |9|13|25|
| route_stops |20|28|52|
| transport_operators / bus_types / buses |3 /3 /7|3 /3 /7|3 /3 /7|
| users / operator_staff |3 /3|3 /3|3 /3|
| operator_routes / operator_route_fares |13 /18|13 /18|13 /18|
| trips / trip_stops / trip_segments |21 /48 /27|21 /48 /27|21 /48 /27|
| trip_seats / inventory |678 /882|678 /882|678 /882|
| bookings / payments / tickets |0 /0 /0|0 /0 /0|0 /0 /0|

PILOT tạo2 locations +4 routes +8 stops. FULL tiếp tục tạo4 locations +12 routes +24 stops. Tổng delta M31.2: **6 locations,16 routes,32 stops**. Rerun FULL trả toàn REUSE,0 CREATED/CONFLICT; count trước/sau rerun trùng nhau. Dry-run trước mỗi batch không đổi count; focused tests còn so sánh toàn bộ nội dung các hàng cũ và rerun snapshots. COASTAL4 stops cùng toàn bộ21 trips legacy/inventory giữ nguyên.

Receipt chi tiết: `backend/target/m31-2-acceptance.json` (local ignored). Đây là số của **test database**; không dùng nó để khẳng định database ứng dụng hiện tại có12/25 records. Trip legacy21 được tạo riêng ở bước chuẩn bị baseline bằng DemoDataSeeder, không phải trip do module M31 tạo.

## 5. Cách chạy dry-run và apply có kiểm soát

Chỉ dùng **schema test/demo cô lập đã xác minh**; module chủ động từ chối DB ứng dụng thông thường. Không chạy `start-busgo.ps1` để import. Dừng công cụ ghi catalogue khác; xác nhận container labels/mounts, DB_URL/schema/user/volume; đọc Flyway history và counts trước. Baseline6 locations/8 direct routes/COASTAL phải được chuẩn bị riêng bằng fixture demo đã review. Module không tự chạy legacy seeder khi thiếu baseline.

JDK17/Maven, từ project root; truyền DB_URL/DB_USERNAME/DB_PASSWORD bằng environment riêng, không paste mật khẩu vào command. Sau đó:

```powershell
# Không bật profile demo: tránh chạy legacy seed.
mvn.cmd -o -f backend/pom.xml spring-boot:run `
  '-Dspring-boot.run.main-class=com.busgo.demo.m31.M31CatalogueRunner' `
  '-Dspring-boot.run.profiles=dev' `
  '-Dspring-boot.run.arguments=--busgo.m31.catalogue.enabled=true --busgo.m31.catalogue.verified-schema=busgo_m312_test --busgo.m31.catalogue.batch=PILOT'
```

Mặc định dry-run=true, output một dòng `M31_CATALOGUE_REPORT=` JSON. CREATE là kế hoạch, không phải dòng đã ghi. REUSE không cập nhật record; CONFLICT cần review. Command tự đóng context sau report; không để app nền tiếp tục chạy.

Sau khi xem report và xác nhận target, chạy cùng command với argument thêm `--busgo.m31.catalogue.dry-run=false`. Apply trả CREATED kèm generated IDs. Không hardcode ID để tạo route; resolve từ manifest identity và record được xác minh. Rerun PILOT phải không tạo thêm record. Sau pilot đạt, đổi batch=FULL: dry-run trước, apply sau, rồi rerun để kiểm tra0 CREATED. Không dùng command này để tự động nhập vào môi trường có khách thật.

Flyway bị loại khỏi context command: schema thiếu/chưa V28 sẽ bị từ chối, command không tự upgrade/repair. Các integration tests có context riêng chạy migration để chuẩn bị schema test mới; đó là bước setup test, không phải tác dụng của dry-run command.

## 6. API / frontend compatibility

Endpoint additive **GET `/api/v1/locations/lookup?ids=…`**:1–100 positive IDs, chỉ trả location ACTIVE hiện hữu, bỏ qua missing/inactive, sắp ID ổn định, DTO chỉ id/name/province/district như autocomplete. Request invalid/>100 trả400. GET public qua security allowlist, không mở quyền mutation. Autocomplete `/locations?q=` vẫn giới hạn20, không tăng LIMIT.

Home lấy đúng tập origin/destination IDs của tối đa8 discovery routes; SearchForm resolve đúng2 selected IDs. Query keys chứa tập IDs để tránh dùng nhầm cache khi đổi route. Không dùng q rỗng/page đầu làm registry. Giữ6 ảnh cũ, exact-name mapping và carousel một hàng/card/UI cũ. Location mới chưa có ảnh dùng fallback hiện hữu; không thêm asset hoặc redesign.

Route discovery giữ contract và giới hạn8, profile30. Query hiện yêu cầu ACTIVE route/association/operator;16 route mới không có association nên chỉ có trong catalogue quản lý. Không tạo association giả hoặc public CTA cho chúng. Routes public legacy vẫn là “chọn ngày khởi hành”, không hứa có trip hôm đó. Curation theo chuyến thực/đa destination là quyết định giai đoạn schedule sau, không tăng LIMIT để giả đủ coverage. Không thay Search/quote/booking/inventory flow.

## 7. Kiểm chứng tập trung

Test mới tập trung vào catalogue/dry-run/pilot/full/rerun/preservation/collision/geometry/COASTAL, target guard và exact lookup vượt20. Một acceptance rehearsal riêng commit vào **schema test mới**, tạo receipt `backend/target/m31-2-acceptance.json`; test này yêu cầu catalogue trống, không reset và không replay trên schema đã có dữ liệu.

| Lượt kiểm chứng | Kết quả | Evidence local ignored |
| --- | --- | --- |
| Focused backend |8/8 unit/foundation;35/35 MySQL integration: M31 catalogue4, M5 search11, M24A marketplace20;0 fail/error/skip|`.tools/m31-2/focused-permitted.log`|
| Recheck standalone design |2/2 target guard unit +4/4 catalogue integration; BUILD SUCCESS|`final-focused.log`|
| Final inactive-endpoint check + committed rehearsal |2/2 guard unit;2/2 integration (1 repeated status/collision case +1 new acceptance); BUILD SUCCESS|`acceptance.log` và receipt|
| Standalone dry-run command |FULL, dryRun=true,37 entries REUSE,0 CREATE/CONFLICT; tự đóng context, BUILD SUCCESS|`.tools/m31-2/command-dry.log`|
| Standalone apply rerun command |FULL, dryRun=false,37 REUSE,0 CREATED/CONFLICT; SQL sau lệnh vẫn12 locations/25 routes/52 stops/21 trips/3 operators|`.tools/m31-2/command-rerun.log`|
| Frontend focused |11/11: M31 carousel, M30.2A journey, M30.3A mapping, final assets|Các4 test files tương ứng trong frontend/tests|
| Frontend production build |TypeScript/Vite PASS; có warnings annotation/import đã tồn tại|`.tools/m31-2/frontend-build.log`|
| Static checks |`git diff --check` PASS; docs local links/whitespace PASS; shared browser fixture syntax PASS|Kiểm tra cuối task|

Không cộng các rerun thành số test duy nhất. Các test catalogue thông thường dùng transaction rollback; chỉ acceptance riêng persist dữ liệu vào schema test. Hai integration harness M31 yêu cầu system-property opt-in riêng bên dưới, để không chạy nhầm/persist dữ liệu khi một full suite trỏ schema khác; target guard vẫn bắt buộc. API smoke qua MockMvc + Spring Security + MySQL thật: autocomplete12 rows, exact lookup, public discovery200 và không lộ route mới chưa có association. Không có browser live E2E mới và không chạy lại toàn bộ M30.6. Fixture browser M29 được bổ sung response cho lookup IDs, giữ các assertions cũ; chưa tuyên bố đã rerun browser suite đó.

Các lệnh chính (DB env trỏ schema cô lập3352, JDK17):

```powershell
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration `
  '-Dbusgo.m31.integration=true' `
  '-Dtest=M31CatalogueTest,FoundationTest' `
  '-Dit.test=M31CatalogueIT,M5TripSearchIT,M24AMarketplaceIT'
# Chạy các rollback tests trước acceptance. Acceptance yêu cầu catalogue trống;
# không xóa/reset schema đã có dữ liệu để cố chạy lại.
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration `
  '-Dbusgo.m31.acceptance=true' `
  '-Dtest=M31CatalogueTest' '-Dit.test=M31CatalogueAcceptanceIT'
npm.cmd --prefix frontend run build
# Từ frontend/:
node --experimental-strip-types --test tests/m31-2-catalogue.test.mjs tests/m30-2a.test.mjs tests/m30-3a.test.mjs tests/m30-3-final.test.mjs
```

Lượt Maven đầu trong sandbox bị dừng do JVM test context không tiến triển; không tính là pass. Vite build đầu bị Windows sandbox chặn realpath; đã rerun ngoài sandbox và đạt. Không thay source để né các kiểm tra hay làm yếu assertions.

## 8. Giới hạn và chuẩn bị M31.3

- Source catalogue không đồng nghĩa tất cả route bookable. M31.3/M31.4 mới chuẩn bị owner/association/fleet/fare/lịch hợp lý; không thực hiện sớm trong task này.
- Mặc định chỉ3 operator. Hoàn thiện dedicated admin còn thiếu và profile demo; giữ3 bus types/layouts. Fleet16–24 xe gồm7 cũ, không tự thêm nhà xe thứ tư khi chưa có lý do.
- Location labels mới là mô phỏng; chưa có verified address/coordinates/admin geography. Giữ legacy administrative text như identity, không chứng thực địa giới hiện hành.
- Catalogue dùng stable natural identity với conservative matching, không có provenance table. User edit khác cấu trúc có thể gây conflict có chủ đích; không nhận quyền overwrite từ prefix Demo.
- Chưa mở rộng public route pagination/curation hoặc per-type pricing. Không fix toàn bộ legacy status policy/reporting/browser issues của M30 bằng task data này.
- DB ứng dụng chưa được seed M31. Không commit/push/merge/deploy/reset/stash. Hoàn tất kiểm chứng rồi dừng để review trước M31.3.

## 9. Trạng thái bàn giao

**M31.2 hoàn tất trong phạm vi source và schema test cô lập đã nêu.** Đã dừng container `busgo-m312-catalogue-mysql-1` với exit0; giữ volume, receipt và logs. Các container cũ giữ trạng thái exited, không có thay đổi database ứng dụng. Không có service web M31 để lại chạy nền.

Các file thay đổi ngoài module: LocationController/Service/Repository (exact lookup), RouteRepository (resolver candidates), SecurityConfig (GET lookup allowlist), Marketplace/SearchForm (ID lookup), fixture M29 customer (lookup response), tests M31 catalogue/acceptance/target guard/carousel và2 tài liệu M31. Không thay migration, DemoDataSeeder, booking/payment/inventory engine, ảnh hoặc thiết kế carousel.

Bước tiếp theo sau review: **M31.3 — hoàn thiện3 operator hiện tại và kế hoạch fleet16–24 xe**, trước khi tạo fares/schedules. Catalogue25 routes hiện chưa đồng nghĩa25 routes có thể đặt vé. Không tự triển khai M31.3 trong task này.
