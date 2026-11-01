# M30.4B — Ưu đãi công khai trên Home / Search / Trip Detail

Ngày: **31/10/2026, giờ Việt Nam**. Repository `C:\Users\Giang\busgo`. Đã triển khai và kiểm thử; chờ người dùng review, không triển khai M30.5.

## 1. Audit, xác minh M30.4A và API/DTO mới

Đã đọc yêu cầu đính kèm, `m30-product-experience-audit.md` (đặc biệt mục 7/M30.4B), `m30-4a-checkout-voucher-discovery.md`, `m24b-promotions-vouchers.md`, source M30.4A và Home/Search/Trip. Git bắt đầu ở `main`, **25 tracked files modified, 18 untracked**; giữ nguyên M30.2A/B, M30.4A, V27 và các báo cáo cũ. Không tìm thấy AGENTS.md áp dụng.

### M30.4A đã được xác minh live trước khi mở rộng

Tạo mới MySQL instance/schema test độc lập, khởi động FE + BE, không API interception. Qua API hợp lệ: Admin tạo DRAFT (private mặc định), activate và bật visibility. Qua browser thật: Search card → guest chọn ghế → register → login → khôi phục đúng chuyến/ghế → server hold → voucher-options → chọn voucher → server quote → booking → mock payment. API xác nhận booking đúng gross/discount/net và đúng một lượt REDEEMED, không còn RESERVED.

Baseline live: booking **#1**, trip **#1**, voucher **#1**, gross **250.000**, giảm **25.000**, net **225.000**. Evidence `.tools/m30-4b/m304a-baseline/results.json`. Chỉ là dữ liệu thử nghiệm, không phải booking demo hiện có. Không tìm thấy blocker về tiền/quota/quyền/transaction trước khi mở rộng.

### Public endpoints

| Endpoint | Input | Phạm vi |
|---|---|---|
| `GET /api/v1/public/promotions` | Optional operatorId; page mặc định 0, size mặc định 6 | Campaign public hiện hành; eligibility `REQUIRES_CONTEXT` |
| `GET /api/v1/trips/{tripId}/promotions` | pickupLocationId, dropoffLocationId bắt buộc; page mặc định 0, size mặc định 5 | Resolve đúng hành trình và operatorRoute của trip; eligibility `CONDITIONAL` |

Cả hai paginated, page 0–100000, size 1–20, IDs positive; response `PagedResponse`, header `Cache-Control: no-store`. SecurityConfig chỉ cho phép GET hai đường dẫn này, không mở mutation/public management.

DTO whitelist `PublicPromotionDtos.Promotion`:

```text
id
operator { id, name }
route { id, name, pickupLocationId, dropoffLocationId } | null
code, name, description
discountType, discountValue, maxDiscount, minimumSpend
startsAt, endsAt, customerLimit
eligibility: REQUIRES_CONTEXT | CONDITIONAL
```

Không có customer/actor IDs, usage/history/reserved/redeemed counts, pháp nhân/liên hệ riêng tư, quote, discountAmount hoặc totalAmount. `route.id` là master route công khai cho CTA; backend vẫn tự dùng operatorRouteId đúng từ trip để kiểm tra phạm vi. Không coi route ID hai loại là một.

### Predicate công khai và Search projection

`PublicPromotionService` dùng một predicate chung cho list, trip list và Search SQL:

- `customer_visible=TRUE`, persisted ACTIVE, thời điểm hiện tại trong `[startsAt, endsAt)`.
- Nhà xe ACTIVE. Campaign theo tuyến cần association đúng operator và ACTIVE; campaign toàn nhà xe cần ít nhất một association ACTIVE, theo ranh commerce hiện tại.
- Global totalLimit chưa bị dùng hết bởi REDEEMED + RESERVED còn hạn. Reservation hết hạn không tiêu global capacity; không trả số lượt còn lại.
- Trip list dùng CustomerJourneyResolver hiện có, không sửa thuật toán chặng/fare và không tạo hold.
- Per-customer limit/minimum spend/minimum payable không được coi là đã thỏa khi chưa có hold. Public endpoint không gọi engine quote và không reserve.

SearchResult thêm boolean **`hasPublicPromotions`**. Nó được tính bằng SQL EXISTS trong projection của truy vấn search hiện có, cùng predicate và đúng operatorRoute. **Không có API theo từng card, không quote N chuyến**, không đổi filters/sort/count/pagination/giá gốc hoặc availability algorithm. Home/profile upcoming dùng cùng SearchResult cũng có thể nhận badge đúng dữ liệu.

Không thêm migration hoặc bảng mới; V27 của M30.4A giữ nguyên. Không sửa VoucherService.evaluate/reserve/finish/deadline, PromotionMoney, booking transaction, M20/M21, M24B, M27A, M22 hoặc M27B.

## 2. Màn hình thay đổi và file

### Home

Thêm section **“Ưu đãi từ nhà xe”** trong HomeDiscovery, dùng grid/cards/tokens M29. Card lấy API thực: nhà xe, tên/mã/description được công bố, mức giảm cấu hình, cap/minimum/per-customer condition, thời hạn giờ Việt Nam, phạm vi tuyến và CTA **“Tìm chuyến phù hợp”**. Không in giá sau giảm hoặc cam kết áp dụng.

Không có campaign thì empty state theo ngữ cảnh, không dựng campaign trong JSX. Có pagination, refresh, loading/error/retry. Không thêm ảnh, banner, gallery hoặc nội dung M30.3.

### Search

Chỉ hiện **“Có ưu đãi — xem điều kiện”** khi boolean từ backend là true. Link về đúng trip/chặng, giữ nested search context và mở anchor điều kiện. Không có giá gạch ngang, không tính net hoặc đổi sort theo discount.

### Trip Detail

Khối thu gọn **“Ưu đãi công khai cho chuyến — xem điều kiện”** sau sơ đồ/hành trình, trước thông tin trước mua. Badge mở đúng khối, khách vẫn chọn ghế bình thường. Có thể sao chép mã; trạng thái sao chép là local UI, không gọi hold/quote hoặc lưu voucher vào selection draft/sessionStorage. Copy thất bại có hướng dẫn nhập tay.

Mở/đóng/xem/copy không remount TripSelection, không mất ghế. Tên nhà xe trong card Trip là text; link hồ sơ dùng đường hiện có ở header Trip đã lưu selection draft, không tạo một đường rời trang bỏ qua draft. Mã sao chép không tự áp vào checkout. Checkout M30.4A tiếp tục dùng owned hold, options, quote và booking validation.

### File thuộc M30.4B (20)

Backend production:

- `backend/src/main/java/com/busgo/promotion/PublicPromotionDtos.java` (mới)
- `backend/src/main/java/com/busgo/promotion/PublicPromotionService.java` (mới)
- `backend/src/main/java/com/busgo/promotion/PublicPromotionController.java` (mới)
- `backend/src/main/java/com/busgo/trip/search/TripSearchDtos.java`
- `backend/src/main/java/com/busgo/trip/search/TripSearchRepository.java`
- `backend/src/main/java/com/busgo/common/security/SecurityConfig.java`

Frontend production:

- `frontend/src/features/marketplace/PublicPromotions.tsx` (mới)
- `frontend/src/features/marketplace/Marketplace.tsx`
- `frontend/src/components/ui.tsx`
- `frontend/src/pages/SearchPage.tsx`
- `frontend/src/pages/TripPage.tsx`
- `frontend/src/types/customer.ts`
- `frontend/src/features/customer/customer.css`

Test/report:

- `backend/src/test/java/com/busgo/FoundationTest.java` (mock service mới trong context không DB)
- `backend/src/test/java/com/busgo/M30PublicPromotionsIT.java` (mới, 7 ca)
- `frontend/tests/m29-customer-browser.cjs` (default fixture public promotions rỗng, giữ assertions)
- `frontend/tests/m30-4b.test.mjs` (mới, 4 ca)
- `frontend/tests/m30-4b-browser.cjs` (mới)
- `frontend/tests/m30-4b-live.cjs` (mới)
- `docs/m30-4b-public-promotions.md` (mới)

Các thay đổi M30 cũ vẫn giữ, không sửa các báo cáo cũ hoặc dashboard Operator/System Admin.

## 3. Hành trình khám phá đến Checkout

1. Home hiển thị chương trình **đang được quảng bá**, chưa xác định giá/eligibility cá nhân.
2. CTA campaign theo tuyến giữ operator + hai endpoint và để khách chọn ngày. Campaign không giới hạn tuyến dẫn đến tìm hành trình theo nhà xe, không tự tạo chuyến hoặc hứa có chuyến vào ngày đó.
3. Search giữ giá gốc, badge chỉ cho biết có chương trình **có thể phù hợp**, dẫn sang điều kiện của đúng trip.
4. Trip hiển thị các public campaign đúng operator/association, có điều kiện về số ghế/gross/lượt cá nhân. Xem/copy không giữ lượt và không giữ ghế.
5. Guest login/register qua returnTo và selection draft cũ, kiểm tra availability lại. Không đưa voucher vào draft để tự áp sang tài khoản khác.
6. Sau hold thành công, checkout options xác định eligibility theo customer/hold; khách chủ động chọn mã rồi quote. Tạo booking revalidate/lock/reserve; chỉ committed booking mới xác nhận giá/quota. M30.4A recovery khi quote/create lỗi giữ nguyên.

## 4. Công khai, bảo mật và freshness

- Private/DRAFT/PAUSED/future/expired/nhà xe hoặc association không hoạt động/global exhausted không xuất hiện ở public read. Không dùng operator management API cho khách.
- Guest và customer nhận cùng public DTO; không ghép per-customer usage vào public response. DTO không có net quote, không tiết lộ ai đã dùng hoặc chính xác còn bao nhiêu lượt.
- Query cache frontend dùng prefix `public-promotions`, tách khỏi `checkout-voucher-options` và `checkout-voucher` scoped user/hold. Public UI không ghi selected voucher vào storage, không tự áp mã từ clipboard.
- Public lists và Search refresh mỗi 30 giây, refetch theo focus/mount; public list có nút refresh. Frontend loại card cache đã qua endsAt mỗi giây. Không có push realtime; visibility/pause/expiry có thể thay đổi giữa snapshot và bước đặt vé.
- Hidden public code từng được copy không bị thu hồi khỏi clipboard; điều này không biến clipboard thành eligibility hoặc auto-apply. Visibility vẫn chỉ điều khiển discovery, mã private hợp lệ nhập tay theo M24B.
- Các GET chỉ đọc, không viết usage/booking/inventory/financial records. Không thêm loyalty, stacking, platform subsidy hoặc payment gateway.

## 5. Kiểm thử: pass/fail và mức bằng chứng

Evidence ignored: `.tools/m30-4b/`.

### Frontend / browser fixture

| Kiểm tra | Kết quả | Evidence |
|---|---|---|
| TypeScript + Vite build | **PASS** | `frontend-build.log` |
| Frontend unit/regression | **177 pass, 0 fail, 0 skip** | `frontend-tests-final.log` |
| Browser M29 + M30.4B fixture | **27 nhóm pass**, 49 captures, 0 page/console errors, 0 request thiếu fixture | `browser/acceptance.json`, `browser-final.log` |
| Checkout/visibility M30.4A fixture rerun | **5 nhóm pass**, 16 captures, 0 page/console errors, 0 request thiếu fixture | `checkout-regression/acceptance.json`, `checkout-regression.log` |
| Whitespace | **PASS** | `git diff --check` |

Cả fixture suites có matrix **1440/834/390/360**; không document-level horizontal overflow ở captures. Các số nhóm là lượt assertions/harness, không cộng thành unique business cases. Đã xem trực tiếp ảnh Trip fixture 390 và Home live 360. Full-page PNG ghi sticky ở vị trí viewport lúc chụp, không phải bố cục đặt header/CTA giữa nội dung.

Fixture coverage: Home zero/many public offers; pagination; không có private code; CTA yêu cầu chọn ngày; Search badge/context/giá gốc; Trip mở terms/copy thật qua clipboard mà không mất ghế/không POST hold/quote; login return; checkout input chưa tự áp mã và chỉ quote sau chọn rõ ràng; đổi tài khoản không dùng lại hold/voucher cũ; refresh hidden/paused response, expired cache, loading/error/retry. Regression M30.4A kiểm tra chọn/bỏ, minimum/quota labels, mã private nhập tay, quote/create rejection, account/hold expiry, CTA và Admin/Staff.

**Các API trong browser fixture đều bị intercept**, không dùng những PNG đó để tuyên bố backend thật. Bốn unit mới kiểm tra public terms không có quote/net, CTA đúng endpoint/operator nhưng không tự gán ngày, Trip anchor/copy không claim hold, Search badge chỉ theo projection mà giá gốc/context không đổi.

### Backend MySQL thật

Instance mới **MySQL 9.2.0**, bind `127.0.0.1:3332`, datadir `.tools/m30-4b/mysql-data`.

- `busgo_m30_4b_live`: FE/BE live, Flyway V1–V27; lần khởi động đầu tạo mới catalogue hư cấu 21 chuyến với profile dev,demo. Các lần nạp code tiếp theo dùng **dev không demo**, không reseed.
- `busgo_m30_4b_test`: integration riêng, không seeder. Không kết nối/reset/reseed DB demo hiện có.

| Lượt chạy | Kết quả đúng theo command |
|---|---|
| Verify đầu | Unit 66: 60 pass / 6 errors do Foundation context chưa mock PublicPromotionService; command FAIL, chưa chạy integration. `backend-integration.log` |
| Verify sau bổ sung mock | Unit **66/66 pass**; MySQL **159 chạy: 158 pass, 1 failure, 0 error**; command **BUILD FAILURE**. `backend-integration-final.log` |
| Affected rerun cuối | M30PublicPromotionsIT **7/7 pass**, **BUILD SUCCESS**. `backend-affected.log` |
| Hợp nhất | Cả **159 ca integration duy nhất** có kết quả pass từ full run + affected rerun; không gọi full run xanh hoặc đã chạy lại toàn bộ lần hai |

Phân bổ 159: CoreDatabaseIT 44; M17CancellationIT 9; M20ModificationIT 28; M21PartialCancellationIT 15; M24BApiIT 4; M24BConcurrencyIT 8; M24BPromotionsIT 25; M30PublicPromotionsIT 7; M30VoucherOptionsIT 8; M5TripSearchIT 11.

Failure MySQL duy nhất là fixture ghi `expires_at` bằng raw UTC_TIMESTAMP thay vì timestamp binder của ứng dụng. Đã sửa fixture dùng `JpaJdbcTime.parameter`, thêm assertion capacity đã hết hạn là 0, giữ assertion campaign xuất hiện trở lại. Không sửa production time handling, M24B engine, quota hoặc tiền để làm test xanh.

Các ca mới kiểm tra: opt-in/private default; public DTO whitelist và header, guest/customer payload giống nhau; không quote/reserve; same code khác tenant, đúng/sai operatorRoute; DRAFT/PAUSED/future/expired/private; Search flag đổi theo visibility mà price/pagination không đổi; quota bị dùng hết giữa public view và booking làm create từ chối; expired RESERVED không tiêu capacity; operator/association inactive; pagination/validation và không cho customer mutate public endpoint. M24B giữ race lượt cuối/rollback/redeem/release/M20/M21/net refund.

Đây là Spring/MockMvc + MySQL/transaction/concurrency thật, khác với browser fixture.

### Live FE → BE → MySQL thật

FE `127.0.0.1:5190` → BE `127.0.0.1:8100` → schema live mới trên MySQL 3332. **Không đăng ký API interception.** Mail bị tắt. Voucher được tạo/activate/public bằng API Admin hợp lệ, customer register/login/seat/checkout/booking/payment bằng browser UI.

Lượt cuối trong `m304b-live/results.json`:

- Home public card → CTA → chọn ngày **01/11/2026** → Search card có badge → Trip đúng chặng, xem terms vẫn giữ ghế.
- Guest → register → login → đúng trip và selection được kiểm tra lại → hold → options → chọn mã → quote → booking → mock payment.
- Booking **#3**, trip **#1**, voucher **#3**: gross **250.000**, giảm **25.000**, net **225.000**; API booking khớp quote, payment redeem đúng 1 và RESERVED=0.
- Kiểm tra private mặc định trước opt-in, visibility off làm public API ẩn campaign; bật lại rồi pause và refresh Home làm card biến mất; booking đã commit vẫn **225.000**.
- **5 nhóm checks pass**, 0 page exceptions; 6 captures: Home 1440/834/390/360, Checkout 1440/390. Không tuyên bố đã đo mọi console/network error hoặc mọi destructive flow qua live UI.

Một lượt live trung gian tạo booking #2 trước khi bổ sung assertions refresh/badge; được giữ nguyên, không reset dữ liệu. Tổng có ba booking test đã mock-pay qua các lượt baseline/trung gian/cuối, không phải booking demo của người dùng.

Có thể gọi luồng thành công nêu rõ ở trên là kiểm chứng end-to-end live; không coi mọi fixture, mọi policy/quota race hoặc toàn bộ ứng dụng đã E2E.

## 6. Vấn đề còn tồn tại / giới hạn

- Không có blocker giá/quota/quyền/transaction còn được xác nhận trong phạm vi này. Không chứng nhận production hoặc mọi concurrency race.
- Public listing chỉ là snapshot quảng bá/có điều kiện; checkout/booking vẫn có thể từ chối vì minimum spend, per-customer limit, quota/state/fare vừa thay đổi.
- Không push realtime; refresh 30 giây/focus/manual. Chưa load-test quy mô lớn; SQL correlated capacity EXISTS/COUNT cần đo bằng dữ liệu lớn trước tối ưu thêm.
- Browser live mới kiểm chứng happy path được mô tả, không chạy mọi hủy/đổi/quota-race bằng UI live. Những phần đó là MySQL regression.
- Chưa thử Safari/iOS/Firefox, screen reader chuyên dụng, cảm ứng thật, mọi timezone/clock-drift/network failure. Clipboard có fallback nếu browser không cho quyền.
- MySQL 9.2 có Flyway compatibility warning; migration/test vẫn pass. Build giữ warning Zod annotation và Notifications static/dynamic import có sẵn.
- Không thêm địa chỉ/hướng dẫn/ảnh còn thiếu của M30.2B/M30.3, không làm M30.5, không sửa các báo cáo cũ.

## 7. Chuẩn bị database demo để nhìn thấy ưu đãi thực tế

Không tạo campaign trong database demo hiện có ở lượt này. Để demo sau khi người dùng duyệt code:

1. Backend/schema cần có additive V27 của M30.4A; dữ liệu cũ mặc định private. Không sửa migration cũ hoặc reseed để ép public.
2. Đăng nhập **Operator Admin đúng nhà xe**, tạo voucher qua UI/API hiện có; chuẩn bị tên/description có quyền công bố. Không đưa thông tin riêng tư vào description.
3. Chọn thời gian campaign bao phủ **thời điểm khách đặt vé**, không chỉ ngày khởi hành. Activate và **Bật công khai** là hai thao tác riêng; chỉ ACTIVE chưa đủ.
4. Nhà xe và operatorRoute phải active; route-restricted voucher phải trỏ đúng association của nhà xe. Chuẩn bị chuyến thật trong môi trường demo còn chỗ/ngày tương lai qua nghiệp vụ hiện có, không reset/reseed.
5. Minimum spend/cap/total/customer limits cần phù hợp với giá và số ghế; phải còn minimum payable theo M24B. Global quota đã hết thì Home/Search không quảng bá, mã còn hiệu lực nhưng private chỉ nhập tay ở checkout.
6. Reload/focus hoặc “Làm mới ưu đãi công khai”; chọn ngày có chuyến. Không thấy card khi không có campaign đủ điều kiện là trạng thái hợp lệ.

Ảnh do người dùng cung cấp ở M30.3, không cần ảnh để voucher discovery hoạt động.

Tái chạy frontend: scripts build/test hiện có; Vite proxy phải trỏ BE test 8100. Fixture: `node frontend/tests/m30-4b-browser.cjs`. **Live harness hiện bị khóa theo nguyên tắc fail-closed:** cần explicit opt-in `--allow-live-mutations`, nhưng opt-in, URL loopback, health=UP và biến môi trường tự khai báo đều không chứng minh được database đích. Source chưa có verifier độc lập cho backend/proxy/database nên harness từ chối trước khi mở browser hoặc gửi HTTP, kể cả khi có opt-in và `--baseline`. Không thêm production endpoint để vượt guard trong thay đổi này. Khi có cơ chế xác minh đáng tin cậy sau này mới có thể mở lại live acceptance; kết quả live bên trên là bằng chứng lịch sử trước guard, không phải lượt chạy mới. Playwright dùng `PLAYWRIGHT_MODULE` hoặc package `playwright`, không phụ thuộc đường dẫn máy cá nhân; nếu thiếu module sẽ có hướng dẫn rõ khi resolve. Fixture dùng ngày 15/01/2027, cần cập nhật sau ngày đó.

MySQL test datadir đã tồn tại: không initialize lại. Dùng JDK `.tools/jdk17/jdk-17.0.20.1+1`, DB_URL `jdbc:mysql://127.0.0.1:3332/busgo_m30_4b_test?connectionTimeZone=UTC`, user root/password rỗng, các suite ở mục 5 với `mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration ... '-Dspring.datasource.password='`.

## 8. Git status cuối

Đã dừng FE 5190, BE 8100 và MySQL test 3332 do lượt này tạo; giữ hai schema/datadir và evidence. Không xóa/reset database. Rà soát cuối loại link hồ sơ phụ trong card Trip để không bỏ qua selection draft; build và 177 frontend tests đã chạy lại sau chỉnh sửa này.

Branch **main**; **28 tracked files modified, 27 untracked**, gồm toàn bộ thay đổi M30 đang chờ review. Riêng M30.4B sửa/thêm 20 file ở mục 2. Không có staged changes; không commit/push/merge, không đổi branch/identity/remotes. Các báo cáo M30 cũ giữ nguyên. `tatus --short` vẫn 2.945 byte, thời gian sửa 12/10/2026 16:09:36.

```text
 M backend/src/main/java/com/busgo/booking/BookingController.java
 M backend/src/main/java/com/busgo/booking/BookingService.java
 M backend/src/main/java/com/busgo/common/security/SecurityConfig.java
 M backend/src/main/java/com/busgo/promotion/VoucherController.java
 M backend/src/main/java/com/busgo/promotion/VoucherDtos.java
 M backend/src/main/java/com/busgo/promotion/VoucherService.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchDtos.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchRepository.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchService.java
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
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
?? backend/src/main/java/com/busgo/promotion/PublicPromotionController.java
?? backend/src/main/java/com/busgo/promotion/PublicPromotionDtos.java
?? backend/src/main/java/com/busgo/promotion/PublicPromotionService.java
?? backend/src/main/java/com/busgo/trip/search/PrebookingInformationMapper.java
?? backend/src/main/resources/db/migration/V27__voucher_customer_visibility.sql
?? backend/src/test/java/com/busgo/M30PublicPromotionsIT.java
?? backend/src/test/java/com/busgo/M30VoucherOptionsIT.java
?? backend/src/test/java/com/busgo/M30VoucherVisibilityMigrationIT.java
?? docs/m30-1-visual-assets-audit.md
?? docs/m30-2a-customer-journey.md
?? docs/m30-2b-prebooking-information.md
?? docs/m30-4a-checkout-voucher-discovery.md
?? docs/m30-4b-public-promotions.md
?? docs/m30-product-experience-audit.md
?? frontend/src/features/booking/CheckoutVoucherOptions.tsx
?? frontend/src/features/marketplace/PublicPromotions.tsx
?? frontend/src/features/trip/PrebookingDetails.tsx
?? frontend/tests/m30-2a-browser.cjs
?? frontend/tests/m30-2a.test.mjs
?? frontend/tests/m30-2b-browser.cjs
?? frontend/tests/m30-2b.test.mjs
?? frontend/tests/m30-4a-browser.cjs
?? frontend/tests/m30-4a.test.mjs
?? frontend/tests/m30-4b-browser.cjs
?? frontend/tests/m30-4b-live.cjs
?? frontend/tests/m30-4b.test.mjs
?? "tatus --short"
```

Dừng để người dùng review; không triển khai M30.5.
