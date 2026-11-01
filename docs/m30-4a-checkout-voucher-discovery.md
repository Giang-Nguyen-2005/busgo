# M30.4A — Khám phá và chọn voucher tại checkout

Ngày: **31/10/2026, giờ Việt Nam**. Dự án `C:\Users\Giang\busgo`. Đã triển khai, chờ người dùng review. Không stage/commit/push/merge; giữ branch `main` và toàn bộ thay đổi M30.2A/B có sẵn.

## 1. Source audit và những gì đã có

Đã đọc mục 7/M30.4A của `m30-product-experience-audit.md`, `m24b-promotions-vouchers.md`, `m30-2b-prebooking-information.md`, VoucherService/Controller/DTOs, BookingService/Controller, BookingPage, operator Vouchers UI, migration V24 và các tests liên quan.

Baseline Git: **16 tracked files modified, 11 untracked**. Không tìm thấy AGENTS.md áp dụng. Không reset/clean/stash, không sửa audit hoặc báo cáo M30 cũ. `tatus --short` giữ nguyên.

M24B đã có các quy tắc và engine sau, được tái sử dụng:

- Một voucher/WEB booking CUSTOMER; mã chuẩn hóa uppercase, unique **trong từng nhà xe**. Hai nhà xe có thể dùng cùng code.
- PERCENT/FIXED_AMOUNT, cap, minimum gross spend, thời gian `[startsAt, endsAt)`, optional **operatorRouteId** (khác master route ID), total/per-customer limit.
- `PromotionMoney` tính BigDecimal, rounding/allocation; phải còn ít nhất 0,01 payable mỗi ghế. Frontend không tính giảm.
- Quote kiểm tra owned/unexpired hold, trip, route, current fare × số ghế và evaluate nhưng không reserve.
- Booking tạo reservation RESERVED trong cùng transaction với booking/items/inventory. Final validation/locks quyết định quota và số tiền. Payment chuyển REDEEMED; unpaid cancellation/expiry chuyển RELEASED; reservation hết hạn không tiêu capacity. Rollback không tiêu lượt hoặc hold.
- Paid full/partial cancellation không trả lại lượt; M20/M21 dùng net/entitlement/snapshot hiện có. Không áp lại voucher khi đổi chuyến.
- Admin nhà xe quản lý đúng tenant; Staff chỉ đọc, SYSTEM_ADMIN không có bypass. Điều khoản đã có usage history bị khóa, nhưng state pause/reactivate vẫn thay đổi được.

Trước M30.4A chưa có visibility opt-in hoặc API customer options; checkout chỉ có nhập mã tay. Không phát hiện blocker về engine tiền/transaction. Đã xác nhận và sửa một blocker giao diện trong lúc test: summary sticky có thể che nút thanh toán khi mở danh sách voucher dài; không mở rộng phạm vi sau phát hiện này.

## 2. API, DTO và migration mới

### Migration V27

`V27__voucher_customer_visibility.sql` chỉ thêm:

- `operator_vouchers.customer_visible BOOLEAN NOT NULL DEFAULT FALSE`.
- Index discovery `(operator_id, customer_visible, state, ends_at)`.

Không sửa V24 hoặc migration cũ. Cả voucher đã có và voucher tạo mới mặc định không công khai. Không backfill thành public, không sửa booking/item/payment/usage/ledger hoặc điều khoản giá. CoreDatabaseIT cập nhật số migration hiện tại thành 27; các test lịch sử V26 không bị viết lại.

### Operator visibility

`PATCH /api/v1/operator/vouchers/{id}/visibility`

```json
{"customerVisible": true}
```

Chỉ Admin đúng tenant. Có thể bật/tắt độc lập với term lock, kể cả đã có usage. Lock operator → voucher theo thứ tự quản lý hiện có, cập nhật actor/time và ghi `PUBLIC_ENABLED`/`PUBLIC_DISABLED` vào lịch sử quản lý/audit. Management DTO trả `customer_visible` boolean theo quy ước snake_case cũ. Bật public không tự activate; tắt public không làm mã nhập tay mất hiệu lực và không reprice booking cũ.

### Customer options

`POST /api/v1/bookings/voucher-options`, chỉ CUSTOMER:

```json
{"holdToken":"owned-hold-token","page":0,"size":5}
```

`holdToken` bắt buộc, tối đa 100 ký tự; page optional mặc định 0, giới hạn 0–100000; size optional mặc định 10, giới hạn 1–20. UI dùng 5/trang. Server tự resolve operator/operatorRoute/seats/fare từ hold, không nhận các giá trị này từ client.

BookingService tách **phần read/validation context hiện có** của voucherQuote thành helper dùng chung cho quote và options: require CUSTOMER (không SYSTEM_ADMIN), owned hold đủ hình dạng, chưa hết hạn, nhà xe/tuyến active, hành trình/segments hợp lệ, current fare và gross đúng số ghế. Không thay createFromHold hoặc logic transaction/hold.

Response có wrapper `data`:

```text
{
  grossAmount, seatCount,
  options: [{
    id, code, name, description, discountType, discountValue,
    maxDiscount, minimumSpend, startsAt, endsAt, customerLimit,
    status, reasonCode, message, quote
  }],
  pagination: { page, size, totalElements, totalPages }
}
```

- Chỉ truy vấn opt-in đúng operator, đúng operatorRoute hoặc áp dụng toàn nhà xe, ACTIVE và đang trong thời gian sử dụng. DRAFT/PAUSED/chưa bắt đầu/expired/private/khác tuyến không được đưa vào danh sách hiện hành.
- Từng option dùng **VoucherService.evaluate hiện có**, không có engine giảm giá thứ hai.
- `AVAILABLE`: evaluate thành công, có server quote.
- `CONDITION_NOT_MET`: chưa đạt minimum spend, không có quote và không chọn được.
- `UNAVAILABLE`: ví dụ hết quota toàn cục/per-customer hoặc không bảo đảm minimum payable; không có quote và không chọn được. Không tiết lộ chính xác còn bao nhiêu lượt hoặc ai đã dùng.
- Danh sách rỗng là không có campaign công khai phù hợp ở snapshot/trang đó, không đồng nghĩa không có mã riêng tư hợp lệ.
- Options/quote chỉ đọc, **không reserve**. Header options `Cache-Control: private, no-store`. Các business rejections được trình bày bằng reason/message hiện có; exception khác không bị che thành kết quả đủ điều kiện.
- Existing `/bookings/voucher-quote` và create body giữ contract. Khách chọn option hay nhập tay đều phải quote lại; tạo booking vẫn revalidate/reserve theo M24B.

Danh sách được phân trang/bounded; evaluate thực hiện các read hiện có cho từng option. Không thêm engine batch/quota, public endpoint anonymous hoặc cơ chế quảng bá Home/Search/Trip.

## 3. UI checkout và operator

### Checkout

- Khối thu gọn **“Ưu đãi dành cho chuyến đi”**, dùng M29. Tải options theo owned hold; danh sách có điều kiện, thời hạn giờ Việt Nam, minimum spend/cap/per-customer limit và lý do từ chối.
- Chọn một mã → điền input → quote server. Chỉ giữ một `appliedCode`; vẫn nhập mã riêng tư, kiểm tra lại và bỏ mã.
- Hiển thị giá gốc, giảm và tổng **dự kiến** từ quote server; nói rõ options/quote không giữ lượt, tạo booking mới xác nhận giá/quota.
- Không có mã công khai vẫn nhập tay hoặc đặt giá gốc bình thường. Error/retry và phân trang không chặn đặt vé khi chưa áp mã.
- Nếu quote lỗi hoặc booking từ chối voucher sau preview: không trình bày số giảm cũ như còn hiệu lực; chặn submit cho đến khi kiểm tra/chọn lại hoặc khách chủ động bỏ mã. Không tự bỏ voucher để tạo booking giá cao hơn.
- Guard submit gắn cả nút và form handler (bao gồm Enter/requestSubmit); không chỉ dựa disabled button.
- State checkout remount theo user ID khi đổi tài khoản. Options/query key scoped user + hold + giá/hạn + page, có abort signal, gcTime 0; quote scope user/hold/code giữ nguyên. Hold hết hạn dừng options/quote và trở về chọn ghế theo cơ chế cũ.
- Danh sách cuộn trong vùng giới hạn. Khi mở details dài, summary cuộn theo document thay vì sticky che confirmation; đóng lại giữ hành vi M29. CTA mobile và giá vẫn dùng layout cũ.

### Operator

List hiển thị public/private. Admin có “Bật công khai”/“Tắt công khai” với bước xác nhận và giải thích mã nhập tay/booking cũ. Nút nằm ngoài form term lock nên vẫn dùng được cho campaign đã có usage. Staff chỉ xem trạng thái, không có nút mutation. Không đổi dashboard hay thiết kế chính M29.

### File thuộc M30.4A

Production backend (6):

- `backend/src/main/java/com/busgo/promotion/VoucherDtos.java`
- `backend/src/main/java/com/busgo/promotion/VoucherController.java`
- `backend/src/main/java/com/busgo/promotion/VoucherService.java`
- `backend/src/main/java/com/busgo/booking/BookingController.java`
- `backend/src/main/java/com/busgo/booking/BookingService.java`
- `backend/src/main/resources/db/migration/V27__voucher_customer_visibility.sql` (mới)

Frontend production (4):

- `frontend/src/features/booking/CheckoutVoucherOptions.tsx` (mới)
- `frontend/src/pages/BookingPage.tsx` (giữ toàn bộ M30.2B)
- `frontend/src/features/operator/Vouchers.tsx`
- `frontend/src/features/customer/customer.css` (giữ CSS M30.2A/B)

Tests/report (8):

- `backend/src/test/java/com/busgo/CoreDatabaseIT.java`
- `backend/src/test/java/com/busgo/M24BConcurrencyIT.java` (thêm bước cả hai khách xem options trước race lượt cuối)
- `backend/src/test/java/com/busgo/M30VoucherOptionsIT.java` (mới, 8 ca)
- `backend/src/test/java/com/busgo/M30VoucherVisibilityMigrationIT.java` (mới, 1 ca)
- `frontend/tests/m29-customer-browser.cjs` (thêm default options rỗng cho regression cũ, không bỏ assertion)
- `frontend/tests/m30-4a.test.mjs` (mới)
- `frontend/tests/m30-4a-browser.cjs` (mới)
- `docs/m30-4a-checkout-voucher-discovery.md` (mới)

## 4. Bảo mật và visibility

- Publicity là opt-in độc lập, không phải một điều kiện redeem. Mã private đang hợp lệ vẫn quote/create bằng nhập tay.
- Scope hold CUSTOMER và tenant/operatorRoute lấy từ server; không tin operator, route, giá hoặc discount do client gửi.
- Customer DTO là record whitelist, không trả management map, created_by/updated_by, raw usage/history, customer IDs, doanh thu, trạng thái của người khác hoặc trường pháp nhân/liên hệ riêng tư.
- Mô tả campaign chỉ xuất hiện sau khi Admin cho phép công khai; UI xác nhận rõ việc công bố mã/mô tả.
- Quote/options không chuyển trạng thái usage. Engine evaluate/reserve/finish/deadline/PromotionMoney, booking transaction, payments, cancellation/modification/refund và FinancialLedger giữ nguyên.
- Visibility ghi audit nhưng không tạo financial event. Không thay M22/M27B, payment rail, tenant ownership hoặc quyền Staff/System Admin.

## 5. Kiểm thử và kết quả

Evidence local ignored: `.tools/m30-4a/`. Không tạo dữ liệu hoặc sửa schema của database demo.

### Build / frontend

| Kiểm tra | Kết quả cuối | Evidence |
|---|---|---|
| TypeScript + Vite production build | **PASS** | `frontend-build-final.log` |
| Frontend unit/regression | **173 pass, 0 fail, 0 skip** | `frontend-tests-final.log` |
| Browser M29 + M30.4A, API fixture | **27 nhóm checks pass**, 55 captures; 0 page/console errors, 0 request thiếu fixture | `browser/acceptance.json`, `browser-final.log` |
| Browser giữ các luồng M30.2A/B, API fixture | **9 nhóm checks pass**, 29 captures; 0 page/console errors, 0 request thiếu fixture | `previous-journeys/acceptance.json`, `previous-journeys.log` |
| Whitespace | **PASS** | `git diff --check` |

Hai browser suite có assertion chung; các số nhóm là số lần chạy của từng harness, không cộng thành số ca nghiệp vụ duy nhất. Cả hai kiểm tra **1440/834/390/360**, không document-level horizontal overflow ở 84 captures. Có hit-testing CTA thực và kiểm tra không gửi booking bằng form khi quote chưa xong. Đã xem trực tiếp ảnh options desktop 1440 và quote đã chọn 360. Full-page PNG chụp sticky tại vị trí viewport lúc capture, không phải layout đặt header/CTA giữa nội dung.

Ba unit mới kiểm tra số tiền lấy nguyên từ server (không tính lại %), HTML được escape, status không khả dụng vô hiệu nút chọn, Staff/Admin và visibility độc lập term lock. Browser kiểm tra chọn/bỏ, mã tay riêng tư khi list rỗng, minimum/quota labels, phân trang, quote lỗi, booking từ chối mã, lỗi list/retry, đổi tài khoản, hold expiry, operator bật/tắt và Staff read-only. Các luồng M29/M30.2A/B cũ giữ assertion.

### Backend thật với MySQL

Instance **MySQL 9.2.0 mới** tại `127.0.0.1:3331`, datadir `.tools/m30-4a/mysql-data`, schema mới `busgo_m30_4a_test`. Flyway V1–V27/Hibernate validate. Không profile demo, không reseed/reset database cũ. Migration test tạo thêm một schema V26 → V27 có dữ liệu cũ để kiểm tra default false và bảo toàn điều khoản. Các schema/data được giữ lại.

| Lượt chạy | Kết quả đúng theo command | Evidence |
|---|---|---|
| Unit trong verify | **66 pass, 0 fail/error/skip** | `backend-integration.log` (cũng pass trong affected verify) |
| Tổng hợp MySQL liên quan ban đầu | **142 chạy: 141 pass, 1 failure, 0 error**; command **BUILD FAILURE** | `backend-integration.log` |
| Affected rerun cuối | **16/16 pass**, M30VoucherOptionsIT 8 + M24BConcurrencyIT 8; **BUILD SUCCESS** | `backend-affected-final.log` |
| Kết quả hợp nhất | Cả **142 ca integration duy nhất** có kết quả pass từ full run + affected rerun; không chạy lại toàn bộ suite lần hai | Hai log trên |

Phân bổ lượt tổng hợp: CoreDatabaseIT 44, M17CancellationIT 9, M20ModificationIT 28, M21PartialCancellationIT 15, M24BApiIT 4, M24BConcurrencyIT 8, M24BPromotionsIT 25, M30VoucherOptionsIT 8, M30VoucherVisibilityMigrationIT 1.

Failure ban đầu là test guest còn test authentication trong Spring Security MockMvc, nhận 403 thay vì 401. Đã dùng TestSecurityContextHolder cleanup và request anonymous rõ ràng; giữ assertion 401 và không sửa SecurityConfig/JWT/permission production. Một affected compile trung gian thiếu import của test race cũng được sửa. Browser chẩn đoán đầu tiên có locator ký hiệu `đ`/`₫`; sau đó phát hiện CTA thực sự bị summary che khi list mở. Đã sửa CSS và chạy lại toàn bộ browser, giữ hit-testing. Không dùng `failure.png`/log chẩn đoán làm evidence pass cuối.

| Tình huống | Bằng chứng / kết quả |
|---|---|
| Voucher cũ/mới mặc định private | PASS migration V26→V27 + options rỗng trước opt-in |
| Admin bật/tắt, Staff đọc, foreign tenant, SYSTEM_ADMIN/guest | PASS MySQL + Spring/MockMvc security; browser fixture Admin/Staff |
| Safe DTO, no-store, bound page | PASS MySQL/API assertions; không usage/customer/actor/internal fields |
| Cùng code khác nhà xe, sai operatorRoute | PASS M24B + M30 tests; options chỉ của operator/route từ hold |
| Draft/paused/future/expired/private không xuất hiện | PASS MySQL |
| Minimum spend, PERCENT/cap/fixed/rounding/min payable | PASS M30 + M24B/PROMOTION unit regression dùng engine cũ |
| Per-customer quota tính pending reservation | PASS MySQL; option UNAVAILABLE không quote/không chọn được |
| Quote/options không reserve | PASS MySQL; quote bằng cùng engine, usage count vẫn 0 |
| Expired giữa list và create | PASS MySQL; lỗi VOUCHER_EXPIRED, không consume hold/usage |
| Hai khách tranh lượt cuối | PASS MySQL concurrency; cả hai thấy AVAILABLE trước, chỉ một tạo booking, một VOUCHER_LIMIT_REACHED; hold bên thua còn nguyên |
| Transaction rollback | PASS M24B concurrency; booking/usage rollback và hold dùng lại được |
| Redeem/release, paid cancellation, partial cancellation, seat/trip change | PASS M24B/M17/M20/M21; giữ net/refund/discount entitlement |
| Đổi tài khoản, hold hết hạn | PASS backend ownership/expiry + browser fixture; không giữ options/selection voucher của user cũ |
| Private manual code | PASS MySQL quote trước opt-in; browser list rỗng vẫn áp mã riêng qua server quote |

Đây là **Spring/MockMvc với MySQL thật và transaction/concurrency thật**, không phải browser nối backend thật. Các response browser đều bị intercept; không tuyên bố E2E UI thật.

### Tái chạy

Frontend: chạy build/test như package scripts. Khởi động Vite `npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5190 --strictPort`, sau đó:

```powershell
node frontend/tests/m30-4a-browser.cjs
node frontend/tests/m30-4a-browser.cjs --previous-journeys
```

`--voucher-only` chỉ là chẩn đoán phần mới, không thay lượt regression cuối. Harness dùng Playwright runtime có sẵn/Edge; hỗ trợ `BUSGO_TEST_ORIGIN`, `PLAYWRIGHT_MODULE`. Fixture chuyến 15/01/2027 cần cập nhật nếu chạy sau ngày đó.

Backend: khởi động **đúng datadir test đã tạo, không initialize lại**; JAVA_HOME `.tools/jdk17/jdk-17.0.20.1+1`, DB_URL `jdbc:mysql://127.0.0.1:3331/busgo_m30_4a_test?connectionTimeZone=UTC`, DB_USERNAME root, password rỗng. Lệnh tổng hợp:

```powershell
mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration '-Dit.test=M30VoucherOptionsIT,M30VoucherVisibilityMigrationIT,M24BPromotionsIT,M24BConcurrencyIT,M24BApiIT,M17CancellationIT,M20ModificationIT,M21PartialCancellationIT,CoreDatabaseIT' '-Dspring.datasource.password='
```

## 6. Chưa nghiệm thu / giới hạn

- Chưa chạy browser ↔ backend thật end-to-end, chưa user review, Safari/iOS/Firefox, thiết bị cảm ứng hoặc screen reader chuyên dụng.
- Không chạy toàn bộ backend integration/financial/concurrency suite. Không gọi lượt tổng hợp ban đầu hoàn toàn xanh; bằng chứng backend là full run + affected recheck nêu rõ ở trên.
- Options và quote có thể stale sau khi hiển thị; final booking luôn revalidate và có thể từ chối. List/quote không bảo đảm quota.
- Không load-test nhiều campaign/usage. Page tối đa 20, UI 5; evaluate read theo từng option có giới hạn, chưa có batch optimization riêng.
- Visibility không tự kiểm duyệt nội dung. Nhà xe phải duyệt mô tả đủ điều kiện công bố trước khi bật; dữ liệu cũ không bị tự public.
- Chưa tạo campaign trong database demo hiện có; nội dung/sao/giá/token trong browser PNG là fixture, không phải ưu đãi thương mại có thật.
- MySQL 9.2 có warning compatibility Flyway; migrations/tests vẫn pass. Build còn warning Zod annotation và Notifications static/dynamic import có sẵn.

Không có blocker tiền/booking còn mở đã được xác nhận trong phạm vi này. Blocker CTA đã sửa và regression pass; chưa coi đây là chứng nhận production.

## 7. Dành cho M30.4B

1. Read DTO công khai riêng cho Home/Search/TripDetail, chỉ opt-in và còn hiệu lực; không dùng management API/map.
2. Phân biệt REQUIRES_CONTEXT/LOGIN/SEATS với eligibility theo hold; không quảng bá net price chưa có số ghế/người/quote.
3. Batch/bounded query phù hợp danh sách nhiều chuyến, cache đúng public/private và thay đổi visibility/pause/expiry/suspension.
4. Nghiệm thu dữ liệu rỗng, phạm vi tuyến thực, guest/login/deep link, quota stale và bảo mật. Không biến mọi ACTIVE thành public hoặc thêm banner giả.

M30.4A không thêm Home/Search/Trip voucher banner, ảnh/gallery, passenger-per-seat, payment gateway, M22 hoặc thay đổi M27B.

## Git và kết thúc

Đã dừng Vite 5190 và instance MySQL test 3331 do lượt này tạo. Giữ datadir, schema upgrade và toàn bộ evidence; không xóa hoặc reset database. Browser/DB test độc lập với dữ liệu demo.

Branch **main**. **25 tracked files modified, 18 untracked**, gồm toàn bộ thay đổi có sẵn. Riêng M30.4A sửa/thêm 18 file đã liệt kê. Không có staged changes. Các báo cáo M30 cũ giữ nguyên; `tatus --short` vẫn 2.945 byte, thời gian sửa 12/10/2026 16:09:36. Không stage/commit/push/merge/đổi branch/identity/remotes.

```text
 M backend/src/main/java/com/busgo/booking/BookingController.java
 M backend/src/main/java/com/busgo/booking/BookingService.java
 M backend/src/main/java/com/busgo/promotion/VoucherController.java
 M backend/src/main/java/com/busgo/promotion/VoucherDtos.java
 M backend/src/main/java/com/busgo/promotion/VoucherService.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchDtos.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchService.java
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/M24BConcurrencyIT.java
 M backend/src/test/java/com/busgo/M5TripSearchIT.java
 M frontend/src/components/ui.tsx
 M frontend/src/features/booking/CancellationSection.tsx
 M frontend/src/features/booking/holdStore.ts
 M frontend/src/features/customer/customer.css
 M frontend/src/features/customer/presentation.ts
 M frontend/src/features/marketplace/Marketplace.tsx
 M frontend/src/features/operator/Vouchers.tsx
 M frontend/src/features/search/SearchForm.tsx
 M frontend/src/pages/BookingDetailPage.tsx
 M frontend/src/pages/BookingPage.tsx
 M frontend/src/pages/SearchPage.tsx
 M frontend/src/pages/TripPage.tsx
 M frontend/src/types/customer.ts
 M frontend/tests/cancellation.test.mjs
 M frontend/tests/m29-customer-browser.cjs
?? backend/src/main/java/com/busgo/trip/search/PrebookingInformationMapper.java
?? backend/src/main/resources/db/migration/V27__voucher_customer_visibility.sql
?? backend/src/test/java/com/busgo/M30VoucherOptionsIT.java
?? backend/src/test/java/com/busgo/M30VoucherVisibilityMigrationIT.java
?? docs/m30-1-visual-assets-audit.md
?? docs/m30-2a-customer-journey.md
?? docs/m30-2b-prebooking-information.md
?? docs/m30-4a-checkout-voucher-discovery.md
?? docs/m30-product-experience-audit.md
?? frontend/src/features/booking/CheckoutVoucherOptions.tsx
?? frontend/src/features/trip/PrebookingDetails.tsx
?? frontend/tests/m30-2a-browser.cjs
?? frontend/tests/m30-2a.test.mjs
?? frontend/tests/m30-2b-browser.cjs
?? frontend/tests/m30-2b.test.mjs
?? frontend/tests/m30-4a-browser.cjs
?? frontend/tests/m30-4a.test.mjs
?? "tatus --short"
```

Dừng để người dùng review; chưa triển khai M30.4B.
