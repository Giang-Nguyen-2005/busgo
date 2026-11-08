# M30.3 — Final Product Review & Refinement

Ngày 06/11/2026 (Việt Nam). Repository `C:\Users\Giang\busgo`, branch `main`, HEAD `ac5297fe5de6bf87792fd1e179f8867df4d491e9`. Tiếp tục trên toàn bộ thay đổi chưa commit; không reset/stash. Không đọc/sửa/xóa `tatus --short`.

## Kết quả sản phẩm

Đã bật `interior-limousine-seat-vip.webp` cho **đúng tên loại xe “Limousine 22 chỗ”** dưới nhãn **“Ảnh minh họa loại xe”**. Giữ ảnh giường nằm phổ thông. Không gắn ảnh cabin vào xe ghế ngồi.

**Yêu cầu 3 card ảnh Home còn chờ banner thứ ba được sửa.** Lượt này không thay đổi banner Home: vẫn 2 ảnh + 1 card chữ tạm từ lượt trước, bố cục 3 cột desktop và cuộn ngang mobile. Đây chưa phải kết quả đạt yêu cầu 3 card hình ảnh. Không tự công khai `promo-featured`, không nhân đôi ảnh hoặc lấy ảnh điểm đến thay cho banner thứ ba. Không tạo campaign/voucher.

## 1. Đối chiếu nội thất

Đã đọc `DemoDataSeeder.seedBusTypes()` và `limousineSeats()` (chỉ đọc):

- Loại `Limousine 22 chỗ`: mô tả **“Ghế limousine 1+1, một tầng”**.
- Template có 11 hàng, mỗi hàng cột 1 và 3, tất cả tầng 1: 22 ghế với lối đi giữa.
- `seatType=STANDARD` là enum kỹ thuật, không đủ để kết luận loại trang bị; không dùng enum này để nhận diện VIP.

Đã mở trực tiếp ảnh WebP: thấy ghế ngồi độc lập ở hai bên lối đi giữa, cùng một tầng, không phải giường hay khoang ngủ. Ở **mức bố trí cơ bản** ảnh phù hợp để minh họa loại 1+1 theo yêu cầu mới. Ảnh không chứng minh đủ 22 ghế, từng hàng cuối, kích thước xe, vật liệu ghế, chức năng massage/ngả điện, cổng sạc hoặc tiện nghi khác. Tên file “vip” không được đưa thành tên loại xe hay nhãn chất lượng trên giao diện.

Mapping chính xác theo tên busType từ API, chuẩn hóa NFC/khoảng trắng/case; không dùng database ID. Không mở rộng cho tên `Limousine`, `Limousine ghế VIP`, `Limousine 22 chỗ VIP` hoặc tên gần giống chưa đối chiếu.

| Ảnh | Trạng thái sau refinement |
| --- | --- |
| `interior-sleeper-standard.webp` | Giữ cho `Giường nằm 34 chỗ` |
| `interior-limousine-seat-vip.webp` | Bật cho `Limousine 22 chỗ`, minh họa bố trí cơ bản |
| `interior-limousine-cabin.webp` | **Chưa dùng**: chưa xác định loại xe giường phòng tương ứng trong contract/catalogue đang đối chiếu |

Component `VehicleImage` dùng chung nên ảnh mới xuất hiện trên Home upcoming trips, Search, public operator profile và Trip Detail. Nhãn “Ảnh minh họa loại xe” luôn hiện cạnh ảnh. Detail/modal ghi rõ ảnh không xác nhận tiện nghi VIP/trang bị của từng xe. Giữ modal focus/Tab/Escape/Đóng/focus return, error fallback và reset theo loại xe. Không sửa production SeatMap, backend, template hoặc dữ liệu live.

## 2. Review ba banner và nội dung sửa chính xác

| Banner | Kết luận | Điều hướng hiện tại |
| --- | --- | --- |
| `promo-booking.webp` | Nội dung khám phá ưu đãi chung, không có mức giảm cố định; giữ | `#public-promotions` — M30.4B hiện có |
| `promo-operators.webp` | Nội dung hình “Ưu đãi nổi bật”, không có mức giảm cố định; giữ. Caption dẫn khám phá nhà xe, nội dung hình chưa đồng nhất hoàn toàn với đích | `#public-operators` — discovery nhà xe công khai |
| `promo-featured.webp` | Có “Giảm đến 20%”; chưa có campaign được đối chiếu nên **không công khai** | Chưa gắn ảnh; slot thứ ba vẫn dẫn `#journey-search` |

Đề xuất sửa **chính ảnh promo-featured**, không thêm chữ che bên ngoài để giấu lời hứa cũ:

| Vị trí trên ảnh | Nội dung cũ | Nội dung thay thế đề xuất |
| --- | --- | --- |
| Tiêu đề lớn bên trái | “Ưu đãi đặt vé hấp dẫn” | **“Chọn chuyến phù hợp”** |
| Dòng phụ | “Đặt vé nhanh chóng, giá tốt cho nhiều hành trình” | **“Chọn điểm đón, điểm trả và ngày đi”** |
| Thẻ vé bên phải | “Giảm đến 20%” | **“Khám phá hành trình”**; xóa hoàn toàn số 20, dấu % và cụm “Giảm đến” |
| Nút trong ảnh | “Đặt vé ngay” | **“Tìm chuyến”** |

Giữ xe/cảnh quan/logo BusGo và màu hiện có; không thêm mã, giá, hạn dùng, tỷ lệ giảm hoặc cam kết giá tốt. Giữ tỷ lệ 1:1, xuất 1200×1200 WebP; chữ/logo/CTA cách mép ít nhất khoảng 60px để đọc tốt trên mobile. Có thể lưu bản sửa thành `promo-featured-reviewed.webp` để giữ nguyên nguồn cũ.

Sau khi có bản sửa được duyệt: slot thứ ba dùng ảnh mới, caption “Lên kế hoạch hành trình”, link `#journey-search` đang hoạt động. Không cần API/voucher mới. Đây là **đặc tả sửa ảnh**, chưa phải file ảnh đã được tạo hay tích hợp trong lượt này. M30.4B vẫn là nguồn duy nhất của campaign/điều kiện thật.

## 3. File thay đổi trong lượt refinement

- `frontend/src/features/customer/visualAssets.ts`: thêm mapping limousine 22 chỗ; giữ mapping sleeper và fallback các loại khác.
- `frontend/src/features/customer/CustomerMedia.tsx`: bổ sung giới hạn minh họa bố trí/trang bị ở detail và modal.
- `frontend/tests/m30-3-final.test.mjs`: kiểm tra mapping limousine đúng tên/ID tùy ý, không cabin, không suy tên gần giống, nhãn không xác nhận VIP.
- `frontend/tests/m30-3-final-browser.cjs`: hỗ trợ BUSGO_MEDIA_TYPE/BUSGO_MEDIA_OUT; fixture limousine khớp catalogue 11 hàng × 2 ghế, một tầng; kiểm tra đúng src và chuyển type/fallback. Đây chỉ là fixture test, không sửa thuật toán hoặc dữ liệu ghế production.
- Báo cáo này và ghi chú cập nhật trong báo cáo final assets trước đó.

Không sửa ảnh nguồn, Home promotional component, PublicPromotions M30.4B, hero, CSS/layout, dependencies, backend/DB/Flyway.

## 4. Review và kiểm chứng

- Frontend production build: **PASS**. `npm --prefix frontend run build`; warning Zod annotation và Notifications chunk có sẵn.
- Frontend tests: **191/191 PASS**, 0 fail/skip.
- Edge media acceptance: chạy React/giao diện ứng dụng thật + ảnh WebP trong workspace, API bị intercept hoàn toàn bằng fixture. **1440/1100/834/390/360px**, Home/Search/Trip Detail/profile, modal bàn phím, giữ ghế khi xem ảnh, không POST hold/booking khi mở ảnh, đổi type/reset/fallback, ảnh static không 404, không document overflow.
- M30.4B public-only regression: **5 nhóm PASS**, zero/many promotions, CTA/context, empty/hidden/expired/error states, login/seat persistence và checkout không tự áp voucher.
- Backend local không có listener ở 8080/8100 khi kiểm tra; **không gọi đây là nghiệm thu live FE→BE→DB**. Không tự khởi động backend/seeder/database. Không thay dữ liệu live.
- `git diff --check`: PASS, chỉ có cảnh báo chuyển LF/CRLF HomePage đã có. Không commit/push/merge.

Evidence mới ở `C:\Users\Giang\busgo\.tools\m30-3-refinement\`:

- `frontend-build.log`, `frontend-tests.log`, `browser.log`, `promotions-regression.log`.
- `browser/acceptance.json`: kết quả media, viewport và lỗi console/page/API fixture.
- `promotions-acceptance.json`: snapshot kết quả M30.4B lượt này.
- `browser/assets-home-{1440,1100,834,390,360}.png`.
- `browser/promotions-{1440,1100,834,390,360}.png`.
- `browser/interior-search-{1440,1100,834,390,360}.png`.
- `browser/interior-trip-{1440,1100,834,390,360}.png`.
- `browser/interior-modal-390.png`, `browser/operator-profile-360.png`.

Chụp mới vì ảnh limousine và nội dung modal đã đổi. Review crop/spacing/CTA ở mobile và desktop; ảnh giữ tỷ lệ, thumbnail không che giờ/giá/CTA, modal xem đầy đủ bằng contain. Full-page screenshot có thể ghi sticky summary theo vị trí viewport lúc chụp. Chưa kiểm thử thiết bị cảm ứng thật, Safari/Firefox hoặc screen reader chuyên dụng.

Tái chạy media bằng PowerShell, từ repo, sau khi chạy Vite cổng 5191:

```powershell
$env:BUSGO_TEST_ORIGIN='http://127.0.0.1:5191'
$env:BUSGO_MEDIA_TYPE='Limousine 22 chỗ'
$env:BUSGO_MEDIA_OUT='.tools/m30-3-refinement/browser'
node frontend/tests/m30-3-final-browser.cjs --media-only
node frontend/tests/m30-4b-browser.cjs --public-only
```

## 5. Chờ review

Nội thất limousine đã được bật theo phạm vi minh họa người dùng cho phép. Ảnh cabin vẫn chưa dùng. Yêu cầu Home **3 card ảnh** cần bản banner thứ ba đã xóa/sửa các lời hứa theo mục 2; chưa đánh dấu phần này hoàn tất. Vite test được dừng sau kiểm chứng. Không thay đổi database hoặc dữ liệu live.
