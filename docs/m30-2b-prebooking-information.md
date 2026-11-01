# M30.2B — Thông tin chuyến xe trước khi đặt vé

Ngày: **31/10/2026, giờ Việt Nam**. Repository: `C:\Users\Giang\busgo`. Trạng thái: triển khai xong trong phạm vi dữ liệu hiện có, chờ người dùng review. Không stage/commit/push/merge hoặc đổi branch.

## 1. Hiện trạng trước khi sửa

Đã đọc roadmap `m30-product-experience-audit.md`, báo cáo `m30-2a-customer-journey.md`, audit `m30-1-visual-assets-audit.md` và source trực tiếp.

Git bắt đầu ở `main`, có 10 tracked files modified và 6 untracked từ M30.2A/các audit. Giữ nguyên các thay đổi đó, không reset/clean/stash hoặc ghi đè. Không tìm thấy AGENTS.md áp dụng. Không sửa hai audit, báo cáo M30.2A hoặc `tatus --short`.

Các nguồn được xác nhận:

| Nguồn | Field/ý nghĩa thực sự có | Giới hạn |
|---|---|---|
| `Location` | name, province, district, address, latitude, longitude, status | Không có loại điểm CITY/AREA/STATION, xác minh địa chỉ, quyền công bố hoặc hướng dẫn lên xe |
| `RouteStop` | location, stopOrder, allowPickup/Dropoff, estimatedOffsetMinutes, status | Không có địa chỉ/hướng dẫn riêng |
| `TripStop` | location, sourceRouteStop, stopOrder, plannedArrival/DepartureTime, permissions, status | Snapshot lịch/chặng; không snapshot địa chỉ/hướng dẫn. Tên/khu vực vẫn đọc từ Location hiện tại |
| `CustomerJourneyResolver` | Chọn đúng pickup/dropoff, chiều, quyền đón/trả, segments và exact active fare | Giữ nguyên thuật toán; không lấy điểm đầu/cuối tuyến thay điểm khách chọn |
| `BusType` | name, seatCount, description, status | Số chỗ cấu hình, không phải availability. Không có danh sách tiện nghi được xác minh |
| `SeatTemplate` / `TripSeat` | floor, row, column, seatType; TripSeat là sơ đồ chuyến | Không đoán chỗ STANDARD thành giường từ tên loại xe |
| `Bus` | busType, imageUrl, licensePlate, operator, status | Không cần đưa biển số, metadata quản trị hoặc entity vào public DTO |
| M17 / M20 / M21 | Đổi/hủy theo trạng thái, mốc 6 giờ trước giờ đón của chặng, attendance, điểm đón, hạn thanh toán và nhà xe/tuyến | Không đồng nhất mọi điều kiện giữa ba nghiệp vụ; không kết luận eligibility chỉ từ thời gian |

Seeder có địa chỉ dạng “Khu vực trung tâm …”, không phải vị trí boarding chính xác. Không có bằng chứng hoặc metadata cho phép coi bất kỳ `Location.address` nào là địa chỉ đón/trả đã xác minh. Không chạy seeder hoặc sửa dữ liệu để tạo cảm giác đầy đủ.

## 2. Backend DTO/API thay đổi

Giữ endpoint `GET /api/v1/trips/{tripId}?pickupLocationId=…&dropoffLocationId=…`, thêm field additive `data.prebooking`:

```text
prebooking
  pickup:  { province, district }
  dropoff: { province, district }
  vehicle: { seatCount, description }
  policy:  { customerCutoffAt, fullCancellation, partialCancellation,
             modification, refund, eligibilityNotice }
```

- `TripSearchDtos.java`: records whitelist riêng; không serialize entity.
- `TripSearchService.java`: sau resolver hiện có, map thông tin theo **hai TripStop được chọn**, không theo origin/destination của route. SearchResult/list DTO không đổi.
- `PrebookingInformationMapper.java` (mới): projection read-only, chuẩn hóa text trống thành null, lấy seatCount/description từ BusType, tính mốc tham khảo bằng plannedDepartureTime của pickup đã chọn trừ 6 giờ.
- Không đưa `Location.address`, tọa độ, pháp nhân, liên hệ riêng tư, biển số, ghi chú vận hành hoặc dữ liệu người dùng vào projection mới. `province`/`district` đã có ở public autocomplete.
- Không thêm endpoint, migration, policy engine, bảng hoặc quyền truy cập. GET chi tiết vẫn công khai cho guest và customer; mutation/private APIs giữ guard cũ.

### Đối chiếu policy

Đây là **bản mô tả server**, không phải eligibility hoặc một lời hứa cho phép thao tác:

- **M17:** PENDING/CONFIRMED; SCHEDULED hoặc BOARDING; kiểm tra điểm đón đóng và attendance CHECKED_IN/BOARDED/NO_SHOW của các item chưa hủy; customer cutoff 6 giờ. Điều kiện trạng thái nhà xe của M17 không đồng nhất với đổi chuyến.
- **M20:** PENDING/CONFIRMED, SCHEDULED, nhà xe ACTIVE; kiểm tra pickup, cutoff, hạn thanh toán, attendance, điểm đón đóng và modification đang chờ. Ghế/chuyến thay thế, giá và xác nhận vẫn theo server.
- **M21:** PENDING/CONFIRMED, SCHEDULED, nhà xe ACTIVE/SUSPENDED và tuyến ACTIVE; kiểm tra pickup, cutoff, hạn thanh toán, modification đang chờ, điểm đón đóng, từng item; giữ ít nhất một khách.
- Copy chung nêu các yếu tố có thể hạn chế thao tác. Hoàn tiền/thu thêm là mô phỏng, không chuyển tiền ngân hàng; số tiền chỉ được xác nhận qua quy trình server hiện có.

Mốc 6 giờ trong mapper phản ánh logic hiện tại của cả ba service, không sửa hoặc dùng để thay thế chúng. Khi đổi chính sách trong tương lai phải cập nhật mô tả và kiểm thử parity tương ứng. Không tính eligibility tương lai từ một chuyến chưa có booking.

## 3. Frontend thay đổi

### TripDetail

- Component mới `features/trip/PrebookingDetails.tsx`: khối mặc định thu gọn **“Thông tin cần biết trước khi đặt vé”** sau sơ đồ ghế/hành trình, trước lịch trình mở rộng.
- Hiển thị tên và giờ theo lịch của đúng điểm đón/trả đã chọn, giờ Việt Nam; district/province được ghi là **Khu vực**.
- Nêu rõ chưa có địa chỉ/hướng dẫn được xác nhận công khai; tên bến/thành phố/khu vực không xác nhận vị trí đón/trả chính xác. Không lấy địa chỉ demo chung làm vị trí boarding.
- Hiển thị tên loại xe, số chỗ cấu hình và description nếu có; phần thiếu có fallback. Số vị trí/tầng lấy từ SeatMapData của chuyến đang mở, phân biệt số chỗ cấu hình với số chỗ còn trống.
- Policy lấy từ API, có mốc theo pickup đã chọn. Khi policy không có, hiện chưa có tóm tắt và không cam kết quyền đổi/hủy. Không tự dựng policy frontend.
- Không suy tiện nghi từ tên loại xe, không thêm ảnh/gallery, không đổi floor/row/column, inventory hoặc hold.

### Checkout

- Dùng lại cùng component, mặc định thu gọn trong summary; giữ form liên hệ và CTA sticky hiện có.
- `SavedHold.tripInformation` chỉ lưu **snapshot phục vụ trình bày** từ bước chọn ghế: prebooking, tên loại xe, danh sách floor. Không dùng làm input cho quote/hold/booking/giá.
- Chỉ lưu snapshot cùng hold sau API giữ chỗ thành công. Checkout lấy tên/giờ từ hold response hiện tại, ghi rõ metadata là tham khảo từ bước chọn ghế.
- Không gọi thêm API chi tiết hoặc endpoint mới. Hold cũ/refresh vào checkout không có metadata vẫn hoạt động và hiển thị fallback; không suy metadata từ ID.

### Fallback hành trình

`SearchForm.tsx` và `presentation.ts` đổi “Điểm đón/trả #ID” thành “Điểm đón/trả đã chọn”. IDs vẫn ở form state, URL và request; autocomplete public vẫn phân giải nhãn đúng ID khi có kết quả. Không hiển thị ID nội bộ cho khách khi thiếu nhãn. Giữ mọi thay đổi điều hướng M30.2A.

### File M30.2B chỉnh/thêm

Backend:

- `backend/src/main/java/com/busgo/trip/search/TripSearchDtos.java`
- `backend/src/main/java/com/busgo/trip/search/TripSearchService.java`
- `backend/src/main/java/com/busgo/trip/search/PrebookingInformationMapper.java` (mới)
- `backend/src/test/java/com/busgo/M5TripSearchIT.java`

Frontend:

- `frontend/src/types/customer.ts`
- `frontend/src/features/trip/PrebookingDetails.tsx` (mới)
- `frontend/src/pages/TripPage.tsx`
- `frontend/src/pages/BookingPage.tsx`
- `frontend/src/features/booking/holdStore.ts`
- `frontend/src/features/customer/customer.css`
- `frontend/src/features/customer/presentation.ts`
- `frontend/src/features/search/SearchForm.tsx`
- `frontend/tests/m30-2b.test.mjs` (mới)
- `frontend/tests/m30-2b-browser.cjs` (mới)
- `docs/m30-2b-prebooking-information.md` (báo cáo mới)

CSS chỉ scoped customer shell, dùng token/component M29. Các file M30.2A khác vẫn giữ nguyên.

## 4. Thông tin hiển thị được từ nguồn thực

- API thật cung cấp tên/giờ theo TripStop của hành trình khách chọn; khu vực từ Location tương ứng, số chỗ/mô tả từ BusType khi được nhập.
- Số vị trí và tầng từ SeatMapData thực của chuyến, không dựng từ ảnh hoặc tên loại xe.
- Tóm tắt quy tắc hiện hành từ server và mốc 6 giờ đúng pickup của chặng.
- Integration test ghi nhận projection thật với MySQL và dữ liệu test được tạo bằng fixture trong transaction; đây không phải xác nhận nội dung của catalogue demo hiện tại.

## 5. Thông tin thiếu nguồn xác thực

- Chưa có địa chỉ boarding/alighting chính xác được duyệt, hướng dẫn lên/xuống xe hoặc phân loại điểm thành phố/khu vực/bến.
- Chưa có publication/verification metadata cho Location.address; vì vậy field này **chưa được công khai**, kể cả khi DB có giá trị. Không dùng heuristic dựa chữ “Bến xe” để xác minh vị trí.
- Mô tả BusType là nội dung cấu hình được khai báo, chưa phải chứng nhận tiện nghi từng xe. Không có amenity contract để xác nhận Wi-Fi/WC/USB/nước uống.
- Chưa có snapshot địa chỉ theo từng trip stop; thay đổi Location có thể thay tên/khu vực đọc được, giữ theo hành vi cũ.

Contract tối thiểu đề xuất cho đợt nội dung sau (chưa triển khai): `publicAddress`, `boardingInstructions`, `locationScope` (CITY/AREA/EXACT_STOP), kèm trạng thái duyệt công bố ở persistence/quản trị. Chỉ trả địa chỉ/hướng dẫn đã được duyệt qua public projection; nếu khác theo chuyến cần override/snapshot tại trip stop. Không lấy nguyên Location hoặc hồ sơ pháp nhân đưa ra public.

## 6. Kiểm thử và giới hạn

Evidence ignored: `.tools/m30-2b/`.

| Lớp kiểm tra | Kết quả |
|---|---|
| Frontend TypeScript + Vite build | PASS; log `frontend-build.log` |
| Frontend unit/regression | **170 pass, 0 fail, 0 skip**; log `frontend-tests.log` |
| Backend unit | **66 pass, 0 fail, 0 skip** trong lệnh verify cuối |
| MySQL integration liên quan | **72 pass, 0 fail, 0 skip**, Maven BUILD SUCCESS; `backend-integration.log` |
| Browser fixture M29 + M30.2A + M30.2B | **31 nhóm checks pass**, 68 captures ở 1440/834/390/360; 0 page/console errors, 0 request thiếu fixture; `browser/acceptance.json`, `browser-final.log` |
| Whitespace | `git diff --check` PASS |

MySQL **9.2.0**, instance mới tại `127.0.0.1:3330`, datadir `.tools/m30-2b/mysql-data`, schema mới `busgo_m30_2b_test`. Flyway áp dụng 26 migration có sẵn; không thêm migration hoặc chạy profile demo. Không reset/reseed hoặc kết nối DB demo hiện có.

Các suite MySQL: M5TripSearchIT **11** (gồm 2 mới), M6SeatAvailabilityIT **9**, M17CancellationIT **9**, M20ModificationIT **28**, M21PartialCancellationIT **15**. Đây là Spring/MockMvc + DB thật, không phải response HTTP fixture. Unit 66 ca chạy trong cùng verify. Lệnh unit riêng ban đầu trong sandbox dừng tại FleetReadBatchTest; đã dừng tiến trình đó và dùng lượt verify ngoài sandbox pass toàn bộ unit làm kết quả cuối. Không báo lượt sandbox chưa hoàn tất là pass.

Hai ca backend mới kiểm tra:

- Tuyến bốn điểm, khách chọn **B → C**, khác cả origin A và destination D: khu vực, giờ và cutoff lấy đúng chặng; public guest và customer đều đọc được; nguyên inventory trước/sau read.
- Description/province/district thiếu hoặc trắng thành null; không tự tạo tiện nghi/hướng dẫn. Response không chứa sentinel địa chỉ chưa xác minh, địa chỉ demo, tọa độ, biển số, field pháp nhân hoặc liên hệ riêng tư. Regression M5 giữ kiểm tra guest không truy cập operator/mutation endpoints.

Frontend unit mới kiểm tra configured count khác số vị trí thực, tầng không bị đoán, HTML trong description được escape, thiếu metadata/policy, hold cũ và không lộ ID trong nhãn hành trình.

Browser chạy React thật + Edge headless, **mọi API được intercept bằng fixture**. Dữ liệu người/giá/sao/description/policy trong PNG là test, không phải catalogue được xác thực. M30.2B kiểm tra guest/customer ở 1440/834/390/360, khối mở/đóng, trường thiếu, policy thiếu, số chỗ/tầng, CTA, checkout không gọi API trùng và hold cũ. Harness kế thừa toàn bộ assertion M29 và M30.2A.

Đã xem trực tiếp ảnh Trip guest 360 và Checkout mở 360. Layout mặc định thu gọn giữ sơ đồ, giá và CTA. Full-page screenshot ghi sticky tại vị trí viewport lúc capture; không phải thiết kế đặt header/CTA giữa nội dung.

Giới hạn:

- Không chạy browser nối backend thật hoặc toàn hành trình E2E thật; MySQL tests và browser fixtures là hai lớp bằng chứng riêng.
- Không chạy toàn bộ backend integration/concurrency suite; chỉ 5 suite liên quan nêu trên.
- Không xác nhận địa chỉ/loại xe trong database demo hiện tại, không user testing, Safari/iOS/Firefox, thiết bị cảm ứng thật hoặc screen reader chuyên dụng.
- Checkout snapshot có thể cũ; đã ghi nhãn tham khảo và không dùng quyết định giao dịch. Eligibility sau đặt vé và xác nhận luôn từ backend.
- MySQL 9.2 có cảnh báo compatibility từ Flyway; migration/test vẫn pass. Build giữ cảnh báo Zod annotation và Notifications static/dynamic import có sẵn.

Tái chạy frontend: khởi động `npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5190 --strictPort`, rồi `node frontend/tests/m30-2b-browser.cjs`. `BUSGO_TEST_ORIGIN`/`PLAYWRIGHT_MODULE` theo harness M29. Fixture ngày chuyến 15/01/2027 cần cập nhật nếu chạy sau ngày này.

MySQL test đã có datadir riêng: **không khởi tạo lại**. Khởi động instance này theo cổng/datadir ở trên nếu cần, đặt JAVA_HOME tới `.tools/jdk17/jdk-17.0.20.1+1`, DB_URL `jdbc:mysql://127.0.0.1:3330/busgo_m30_2b_test?connectionTimeZone=UTC`, DB_USERNAME root, password rỗng và chạy:

```powershell
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration '-Dit.test=M5TripSearchIT,M6SeatAvailabilityIT,M17CancellationIT,M20ModificationIT,M21PartialCancellationIT' '-Dspring.datasource.password='
```

## 7. Nội dung cần người dùng chuẩn bị

1. Địa chỉ điểm đón/trả được chủ sở hữu xác nhận, mức chính xác CITY/AREA/EXACT_STOP và quyền công bố. Sáu địa điểm demo cần ghi rõ dữ liệu minh họa hay điểm thực; không dùng mô tả khu vực làm vị trí boarding chính xác.
2. Hướng dẫn lên/xuống xe theo địa điểm/chuyến nếu có nguồn; hiện chưa có field hoặc nội dung để công bố.
3. Mô tả cấu hình ba loại xe, đối chiếu seatCount và sơ đồ thực; tiện nghi từng xe cần chứng cứ riêng và contract được duyệt.
4. Duyệt câu chữ tóm tắt đổi/hủy với quy tắc server hiện hành. Không nhập một “chính sách nhà xe” tự do khiến khác engine nghiệp vụ.

## 8. Để lại cho M30.3–M30.6

- **M30.3:** nhận diện/ảnh khám phá, nhãn minh họa và fallback, ảnh xe/gallery theo roadmap; không làm trong lượt này.
- **M30.4:** voucher discovery/options, giá/quote server và opt-in publicity; không thay giá hoặc thêm discovery trong M30.2B.
- **M30.5:** quản trị cover/profile/media/tiện nghi đúng tenant; contract/persistence địa chỉ/hướng dẫn cần chốt nguồn nội dung và quyền quản trị riêng, không tự mở quyền sửa Location dùng chung.
- **M30.6:** nghiệm thu toàn hành trình qua backend thật, session/stale/error, keyboard/accessibility, các breakpoint và browser engine bổ sung; đánh giá lại parity copy khi policy thay đổi.
- Passenger-per-seat, thanh toán thật M22 và M27B ngoài phạm vi; không sửa lifecycle, hold, transaction, tiền hoặc cấu trúc ghế.

Browser final có 12 lỗi HTTP cố ý thuộc các ca fixture lỗi; không có lỗi runtime không xử lý. Các screenshot M30.2B: `browser/m30b-trip-open-{guest|customer}-{width}.png`, `browser/m30b-checkout-open-{width}.png`, `browser/m30b-missing-metadata-360.png`. 55 captures còn lại giữ regression M29/M30.2A. Kiểm tra overflow document ở toàn bộ 68 captures pass.

Đã dừng Vite 5190 và instance MySQL test 3330 do lượt này tạo; giữ datadir, schema, logs và PNG, không xóa database. Không có backend demo được khởi động.

## Git status cuối

Branch **main**. **16 tracked files modified, 11 untracked**; bao gồm toàn bộ M30.2A và audit có sẵn. Riêng M30.2B sửa/thêm 15 file được liệt kê ở mục 3. Không có staged changes, không commit/push/merge, không thay identity/remote/branch. `tatus --short` vẫn 2.945 byte, thời gian sửa 12/10/2026 16:09:36.

```text
 M backend/src/main/java/com/busgo/trip/search/TripSearchDtos.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchService.java
 M backend/src/test/java/com/busgo/M5TripSearchIT.java
 M frontend/src/components/ui.tsx
 M frontend/src/features/booking/CancellationSection.tsx
 M frontend/src/features/booking/holdStore.ts
 M frontend/src/features/customer/customer.css
 M frontend/src/features/customer/presentation.ts
 M frontend/src/features/marketplace/Marketplace.tsx
 M frontend/src/features/search/SearchForm.tsx
 M frontend/src/pages/BookingDetailPage.tsx
 M frontend/src/pages/BookingPage.tsx
 M frontend/src/pages/SearchPage.tsx
 M frontend/src/pages/TripPage.tsx
 M frontend/src/types/customer.ts
 M frontend/tests/cancellation.test.mjs
?? backend/src/main/java/com/busgo/trip/search/PrebookingInformationMapper.java
?? docs/m30-1-visual-assets-audit.md
?? docs/m30-2a-customer-journey.md
?? docs/m30-2b-prebooking-information.md
?? docs/m30-product-experience-audit.md
?? frontend/src/features/trip/PrebookingDetails.tsx
?? frontend/tests/m30-2a-browser.cjs
?? frontend/tests/m30-2a.test.mjs
?? frontend/tests/m30-2b-browser.cjs
?? frontend/tests/m30-2b.test.mjs
?? "tatus --short"
```

Dừng để người dùng review; không triển khai M30.3–M30.6.
