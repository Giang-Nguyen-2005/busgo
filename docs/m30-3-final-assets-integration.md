# M30.3A Final Assets + M30.3B Interiors + Home Promotional Cards

Ngày 06/11/2026, giờ Việt Nam. Workspace `C:\Users\Giang\busgo`. Chờ người dùng review; không commit/push/merge.

## Đầu vào và phạm vi

Đã đọc M30 product experience audit, M30.1 visual audit, M30.3A đang làm và M30.4B public promotions; đối chiếu HomePage, Marketplace, TripCard, TripPage, visualAssets, router và catalogue backend (chỉ đọc). Branch `main`, HEAD `ac5297fe5de6bf87792fd1e179f8867df4d491e9`. Đầu vào đã có 3 tracked frontend files thay đổi cùng component/test/report M30.3A untracked và 15 ảnh mới. Tiếp tục trên các thay đổi đó, không reset/stash/ghi đè công việc trước. Không mở/sửa file `tatus --short`.

Chỉ frontend/static assets và tài liệu/test. Giữ hero-coach.jpg, M29 tokens, search/navigation và booking flows. Không sửa backend/DB/Flyway/seeder, auth/isolation/availability/hold/payment/ticket/cancellation hoặc voucher quote/redeem. Không gọi mutation live.

## 1. Asset đã nhận và tối ưu

Đủ **15/15 ảnh mới**, không thiếu file trong danh sách người dùng. Đã giải mã bằng Pillow để xác minh định dạng thật, xem contact sheet trước tích hợp. Không tải/tạo ảnh thay thế. Giữ nguyên toàn bộ JPG/PNG/WebP gốc; tạo 15 WebP tối ưu bằng encoder WebP thật (quality 85, method 6), cạnh tối đa 1200px; logo 512px, giữ tỷ lệ/transparency. Riêng Đà Nẵng giữ bản WebP gốc và thêm `-optimized.webp`.

Tổng nguồn **17.100.968 byte** → các bản tối ưu **2.203.958 byte**, giảm khoảng **87,1%**. Đây là tổng thư viện gồm cả 3 ảnh chưa bật, không phải dung lượng mỗi page load. Hero cùng 3 ảnh ngoại thất cũ giữ nguyên.

| File nguồn | Định dạng/kích thước thật | File tối ưu trong `frontend/public/images/busgo/` | Byte nguồn → mới | Nơi sử dụng / trạng thái |
| --- | --- | --- | --- | --- |
| `destination-buon-ma-thuot.jpg` | JPEG, 1024×583 | `destination-buon-ma-thuot.webp` | 378,253 → 218,676 | Home: thẻ tuyến, đối chiếu location API |
| `destination-da-lat.jpg` | JPEG, 1920×1233 | `destination-da-lat.webp` | 607,213 → 174,202 | Home: thẻ tuyến, đối chiếu location API |
| `destination-da-nang.webp` | WEBP, 1199×898 | `destination-da-nang-optimized.webp` | 336,648 → 174,832 | Home: thẻ tuyến, đối chiếu location API |
| `destination-ho-chi-minh.jpg` | JPEG, 2340×1281 | `destination-ho-chi-minh.webp` | 512,619 → 164,734 | Home: thẻ tuyến, đối chiếu location API |
| `destination-hue.jpg` | JPEG, 1200×675 | `destination-hue.webp` | 161,084 → 145,752 | Home: thẻ tuyến, đối chiếu location API |
| `destination-nha-trang.jpg` | JPEG, 1920×1080 | `destination-nha-trang.webp` | 189,050 → 148,874 | Home: thẻ tuyến, đối chiếu location API |
| `interior-limousine-cabin.png` | PNG, 1448×1086 | `interior-limousine-cabin.webp` | 2,591,168 → 166,928 | Đã tối ưu, chưa bật: contract chưa xác nhận cabin/VIP |
| `interior-limousine-seat-vip.png` | PNG, 1448×1086 | `interior-limousine-seat-vip.webp` | 2,599,092 → 168,034 | Đã tối ưu, chưa bật: contract chưa xác nhận cabin/VIP |
| `interior-sleeper-standard.png` | PNG, 1448×1086 | `interior-sleeper-standard.webp` | 2,869,516 → 184,050 | TripCard Home/Search/profile + Trip Detail |
| `operator-an-phu-express-logo.png` | PNG, 724×724 | `operator-an-phu-express-logo.webp` | 213,334 → 54,158 | Home + public operator profile, đúng tên demo |
| `operator-minh-thanh-limousine-logo.png` | PNG, 724×724 | `operator-minh-thanh-limousine-logo.webp` | 211,654 → 48,690 | Home + public operator profile, đúng tên demo |
| `operator-tay-nguyen-travel-logo.png` | PNG, 724×724 | `operator-tay-nguyen-travel-logo.webp` | 246,348 → 60,544 | Home + public operator profile, đúng tên demo |
| `promo-booking.png` | PNG, 1254×1254 | `promo-booking.webp` | 2,124,009 → 167,636 | Home → #public-promotions |
| `promo-featured.png` | PNG, 1254×1254 | `promo-featured.webp` | 2,071,552 → 176,700 | KHÔNG công khai: chữ Giảm đến 20% chưa có campaign đối chiếu |
| `promo-operators.png` | PNG, 1254×1254 | `promo-operators.webp` | 1,989,428 → 150,148 | Home → #public-operators |

Metadata đầy đủ: `.tools/m30-3-final/assets.json`; contact sheet đã xem: `.tools/m30-3-final/input-sheet.jpg`. Nguồn là file người dùng chuẩn bị trong workspace; không tự xác nhận giấy phép bên thứ ba hay ảnh thực của doanh nghiệp.

## 2. Mapping và component

### Điểm đến

Manifest bật đủ 6 ảnh. Discovery chỉ render route API trả về, lấy dropoffLocationId → location API → đối chiếu chính xác tên địa danh/bến trong manifest. Không dùng ID seeder, tên tuyến/province hoặc ảnh thành phố khác. API location thiếu/khác tên/lỗi hoặc ảnh lỗi dùng MapPin. `/locations` hiện chỉ trả phạm vi giới hạn 20 theo contract cũ; địa điểm ngoài response vẫn fallback. Thumbnail 80×60, object-fit cover, nội dung route/CTA được giữ.

### Nội thất

`VehicleImage` dùng chung trong TripCard (Home, Search, public operator profile) và Trip Detail. Không dùng busImageUrl ngoại thất làm nội thất.

- Chỉ `busType.name = Giường nằm 34 chỗ` (chuẩn hóa NFC, khoảng trắng, case) được map `interior-sleeper-standard.webp`. Catalogue hiện tại xác nhận giường nằm hai tầng, lối đi giữa. ID tùy ý không ảnh hưởng.
- `Limousine 22 chỗ` có mô tả `Ghế limousine 1+1, một tầng`; không xác nhận ghế VIP hoặc giường phòng. `Ghế ngồi 40 chỗ`, limousine chung và các tên khác dùng icon + “Chưa có ảnh nội thất phù hợp”. Không suy từ STANDARD hoặc số chỗ thành VIP/cabin; không sửa sơ đồ ghế.
- Cabin/VIP được encode và có key trong manifest nhưng chưa trả từ mapping. Khi có contract/cấu hình đã xác nhận mới bật mapping tương ứng.
- Ảnh có nhãn nhìn thấy **“Ảnh minh họa loại xe”**, alt minh họa và lưu ý không xác nhận trang bị từng xe. Card thumbnail 136px desktop/112px tablet; mobile 112×84, luôn có nút xem ảnh, không che CTA/giá/giờ.
- Modal dùng native dialog/showModal, tiêu đề accessible, focus nút Đóng, Tab/Shift+Tab giữ trong dialog (hiện chỉ một control), Escape/Đóng đóng và trả focus về trigger. Khóa scroll nền khi mở, khôi phục khi đóng/unmount. Không remount TripSelection hay ghi hold/draft. Đổi loại xe reset lỗi ảnh/modal; ảnh lỗi dùng icon, không lặp request.

### Logo

API logoUrl hợp lệ được ưu tiên. Khi không có URL hợp lệ, map chính xác 3 tên đã đối chiếu catalogue: An Phú Express, Minh Thành Limousine, Tây Nguyên Travel. Không fuzzy match/ID seeder; tên gần giống giữ icon. Component đánh dấu alt/title nhận diện demo hư cấu. Logo contain trong khung 72×72, lỗi ảnh về BusFront; đổi URL reset failure state. Không ghi logoUrl vào DB, không dùng ProfileForm để sửa live.

## 3. Home và public promotions

Một khối “Ưu đãi nổi bật” gộp các thẻ khám phá với **PublicPromotions M30.4B hiện có**, không tạo voucher system hoặc fetch campaign thứ hai.

1. promo-booking → `#public-promotions`: danh sách chương trình công khai hiện có.
2. promo-operators → `#public-operators`: khu vực nhà xe công khai có link profile thực.
3. Thẻ trung tính “Lên kế hoạch hành trình” → `#journey-search`: form hiện có. Thẻ dùng icon/text để giữ 3 cột; không phát hành ảnh promo-featured.

Desktop 3 card cạnh nhau, vùng ảnh vuông 1:1, contain để không cắt chữ. Mobile cuộn ngang scroll-snap trong container, card rộng 82%, không tràn document. Caption nêu đây là nội dung khám phá; điều kiện/chương trình chỉ đến từ PublicPromotions. Khi không có active campaign, giữ empty state hiện có và khách vẫn tìm chuyến được. Không thêm route `/promotions` giả.

**promo-featured.png / WebP có chữ “Giảm đến 20%”**. Chưa xác minh campaign tương ứng nên file này không được tham chiếu trên UI. Cần người dùng sửa ảnh thành nội dung không hứa mức giảm, hoặc xác nhận một campaign/backend association đúng trước khi triển khai liên kết theo campaign. Không tự tạo campaign live để hợp thức hóa ảnh.

## 4. File sửa/tạo

Tính cả thay đổi M30.3A được kế thừa:

- `frontend/src/pages/HomePage.tsx` — tiếp tục HeroArt/FAQ trước đó, hero vẫn ảnh cũ.
- `frontend/src/features/marketplace/Marketplace.tsx` — Home khối banner + public promotions, anchor nhà xe, tiếp tục logo/destination API mapping.
- `frontend/src/features/marketplace/marketplace.css` — logo/destination M30.3A + bố cục banner responsive.
- `frontend/src/features/customer/visualAssets.ts` — manifest 6 destinations, 3 logo demo, 3 interiors với mapping giới hạn.
- `frontend/src/features/customer/CustomerMedia.tsx` — AssetImage/OperatorLogo/DestinationImage/HeroArt đã có; thêm VehicleImage/modal.
- `frontend/src/features/customer/CustomerFaq.tsx` — giữ phần trước đó.
- `frontend/src/features/marketplace/HomePromotionalCards.tsx` — mới.
- `frontend/src/components/ui.tsx` — TripCard dùng VehicleImage.
- `frontend/src/pages/TripPage.tsx` — thêm VehicleImage sau seat map, giữ map ở gần đầu.
- `frontend/src/features/customer/customer.css` — preview/modal/responsive; ghi đè aspect-ratio legacy để không tạo vùng trống mobile.
- `frontend/src/features/marketplace/PublicPromotions.tsx` — thêm anchor Home, logic campaign không đổi.
- `frontend/tests/m30-3a.test.mjs`, `m30-3a-browser.cjs` — cập nhật ảnh điểm đến đã được cấp.
- `frontend/tests/customer-admin-ux.test.mjs` — đổi kỳ vọng từ ngoại thất chung sang icon khi loại xe chưa đủ mapping; giữ kiểm tra ngày qua đêm/search/CTA.
- `frontend/tests/m30-3-final.test.mjs`, `m30-3-final-browser.cjs`, `frontend/tests/fixtures/customer-media.html` — mới, asset/mapping/banner/modal/error/reset + customer regression. HTML fixture được Vite phục vụ trực tiếp cho test, không là entry production build.
- 15 WebP ở bảng trên; giữ 15 file nguồn.
- `docs/m30-3-final-assets-integration.md` (báo cáo này); thêm liên kết cập nhật vào báo cáo M30.3A cũ.

## 5. Kiểm chứng

| Kiểm tra | Kết quả cuối | Evidence |
| --- | --- | --- |
| TypeScript + Vite production build | PASS | `.tools/m30-3-final/frontend-build-final.log` |
| Frontend tests | **191/191 pass**, 0 fail/skip | `.tools/m30-3-final/frontend-tests-final.log` |
| Browser ảnh — Edge headless, API fixture, ảnh WebP thật | **3 nhóm pass**, 16 captures ghi trong JSON + 6 PNG chụp trực tiếp; 1440/1100/834/390/360px | `.tools/m30-3-final/browser/acceptance.json`, `media-final.log` |
| Customer journey regression — Edge, API fixture | **22 nhóm pass**, 11 captures | `.tools/m30-3-final/customer-acceptance-final.json`, `customer-regression-final.log` |
| Public promotions M30.4B regression — Edge, API fixture | **5 nhóm pass**, 10 captures | `.tools/m30-3-final/promotions-acceptance-final.json`, `promotions-regression.log` |
| Whitespace | `git diff --check` PASS | Chỉ có cảnh báo chuyển LF/CRLF đã có trong HomePage |

Ba browser reports cuối đều **0 page errors, 0 console errors, 0 request thiếu API fixture**. Không phát hiện static asset 404 khi chạy trang thật bằng fixture; có test chủ động dùng URL thiếu ở media harness để xác nhận fallback. Các số nhóm không phải tổng số business cases độc lập. Browser ảnh kiểm tra các viewport, không overflow document, banner anchors/3 cards, không promo-featured, empty promotion, ảnh load thật, focus/Tab/Escape/Đóng/focus return và giữ selected seat không POST khi xem ảnh. Interior error test chủ động phát `error` event lên ảnh thật (tránh decoded-image cache của browser); logo/destination test dùng URL không tồn tại. Đổi loại xe qua fallback rồi quay lại ảnh hợp lệ và đổi logo đều khôi phục đúng.

Customer regression gồm autocomplete/IDs/đổi chiều, ngày đi, filters/pagination/back, seat geometry/floors/5-seat limit, conflict, guest login + selection draft, polling availability, hold expiry, contact/quote, booking/payment recovery, mock payment/QR hợp lệ-void-thiếu, My Bookings, discovery empty/error, reduced motion. Public regression gồm zero/many/pagination, đúng search context/terms/copy, giữ ghế, không tự áp mã sau login, quote chỉ sau chọn, đổi tài khoản không tái dùng hold/voucher, refresh hidden/expired/error. Đây đều là **fixture**, không phải live FE→BE→DB; không chạy live harness hoặc backend integration cho thay đổi frontend/static này.

Lượt thử ban đầu: Vite bị EPERM realpath trong sandbox; build ngoài sandbox thành công. Có test cũ kỳ vọng ngoại thất chung, đã cập nhật sang icon fallback phù hợp yêu cầu mới. Harness ảnh ban đầu có selector A01 không khớp accessible name, fixture reviews còn thiếu và lỗi websocket của HTML intercept; đã sửa harness dùng đúng tên ghế, bổ sung reviews fixture và HTML phục vụ trực tiếp. Các kết quả cuối ở bảng đều sạch. Rà ảnh phát hiện CSS legacy áp aspect-ratio 16/7 tạo khoảng trống mobile và reset margin của dialog; đã sửa, chụp lại và kiểm tra lại. Build giữ warning Zod pure annotation/Notifications chunk có sẵn; không thêm dependency.

Lệnh tái chạy (PowerShell, từ repo; Vite đang phục vụ 5191):

```powershell
npm --prefix frontend run build
npm --prefix frontend test
$env:BUSGO_TEST_ORIGIN='http://127.0.0.1:5191'
node frontend/tests/m30-3-final-browser.cjs --media-only
node frontend/tests/m29-customer-browser.cjs --interactions-only
node frontend/tests/m30-4b-browser.cjs --public-only
```

`node frontend/tests/m30-3-final-browser.cjs` không flag sẽ chạy cả matrix M29 7 màn hình × 4 viewport, interactions và media acceptance. PLAYWRIGHT_MODULE có thể dùng để chỉ định package Playwright; harness kế thừa fallback local từ M29 hiện có. Evidence `.tools/` ignored, không đưa vào commit. Lượt này chỉ khởi động Vite local cho test và đã dừng sau nghiệm thu; không khởi động/mutate DB.

### Screenshot đã chụp và xem

Thư mục tuyệt đối: `C:\Users\Giang\busgo\.tools\m30-3-final\browser\`.

- `assets-home-1440.png`, `assets-home-1100.png`, `assets-home-834.png`, `assets-home-390.png`, `assets-home-360.png`.
- `promotions-1440.png`, `promotions-1100.png`, `promotions-834.png`, `promotions-390.png`, `promotions-360.png` (viewport khu vực banner).
- `interior-search-1440.png`, `interior-search-1100.png`, `interior-search-834.png`, `interior-search-390.png`, `interior-search-360.png`.
- `interior-trip-1440.png`, `interior-trip-1100.png`, `interior-trip-834.png`, `interior-trip-390.png`, `interior-trip-360.png`.
- `interior-modal-390.png`, `operator-profile-360.png`.

Đã xem trực tiếp Home desktop, Search mobile, Trip mobile, promotion mobile và modal mobile. Full-page screenshot có thể ghi sticky selection summary ở vị trí viewport hiện tại; không suy đó là vị trí tĩnh trong flow. Toàn bộ screenshot dùng dữ liệu synthetic, logo/ảnh static thật trong workspace.

## 6. Giới hạn và bước tiếp theo

- Hai ảnh limousine chưa dùng vì contract public hiện chưa xác định cabin/VIP; đây là fallback có chủ đích, không phải thiếu file.
- promo-featured chưa công khai vì lời hứa 20%; thẻ thứ ba đang là nội dung trung tính. promo-operators có nội dung hình nói ưu đãi chung, caption/đích thực là khám phá nhà xe; có thể chuẩn bị lại ảnh riêng cho nhà xe để nội dung đồng nhất hơn.
- Không xác minh DB/live campaigns/profile logo đang có, không thao tác dữ liệu thật. Fixture không chứng minh chuyến/ưu đãi ngoài môi trường test tồn tại.
- Chưa kiểm thử thiết bị cảm ứng thật/Safari/Firefox hoặc screen reader chuyên dụng.
- Khi review: xem Home/Search/Trip/profile; duyệt ảnh banner mới không có mức giảm cố định; bổ sung contract cấu hình xe riêng trong task tiếp theo nếu muốn bật cabin/VIP. Mọi cập nhật live logoUrl/campaign là thao tác riêng cần người dùng cho phép.


## Cập nhật review sản phẩm 06/11/2026

Theo yêu cầu refinement mới, đã cho phép ảnh ghế limousine minh họa bố trí 1+1 cho đúng loại `Limousine 22 chỗ`; chưa xác nhận tiện nghi VIP. Kết luận trước đó về việc chưa bật ảnh ghế limousine là trạng thái lịch sử, được thay bằng [báo cáo product refinement](m30-3-product-refinement.md). Ảnh cabin vẫn chưa dùng. Yêu cầu Home 3 card ảnh vẫn chờ sửa banner promo-featured; chưa đánh dấu hoàn tất.
