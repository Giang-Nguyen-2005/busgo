# M31.5 — Supporting Data & Content

Ngày 08/11/2026, giờ Việt Nam. Workspace `C:\Users\Giang\busgo`, branch `main`, HEAD `df8f0b7812c061774c3d1285d5311d9b776cf31f`. Tiếp tục working tree M31.1–M31.4; không commit/push/merge/deploy/reset/stash. Không đọc/sửa/xóa file `tatus --short`.

## 1. Baseline test database

Đã đọc báo cáo M31.1–M31.4 và kiểm tra lại target trước mutation:

| Thuộc tính | Target đã xác minh |
| --- | --- |
| Container / Compose project | `busgo-m312-catalogue-mysql-1` / `busgo-m312-catalogue` |
| Volume | `busgo-m312-catalogue_m312_data`, mount `/var/lib/mysql` |
| Host / port / schema | `127.0.0.1:3352` / `busgo_m312_test` |
| Engine | MySQL 8.4.11 |
| Flyway | 28 successful migrations, latest installed V28, 0 failed; không migration mới |
| Phạm vi M31 giữ nguyên | 3 operators, 19 buses, 3 types, 12 locations, 25 routes; 24 direct routes có lịch, 364 trips mới trong 14–27/11/2026 |
| Giao dịch trước đó | Booking 18, trip 335, gross/net/payment 180.000, không voucher; vé VALID |

Test application đã validate V1–V28 và báo schema up to date. Importer gọi lại guard M31.2: bắt buộc dev, loại demo/prod/production; schema khớp `busgo_m312_*` và property verified-schema, JDBC URL loopback với port explicit. Catalogue phải hoàn toàn REUSE, fleet/trip counts phải đúng M31.4. Không ghi vào database ứng dụng; các container ứng dụng cũ giữ trạng thái stopped.

## 2. Pickup / dropoff

Đã đối chiếu Locations, route stops, trip snapshots và `PrebookingInformationMapper`. API Trip Detail đã trả điểm đón/trả và thời gian của journey được chọn; thông tin trước đặt vé chỉ có province/district, thông tin xe và các chính sách hiện hành. Không có field/API hướng dẫn boarding tự do phù hợp để bổ sung trong milestone này.

**Không persist thêm pickup/dropoff content.** Giữ nguyên tên điểm demo M31.2, flags, stop order, offsets, snapshots và fare journey. Sáu locations mới vẫn không có địa chỉ/tọa độ/đơn vị hành chính chưa xác minh. Không đưa nội dung hướng dẫn vào location name hoặc description xe. HTTP smoke xác nhận Trip Detail trả `Điểm đón demo Phan Thiết` cùng departure/arrival; snapshot toàn bộ locations/route_stops/trip_stops không đổi.

Đề xuất riêng, chưa triển khai: bổ sung hướng dẫn đón/trả có provenance, nhãn demo và trạng thái xác minh địa chỉ; cần thiết kế API/UI và quy tắc snapshot trước khi dùng. Không coi những đề xuất này là dữ liệu đã persist.

## 3. Media created / reused

Đã dùng asset có sẵn do người dùng cung cấp trong project, theo báo cáo M30.3; không tải/tạo ảnh mới. Đã xem trực tiếp ba logo, ảnh nội thất và ngoại thất trước khi chọn. Persist **11 operator_media** qua `OperatorMediaService` M30.5, không raw insert BLOB:

| Owner | Logo | Gallery / featured |
| --- | --- | --- |
| An Phú Express | `operator-an-phu-express-logo.webp` | SLEEPER34: `interior-sleeper-standard.webp`; STANDARD40: `bus-standard.jpg` |
| Minh Thành Limousine | `operator-minh-thanh-limousine-logo.webp` | LIMO22: `bus-limousine.jpg`; SLEEPER34: ảnh giường nằm trên; STANDARD40: ảnh ngoại thất trên |
| Tây Nguyên Travel | `operator-tay-nguyen-travel-logo.webp` | LIMO22, SLEEPER34, STANDARD40 tương tự, lưu riêng đúng owner |

Mỗi owner/type có 1 ảnh featured: 8 nhóm / 8 gallery rows, cộng 3 logos. LIMO22 chỉ dùng ngoại thất; không gán cabin đôi hoặc ảnh ghế chưa chắc chắn khớp layout 1+1. Các ảnh chỉ minh họa, không xác nhận bố trí, tiện nghi hoặc xe thực tế.

Gallery alt bắt đầu `[DEMO M31]`, có tên loại xe và giới hạn ý nghĩa. Logo upload API hiện không nhận alt; `OperatorLogo` đã gắn alt/title nhận diện hư cấu cho đúng ba tên demo, kể cả khi dùng managed logo. UI nhận marker gallery để hiển thị “Ảnh minh họa loại xe”, trigger phù hợp và disclaimer; ảnh managed thông thường tiếp tục dùng nhãn hiện hành. Chỉ sửa text/điều kiện hiển thị, không redesign.

Service giữ giới hạn 5 MiB, 12 ảnh/owner/type, MIME/content validation. Public binary được kiểm tra HTTP 200, có bytes và `nosniff`; UUID của owner khác trả 404 ở cả public-owner URL và operator preview.

Rerun: không gọi logo replacement nếu có LOGO row hoặc logo_url; không append/reorder/edit/re-feature gallery không trống. Mọi nhóm hiện có được báo PRESERVE, không nhận quyền sửa từ prefix demo. Test chỉnh alt/category/featured rồi rerun xác nhận toàn bộ DB snapshot giữ nguyên, sau đó rollback test.

## 4. Operator profile presentation

Ba public descriptions M31.3 đã mô tả rõ operator hư cấu, dịch vụ/thanh toán mô phỏng; giữ nguyên descriptions và contact/legal fields. Chỉ logo_url/updated_at được domain media service cập nhật khi logo ban đầu trống. Không thêm hotline, địa chỉ hoặc tuyên bố doanh nghiệp thật.

HTTP thật kiểm tra Home operator list đủ 3, public profile có nhãn demo, managed logos/gallery và operator marketplace-profile đúng owner. Không thay đổi Home carousel, Search layout hoặc Operator Dashboard layout.

## 5. Voucher matrix

Persist **6 vouchers** qua `VoucherService` M30.4, kèm management history/audit của service. Tên đều bắt đầu `DEMO ·`, description ghi chỉ mô phỏng trong DB test cô lập, không phải khuyến mãi thực tế.

| Code / owner | Discount | Minimum spend | Scope / visibility / state | Quota tổng / khách |
| --- | --- | --- | --- | --- |
| M31-AP-FIX20 / AP | Fixed 20.000 | 100.000 | HCM → Phan Thiết; public ACTIVE | 40 / 1 |
| M31-AP-MIN50 / AP | Fixed 50.000 | 1.000.000 | HCM → Nha Trang; public ACTIVE | 10 / 1 |
| M31-MT-PCT10 / MT | 10%, cap 50.000 | 200.000 | Operator-wide; public ACTIVE | 60 / 2 |
| M31-MT-FUTURE10 / MT | Fixed 10.000 | 100.000 | Operator-wide; public ACTIVE, effective SCHEDULED tại ngày kiểm tra | 10 / 1 |
| M31-TN-PRIVATE15 / TN | Fixed 15.000 | 100.000 | Operator-wide; private ACTIVE | 30 / 1 |
| M31-TN-PAUSED10 / TN | Fixed 10.000 | 100.000 | Operator-wide; private PAUSED | 5 / 1 |

AP/MT/TN là ba operator demo đã có, không thêm operator. Route IDs được resolve từ manifest và association đúng owner; không hardcode generated IDs. Năm mã bắt đầu **08/11/2026 00:00 +07**, FUTURE10 bắt đầu **20/11/2026 00:00 +07**; tất cả kết thúc **28/11/2026 00:00 +07** (exclusive). Thời hạn bao phủ booking hiện tại và cửa sổ lịch; không tự trượt ngày khi rerun.

Tại thời điểm kiểm tra, public promotions chỉ có 3 mã đang hiệu lực; không lộ PRIVATE15, PAUSED10, FUTURE10. Trip HCM–Phan Thiết có đúng FIX20. Evaluator xác nhận minimum spend bị từ chối; private code hợp lệ khi nhập đúng owner; paused/future bị từ chối; 10% của 300.000 là 30.000, của 900.000 bị cap 50.000. Quote sai owner trả HTTP 409. Không sửa fares/lịch để phù hợp voucher.

Không tạo fake usages/redemptions. Chỉ có 1 REDEEMED usage từ booking smoke thật. Mọi voucher tồn tại theo owner+code được PRESERVE, kể cả terms/state/visibility đã chỉnh; importer không re-activate hoặc restore. Chỉ activate/visibility trên bản ghi vừa tạo trong cùng transaction. Sau ngày hết hạn, rerun giữ nguyên campaign; không cố bật lại.

## 6. Staff / crew

Persist 6 employees mới: một DRIVER và một ATTENDANT mỗi operator, stable codes `M31-{AP|MT|TN}-{DRIVER|ATTENDANT}`. Tên đều ghi “Nhân sự demo”, phone `DEMO-NOT-DIALABLE`. Driver profile dùng `DEMO-NOT-VALID-{AP|MT|TN}`, class `DEMO`, expiry 01/01/2030: placeholder hư cấu, không phải giấy phép thật và không chứng nhận khả năng hành nghề.

| Owner | Trip ID được resolve | Journey / ngày giờ Việt Nam | Assignment mới |
| --- | --- | --- | --- |
| AP | 279 | HCM → Cần Thơ, 14/11 06:00–10:00 | 1 DRIVER + 1 ATTENDANT |
| MT | 419 | HCM → Vũng Tàu, 14/11 06:00–08:30 | 1 DRIVER + 1 ATTENDANT |
| TN | 587 | Đà Nẵng → Quảng Ngãi, 14/11 06:00–09:00 | 1 DRIVER + 1 ATTENDANT |

IDs trên là kết quả DB, không được hardcode vào importer. Resolve bằng owner/route/fixed departure với `JpaJdbcTime.parameter`, cùng quy ước thời gian của dự án. Mỗi người chỉ có một assignment demo, không overlap; không xếp ca dài hoặc phủ cả 364 trips. Dùng `OperationsService.saveEmployee/replaceCrew` với owner/capability/status/licence/overlap guards hiện hành. Không bypass lifecycle hoặc thay đổi trip operations để gán crew.

Hai employees, một driver profile và 6 assignment rows cũ giữ nguyên. Bất kỳ crew history nào trên trip mục tiêu đều chặn replacement và được PRESERVE, bao gồm released rows; không sửa lịch sử. API crew của ba trip trả ready=true theo validation mô phỏng hiện tại. Test cố gán tài xế sang chuyến overlap và employee khác owner đều bị từ chối, không ghi DB. Không coi ready=true là xác minh giấy phép ngoài đời.

## 7. Booking smoke

Chỉ tạo **1 booking mới: ID 19**, trip 335 HCM → Phan Thiết ngày 14/11, dùng ghế khác booking 18. Search → Trip Detail/Seats → Hold → Voucher options/quote → Book → Mock confirm → Ticket/QR chạy HTTP thật tới server loopback random port, MySQL test thật.

| Đối chiếu | Kết quả |
| --- | --- |
| Gross / discount / net | 180.000 / 20.000 / 160.000 |
| Voucher | M31-AP-FIX20, đúng owner/journey |
| Payment | PAID, amount 160.000 |
| Usage | 1 REDEEMED gắn booking 19 |
| Inventory | 1 ghế mới BOOKED; hold token đã được gỡ |
| Ticket | 1 VALID, QR nonblank; không lưu raw QR/token trong receipt |
| Booking M31.4 | ID 18 vẫn 180.000, discount 0, voucher NULL; bookings/items/payments/tickets cũ so snapshot không đổi |

Một lần HTTP rehearsal đầu dừng ở negative quote do test kỳ vọng sai 200 thay vì 409; khi đó chưa tạo booking/payment/ticket. Đã sửa kỳ vọng và release hold qua API của chính khách demo, dùng lại customer đó. Chỉ một account customer M31.5 được tạo. Test có durable lookup usage/code để không tạo thêm booking khi chạy lại sau failure; không dùng booking 18 để tính lại giá.

## 8. Row counts before / after

Baseline đầu milestone lấy từ M31.4 receipt và kiểm tra target trước mutation. Acceptance JSON cuối được ghi ở lần tiếp tục sau khi supporting content đã persist; trường `before` trong receipt đó là baseline của lần chạy lại, không phải baseline đầu milestone.

| Table / nhóm | Trước M31.5 | Sau M31.5 |
| --- | ---: | ---: |
| transport_operators / buses / bus_types | 3 / 19 / 3 | 3 / 19 / 3 |
| seat_templates | 96 | 96 |
| locations / routes / route_stops | 12 / 25 / 52 | 12 / 25 / 52 |
| operator_routes / operator_route_fares | 29 / 34 | 29 / 34 |
| trips / trip_stops / trip_segments | 385 / 776 / 391 | 385 / 776 / 391 |
| trip_seats / trip_seat_segment_inventory | 12.886 / 13.090 | 12.886 / 13.090 |
| operator_media | 0 | 11 |
| operator_vouchers / voucher_usages | 0 / 0 | 6 / 1 |
| operator_employees / driver_profiles / employee_capabilities | 2 / 1 / 2 | 8 / 4 / 8 |
| trip_crew_assignments | 6 | 12 |
| users | 7 | 8 |
| bookings / payments / tickets | 1 / 1 / 1 | 2 / 2 / 2 |

Inventory cuối: 12.986 AVAILABLE, 102 BLOCKED, 2 BOOKED, 0 HELD. Không tạo thêm trips/seats/inventory; chỉ một ghế AVAILABLE thành BOOKED do smoke M31.5. Không xóa dữ liệu cũ.

## 9. Dry-run / apply / rerun

Source mới: `M31SupportService`, `M31SupportRunner`, trong module M31. Runner không đăng ký ApplicationRunner/CommandLineRunner trong startup thường. Minimal context chỉ import các domain services cần dùng; không web/Flyway/schedulers/bootstrap/legacy initializer. Nested command source cố ý không có `@Configuration` để không bị component scan đăng ký.

Dry-run mặc định, explicit opt-in, asset directory bắt buộc; chỉ service apply ghi. Apply dùng transaction READ_COMMITTED, rollback cả batch với checked/unchecked exceptions; role lock phối hợp các importer M31, domain owner/trip locks vẫn được giữ. Chỉ chạy command này riêng, không chạy song song importer/legacy runner hoặc thao tác operator trên fixture. Existing key/nonempty group/history chặn create và report PRESERVE với lý do; missing/ambiguous baseline/admin/trip từ chối batch, không tự sửa.

Ví dụ chạy tại workspace sau khi kiểm tra target và cấu hình DB_URL/DB_USERNAME/DB_PASSWORD bằng environment riêng (không đưa secrets vào args/log):

```powershell
mvn.cmd -o -f backend/pom.xml spring-boot:run `
  '-Dspring-boot.run.main-class=com.busgo.demo.m31.M31SupportRunner' `
  '-Dspring-boot.run.profiles=dev' `
  '-Dspring-boot.run.arguments=--busgo.m31.support.enabled=true --busgo.m31.catalogue.verified-schema=busgo_m312_test --busgo.m31.support.assets=C:/Users/Giang/busgo/frontend/public/images/busgo'
```

Thêm `--busgo.m31.support.dry-run=false` chỉ để apply đúng target đã review. Không cần fleet account secrets cho command này; dùng dedicated active admin đã tồn tại, không sửa mật khẩu. SecurityContext tạm cho domain calls được restore trong finally.

Dry-run không đổi snapshot. Apply đầu tạo 11 media, 6 vouchers, 6 employees và crew trên 3 trips. Rerun trong acceptance và standalone dry command đều trả **26 PRESERVE**, không duplicate hoặc thay featured/terms/state. Rerun sau paid booking cũng giữ toàn bộ DB snapshot. Logs ở `.tools/m31-5/`; sanitized receipts ở `backend/target/m31-5-import-receipt.json` và `m31-5-acceptance.json`, đều ignored. Không ghi password/access token/hold token/raw QR vào receipts.

## 10. Relevant verification

| Kiểm tra | Kết quả / evidence |
| --- | --- |
| M31CatalogueTest | 2 pass: từ chối prod/demo/app schema trước write; guard được support reuse |
| ImageUploadTest / PromotionMoneyTest | 1 + 5 pass: format decoding và money boundaries |
| M31SupportAcceptanceIT | 1 pass: import/rerun/snapshots, public/operator HTTP, media binaries/owner isolation, voucher quote + paid booking/QR |
| M31SupportIT | 4 focused rollback tests: edited content preservation; voucher min/private/paused/future/percent-cap; crew owner/overlap; image MIME/size/quota/type |
| Standalone M31SupportRunner | Default dry-run PASS, 26 PRESERVE, đóng context sạch |
| Frontend SSR | 4/4 pass: M30.5 owner/type cover + M31.5 demo labels/accessibility/disclaimer |
| Frontend build | TypeScript + Vite production build PASS |
| Whitespace | `git diff --check` PASS |

Focused commands: Maven offline `verify -Pmysql-integration`, unit selector `M31CatalogueTest,ImageUploadTest,PromotionMoneyTest`; opt-in flags `busgo.m31.support.acceptance=true` hoặc `busgo.m31.support.integration=true` và đúng IT selector. Frontend: `node --test tests/m31-5-supporting-media.test.mjs tests/m30-5-media.test.mjs`, `npm run build`.

Operator HTTP checks dùng JWT test ký qua JwtService cho dedicated accounts đã tồn tại, được application filter kiểm tra bình thường; không reset mật khẩu. Customer register/login đã chạy ở rehearsal đầu. Các test chỉnh nội dung hoặc thử mutation bị từ chối đều rollback, không tạo usages/crew/media giả trong fixture cuối. Không chạy full regression M30.6, không claim browser E2E hoặc production certification.

## 11. Known limitations

- Không có boarding guide/address mới: API hiện tại không có trường phù hợp, cần thiết kế riêng. Không xác minh địa chỉ legacy ngoài đời.
- Chỉ 3 chuyến có crew mới; không mô phỏng toàn bộ staffing/ca lái. Driver fields là placeholder demo rõ ràng.
- Một ảnh mỗi group, LIMO22/STANDARD40 chỉ ngoại thất; không hứa ảnh đúng từng biển số hoặc trang bị cụ thể.
- Không có provenance table hoặc migration. Nonempty content luôn thắng và report PRESERVE, không chứng nhận nó còn khớp manifest. Nếu user xóa toàn bộ gallery/logo/employee/voucher và làm identity trống hoàn toàn, lần apply sau có thể tạo lại; không dùng command như công cụ đồng bộ/xóa/repair. Crew history chặn replacement ngay cả khi đã release.
- Marker alt có thể được operator sửa; khi mất marker, UI xử lý như upload thông thường. Importer giữ chỉnh sửa đó; operator chịu trách nhiệm mô tả ảnh, không tự sửa lại metadata.
- Fixed voucher dates sẽ hết hạn; window lịch cũng cố định. Không tự gia hạn, tạo rolling schedule hoặc bật campaign trên môi trường ứng dụng.
- Apply command dành cho fixture cô lập không có thao tác cạnh tranh; chưa chạy load/fault injection hoặc thử mọi race. Domain guards/transaction không phải chứng nhận production rollout.

## 12. Preparation for M31.6 / bàn giao

Đã persist media/voucher/crew và một giao dịch smoke như trên trong test schema; pickup guide chỉ là đề xuất. Giữ nguyên toàn bộ phạm vi catalogue/fleet/lịch/fares M31.2–M31.4 và booking M31.4. Không thêm migration hoặc sửa commerce engine.

Sau kiểm chứng, đã dừng `busgo-m312-catalogue-mysql-1` với exit0, giữ volume, local receipts/logs và working tree để review; không để web server/importer chạy nền. Final counts lưu tại `.tools/m31-5/final-counts.txt`; các container BusGo cũ vẫn exited. Database ứng dụng không thay đổi. Có thể dùng fixture này làm đầu vào review M31.6 sau khi người dùng giao nhiệm vụ; **không triển khai M31.6 trong milestone này**.
