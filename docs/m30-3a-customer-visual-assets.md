# M30.3A — Customer Visual Assets & Content

Ngày: 04/11/2026 (Việt Nam). Phạm vi frontend/static, bàn giao để review; chưa hoàn thành gói nội dung/ảnh mới vì chưa được cung cấp nguồn và quyền sử dụng.

## Đầu vào và chức năng đã có

- Đã đọc README, M30 product experience audit (roadmap M30.3A/B), M30.1 visual audit và đối chiếu frontend, public DTO/repository, location API, catalogue seeder chỉ đọc.
- Branch `main`, HEAD `ac5297f` — `feat(dev): add one-command local launcher`. Đầu vào không có thay đổi tracked; duy nhất untracked `tatus --short`, không đọc/sửa/xóa file này. Không chuyển branch, commit, push hay merge.
- Home đã có search, discovery nhà xe/tuyến/chuyến/review từ API, loading/error/empty, guide và public promotions M30.4B.
- M30.2A đã có route CTA chọn ngày, profile/back, khôi phục ghế sau đăng nhập và giải thích chọn ghế chưa giữ chỗ; M30.2B có thông tin trước mua. Không làm lại các luồng này.
- Public operator có `logoUrl`, `description`, rating/review count. Hồ sơ trước đây đã hiển thị logo/mô tả nhưng lỗi ảnh chỉ ẩn ảnh; Home dùng icon. Admin nhà xe đã có ProfileForm cập nhật logo/mô tả; không cần API mới.

## Thay đổi

- `OperatorLogo` dùng chung Home/profile: nhận logo API, kiểm tra HTTPS hoặc local path, chặn protocol-relative/backslash/credentials/URL sai; `object-fit: contain`, khung 72×72. Logo thiếu/lỗi dùng biểu tượng xe trung tính, không dùng logo thương hiệu khác. Thay URL sẽ reset trạng thái lỗi.
- Hiển thị mô tả API trên discovery và profile; trim chuỗi trắng, fallback “Nhà xe chưa cập nhật giới thiệu công khai.”; React escape nội dung.
- Manifest đủ sáu địa danh trong catalogue demo. Chỉ render các thẻ tuyến do API discovery trả; lấy `dropoffLocationId` đối chiếu **ID từ API locations → tên chính xác trong manifest**. Không suy ảnh từ tên tuyến hay thứ tự seeder, không suy địa danh từ province. Catalogue mới/khác tên/thiếu location/API lỗi dùng MapPin.
- `/locations` public hiện giới hạn 20 kết quả; sáu địa danh demo nằm trong phạm vi đó. Với catalogue lớn hơn, địa điểm ngoài response dùng icon; cần chiến lược lookup/contract riêng khi mở rộng. Không coi manifest là danh sách điểm đến được phép tạo route.
- Manifest `imageUrl: null` cho toàn bộ ảnh chưa được cấp. Không request file ảnh chưa tồn tại. Khi có ảnh đúng nguồn, điền URL local tương ứng trong manifest; thông tin filename là kế hoạch, không phải bằng chứng đã có asset.
- Hero dùng cấu hình `heroAsset`: ảnh thay thế, URL fallback JPG hiện có, crop desktop/mobile riêng. Khi cả ảnh chính và dự phòng lỗi, giữ nền hero M29; ảnh decorative không che copy/CTA. Không đổi search form hoặc bố cục/tokens M29.
- Component ảnh có kích thước cố định, lazy loading cho logo/điểm đến, ưu tiên tải hero; lỗi ảnh chính → ảnh dự phòng → nền/icon, không lặp vô hạn.
- FAQ sáu câu: tìm chuyến, giữ ghế, thông tin đặt vé, thanh toán mô phỏng, quản lý/đổi/hủy vé và hỗ trợ gắn booking. Không thêm hotline, tiện nghi, review/rating, voucher hoặc cam kết giả.

## File sửa/tạo

Sửa:

- `frontend/src/pages/HomePage.tsx`
- `frontend/src/features/marketplace/Marketplace.tsx`
- `frontend/src/features/marketplace/marketplace.css`

Tạo:

- `frontend/src/features/customer/visualAssets.ts` — manifest/config/URL validation/mô tả fallback.
- `frontend/src/features/customer/CustomerMedia.tsx` — component logo/điểm đến/hero và fallback.
- `frontend/src/features/customer/CustomerFaq.tsx`
- `frontend/tests/m30-3a.test.mjs`
- `frontend/tests/m30-3a-browser.cjs`
- `docs/m30-3a-customer-visual-assets.md`

Không sửa backend, MySQL, Flyway, seeder, API, auth/role guards hoặc dependency/package lock.

## Build và kiểm chứng

- `cd frontend; npm run build`: PASS (`tsc --noEmit` + Vite). Sandbox ban đầu gặp `EPERM realpath`; chạy lại ngoài sandbox thành công. Có warning Rollup từ annotation Zod và Notifications static/dynamic import; không thêm dependency.
- `cd frontend; npm test`: **187/187 PASS**, gồm bốn test M30.3A: mapping sáu địa danh với ID tùy ý, URL logo/fallback, thiếu ảnh/ảnh được cấp, mô tả trắng.
- Browser M30.3A: Edge headless, React thật + API/image fixture tổng hợp; **không phải kiểm chứng backend/live data**. Ba viewport 1440×1000, 834×1000, 390×1000: Home/profile không horizontal overflow; search button actionable, FAQ, logo load/lỗi, mô tả trắng, chỉ thẻ tuyến API, icon khi thiếu ảnh. Harness dùng production components kiểm tra ảnh lỗi → fallback cuối, đổi URL lỗi→đúng→lỗi và ảnh dự phòng tải được.
- Chạy từ repo: `$env:BUSGO_TEST_ORIGIN='http://127.0.0.1:5191'; node frontend/tests/m30-3a-browser.cjs` với Vite local. Sandbox không khởi động được Edge; chạy ngoài sandbox. Evidence local ignored: `.tools/m30-3a/report.json`, `home-1440.png`, `home-834.png`, `home-390.png`, `frontend-tests.log`.
- Regression M30.2A browser: **29 nhóm kiểm tra PASS**, gồm M29 CUSTOMER và M30.2A (1440/834/390/360px), search/filter/pagination/back, seat/draft/login, hold/conflict/expiry, checkout/voucher quote, mock payment/tickets, booking actions và discovery error/empty. Không page error, console error hoặc API request ngoài fixture. Evidence: `.tools/m30-2a/acceptance.json`. Lệnh: `node frontend/tests/m30-2a-browser.cjs` với cùng BUSGO_TEST_ORIGIN.
- Không chạy backend tests, DB integration hoặc giao dịch live. Các kiểm tra fixture không xác nhận dữ liệu/ảnh của nhà xe ngoài đời, email hay giao dịch thật.

## Asset còn thiếu và cách tiếp nhận

Chưa tải/tạo ảnh mới, chưa giả lập logo doanh nghiệp. Đặt ảnh được duyệt dưới `frontend/public/images/busgo/`; ghi nguồn, chủ sở hữu, quyền dùng/chỉnh sửa và tên nhà xe/địa danh đối chiếu trước khi bật URL. Bảng này là yêu cầu tiếp nhận, tất cả đang **chờ cung cấp**:

| Tên file đề xuất | Mục đích | Kích thước | Định dạng |
| --- | --- | --- | --- |
| `hero-coach.webp` | Hero xe/cung đường, không nhúng chữ, crop mobile an toàn | 1920×960 | WebP |
| `destination-ho-chi-minh.webp` | TP. Hồ Chí Minh | 1200×900 | WebP |
| `destination-da-lat.webp` | Đà Lạt | 1200×900 | WebP |
| `destination-buon-ma-thuot.webp` | Buôn Ma Thuột | 1200×900 | WebP |
| `destination-nha-trang.webp` | Nha Trang | 1200×900 | WebP |
| `destination-da-nang.webp` | Đà Nẵng | 1200×900 | WebP |
| `destination-hue.webp` | Huế | 1200×900 | WebP |
| `operator-an-phu-express-logo.webp` | Đúng nhà xe An Phú Express trong môi trường được xác nhận | 256×256, có khoảng an toàn | WebP/PNG trong suốt |
| `operator-minh-thanh-limousine-logo.webp` | Đúng nhà xe Minh Thành Limousine đã xác nhận | 256×256 | WebP/PNG trong suốt |
| `operator-tay-nguyen-travel-logo.webp` | Đúng nhà xe Tây Nguyên Travel đã xác nhận | 256×256 | WebP/PNG trong suốt |

Tên nhà xe lấy từ catalogue demo, không xác nhận doanh nghiệp thật hoặc quyền thương hiệu. Không tự tạo bộ nhận diện theo những tên đó. Khi có logo, cập nhật `logoUrl` qua ProfileForm của đúng tenant trong môi trường được cho phép; không gán logo theo ID cố định. Ba mô tả nhà xe có nguồn xác nhận cũng còn thiếu; hiện chỉ dùng dữ liệu API nếu có. Không lấy số điện thoại seed làm hotline.

Khi có ảnh địa danh: xác nhận landmark đúng nơi; bật `imageUrl: '/images/busgo/<file>'` của đúng entry, kiểm tra crop và dung lượng. Đây là ảnh địa danh trang trí, không xác nhận địa chỉ bến đón/trả. Hero mới: bật `heroAsset.imageUrl`, điều chỉnh `desktopPosition/mobilePosition` theo ảnh và chạy lại ba viewport. Ảnh mới chưa có nên chưa kiểm chứng crop/chất lượng/tốc độ tải của bộ ảnh tương lai.

## Giới hạn và bước M30.3B

- M30.3A hoàn thiện phần code có thể triển khai; phần 10 asset mới/ba mô tả và nghiệm thu bằng nội dung được duyệt vẫn chờ bổ sung. Ảnh hero JPG cũ giữ nguyên, không tái xác nhận quyền của asset lịch sử.
- M30.3B: đối chiếu ba ảnh xe hiện có, thêm nhãn **minh họa** nhìn thấy nhất quán trên card và chi tiết; gallery gọn mở được trên mobile, reset/fallback khi đổi chuyến. Chỉ tích hợp nội thất đúng cấu hình khi có nguồn/quyền, không nhận ảnh minh họa là xe thật của nhà xe. Giữ selection/hold và CTA, nghiệm thu focus/back/390px và hồi quy booking/voucher. Không mở rộng gallery/API/DB khi chưa có dữ liệu xác thực cần quản lý.


## Cập nhật 06/11/2026

Đã nhận đủ bộ 15 ảnh và tiếp tục tích hợp; phần “còn thiếu/chờ cung cấp” phía trên là trạng thái lịch sử ngày 04/11. Xem [bàn giao final assets + interiors + Home cards](m30-3-final-assets-integration.md) để biết asset đang bật, ảnh tạm giữ và kiểm thử mới. Hero giữ nguyên ảnh đã duyệt.
