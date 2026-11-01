# M30.2A — Customer journey & navigation

Ngày: **31/10/2026, giờ Việt Nam**. Trạng thái: đã triển khai frontend, chờ người dùng review; không commit/stage/push/merge.

## 1. Phát hiện trước khi sửa

Đã đối chiếu `m30-product-experience-audit.md` (roadmap M30.2A/M30.2B), `m30-1-visual-assets-audit.md`, `m29-customer-experience.md`, `m29-final-hardening.md` và source hiện tại.

- HomeDiscovery gán ngày hôm nay khi mở tuyến. Tuyến hoạt động không bảo đảm có chuyến vào ngày đó; không thể suy ngày có chuyến từ catalogue.
- SearchForm mặc định hôm nay, nên chỉ bỏ ngày ở URL discovery chưa đủ. Route DTO chỉ có tên tuyến và ID đầu/cuối, không có nhãn riêng hai điểm.
- Hồ sơ công khai chỉ liệt kê tuyến, chưa dẫn đến form tìm chuyến; đường quay lại chỉ về Home. TripCard search chưa truyền context sang hồ sơ.
- Chi tiết chuyến có tên nhà xe nhưng chưa hiển thị Rating hoặc link thông tin/đánh giá. Lựa chọn ghế local mất khi rời trang xem hồ sơ.
- Guest đã có selection draft và returnTo qua login/register. Khôi phục đã yêu cầu fresh seat response và lọc ghế unavailable; giữ chỗ đã chỉ được lưu trong `onSuccess` của `/seat-holds`. Không xác nhận lỗi inventory/transaction để sửa.
- Câu “Chỉ hủy toàn bộ đặt vé” mâu thuẫn section hủy một phần. Booking detail có đủ chức năng nhưng thiếu điều hướng nhanh đến điều kiện và hỗ trợ.

## 2. File chỉnh sửa

Production frontend:

1. `frontend/src/features/customer/presentation.ts`: helper routeSearch không gán ngày; profileReturn giới hạn về Search/Trip.
2. `frontend/src/features/marketplace/Marketplace.tsx`: route CTA Home/profile và đường quay về theo context.
3. `frontend/src/features/search/SearchForm.tsx`: ngày trống khi vào từ route discovery; giữ ID, nhãn; lấy tên khớp ID từ autocomplete công khai hiện có khi có thể.
4. `frontend/src/pages/SearchPage.tsx`: hướng dẫn chọn ngày, hiển thị tên tuyến trong trạng thái chưa tìm kiếm.
5. `frontend/src/components/ui.tsx`: link hồ sơ trên TripCard giữ URL search đầy đủ.
6. `frontend/src/pages/TripPage.tsx`: Rating, link hồ sơ có returnTo, lưu draft khi xem hồ sơ, làm rõ chọn ghế/hold/login.
7. `frontend/src/pages/BookingDetailPage.tsx`: mục lục điều kiện đổi/hủy và hỗ trợ theo booking, các anchor đến section hiện có.
8. `frontend/src/features/booking/CancellationSection.tsx`: tiêu đề/copy rõ phạm vi hủy toàn bộ cho CUSTOMER; link điều kiện hủy một phần.
9. `frontend/src/features/customer/customer.css`: khoảng cuộn anchor và khoảng cách link, scoped customer shell.

Test / tài liệu:

- `frontend/tests/cancellation.test.mjs`: bổ sung assertion copy và link hủy một phần.
- `frontend/tests/m30-2a.test.mjs`: route/date/operator context, filter/sort/page và giới hạn đường quay lại.
- `frontend/tests/m30-2a-browser.cjs`: mở rộng regression M29 bằng fixture M30.2A, giữ nguyên assertion M29; tùy chọn `--m30-only` để chẩn đoán.
- `docs/m30-2a-customer-journey.md`: báo cáo này.

## 3. Thay đổi theo màn hình

### Home → Search

Thẻ tuyến ghi “Chọn ngày tìm chuyến”, truyền ID điểm đón/trả và tên tuyến qua URL; không truyền ngày. Search dùng lại SearchForm, để ngày trống và không gọi search API trước khi có ngày hợp lệ. Ngày được chọn mới gửi qua journeyParams; filter/sort vẫn được giữ, page reset theo cơ chế cũ. Trang Home tìm tự do giữ mặc định ngày hiện có của M29.

Không đoán lịch chạy từ trạng thái ACTIVE. Thông báo nêu rõ chuyến/chỗ phụ thuộc ngày. Nhãn hai điểm được lấy theo đúng ID từ `/locations?q=` nếu có trong kết quả; khi không có, hiện “Điểm đón/trả #ID” cùng tên tuyến thay vì tự tách tên tuyến thành tên bến. Autocomplete public hiện giới hạn 20 kết quả, vì vậy không cam kết luôn phân giải được mọi ID. Không gọi danh mục nội bộ hoặc thêm API.

### Hồ sơ nhà xe / chi tiết chuyến

Tuyến trong hồ sơ có CTA chọn ngày và operatorId để tìm đúng nhà xe. Search card truyền URL search (gồm filter, sort, page) vào returnTo. Trip truyền nguyên URL trip, gồm nested search context. Hồ sơ chỉ nhận đường quay về Search/Trip nội bộ; thiếu hoặc sai context thì về Home.

Trip hiển thị Rating từ payload hiện có, thiếu dữ liệu dùng thông báo “Chưa có đánh giá”. Link “Thông tin và đánh giá nhà xe” mở profile/reviews public. Trước khi rời Trip, lưu lựa chọn trong selection draft hiện có; khi quay lại, chỉ khôi phục ghế còn available sau response mới. Không tạo review/rating hoặc dữ liệu chuyến.

### Chọn ghế → đăng nhập → giữ chỗ

Copy nói rõ ghế đang chọn chưa được giữ, giá tạm tính, chỉ chuyển sang đặt vé sau xác nhận giữ chỗ thành công. Guest được giải thích cần login/register và sẽ kiểm tra lại chỗ trống khi quay về. Giữ nút “Tiếp tục”, returnTo, TTL/validation draft, polling, giới hạn 5 ghế và mutation hold cũ. Copy khôi phục áp dụng cả sau xem hồ sơ và sau đăng nhập.

### Sau đặt vé

Mục “Quản lý đặt vé” dẫn đến **điều kiện** đổi ghế/chuyến, hủy toàn bộ, hủy một phần và `/support/new?bookingId=…`. Các link mục lục không phải nút thực hiện giao dịch. Những nút thực hiện vẫn nằm trong component hiện có và theo eligibility backend; không tạo action giả khi không đủ điều kiện.

Section hủy toàn bộ giải thích hủy các chỗ còn lại và dẫn đến điều kiện hủy một phần. Phần hủy một phần giữ hướng dẫn giữ ít nhất một hành khách và quy trình quote/confirm hiện có. Không đổi quy tắc hoàn tiền/cutoff/giá/status.

## 4. Kiểm thử và kết quả

Evidence local ignored: `.tools/m30-2a/`. Tất cả browser API được intercept bằng fixture; không sử dụng database thật. Các tên, sao, giá, review, tài khoản và token trong harness là dữ liệu test, không phải dữ liệu sản phẩm.

| Kiểm tra | Kết quả | Evidence |
|---|---|---|
| TypeScript + Vite production build | **PASS**, 2.220 modules | `frontend-build.log` |
| Toàn bộ frontend unit/regression | **166 pass, 0 fail, 0 skip** | `frontend-tests.log` |
| Browser M29 + M30.2A với fixture | **29 nhóm checks pass**, 55 captures, 0 page errors, 0 console errors, 0 request thiếu fixture | `acceptance.json`, `browser-final.log` |
| Layout | **1440/834/390/360**, không document overflow ở 55 captures | PNG cùng thư mục evidence |
| Whitespace | **PASS** | `git diff --check` |

Lượt build sandbox đầu tiên thất bại vì Windows `EPERM realpath`, Edge sandbox đầu tiên không khởi động được. Chạy lại ngoài sandbox đã pass. Build còn cảnh báo Zod annotation và Notifications static/dynamic import có sẵn. Các lượt chẩn đoán harness từng fail vì assertion chạy trước lazy route render, locator login không unique và tọa độ scroll phân số dưới 1px; đã sửa chờ URL + màn hình, scope locator vào form và dung sai hình học 1px, giữ hit-testing. Không bỏ assertion nghiệp vụ. Kết quả cuối là lượt full chạy lại sau sửa harness.

| Luồng đã test | Kết quả / loại bằng chứng |
|---|---|
| Guest Home → tuyến → chọn ngày → Search | PASS fixture; ngày ban đầu trống; submit thiếu ngày bị chặn; chưa có ngày không gọi search; ID/tên tuyến/nhãn điểm được giữ |
| Filter/sort/page và back-forward | PASS fixture regression M29; bổ sung back-forward sau discovery, URL giữ ngày/hành trình/sort |
| Search → profile → kết quả | PASS fixture; URL search giữ nguyên |
| Trip → profile → Trip | PASS fixture; nguyên trip URL/nested search, draft phục hồi sau revalidation; profile route CTA giữ operatorId và chưa có ngày |
| Chọn ghế → login → đúng chuyến | PASS fixture M29 với ghế còn trống; M30 kiểm tra ghế vừa unavailable không được phục hồi |
| Login ↔ Register | PASS fixture cho liên kết và returnTo chính xác; không submit đăng ký mới |
| Không hold tự động | PASS fixture; chọn ghế, xem profile, login return không POST hold. Khi hold lỗi hoặc đang chờ response, không ghi hold/không mở checkout; chỉ response thành công mới chuyển checkout |
| Booking detail đổi/hủy/hỗ trợ | PASS fixture; heading hủy toàn bộ, section một phần, anchor và bookingId hỗ trợ đúng; fixture không đủ điều kiện ẩn nút mutation |
| Mobile sticky và overflow | PASS fixture; nút có hit target, không bị che ở ma trận viewport; guest thêm hướng dẫn vẫn hiển thị CTA. Đã xem trực tiếp ảnh guest 360, profile 390, booking detail 360 và seat 360 |
| Luồng M29 khác | PASS fixture: keyboard autocomplete/swap, filter drawer/focus/Escape, empty/error/retry/loading, 2 tầng/giới hạn 5, conflict/polling, expired hold, voucher quote, booking/payment recovery, QR valid/VOID/missing, Vé của tôi và reduced motion |

Các HTTP 409/lỗi cố ý trong fixture được tính riêng (`expectedHttpErrors: 12`), không phải lỗi runtime không xử lý. Browser không gọi backend thật.

Tái chạy từ repo root (Vite đang phục vụ `127.0.0.1:5190`):

```powershell
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend test
node frontend/tests/m30-2a-browser.cjs
```

`BUSGO_TEST_ORIGIN` và `PLAYWRIGHT_MODULE` theo harness M29. Mặc định Playwright dùng runtime có sẵn trên máy; browser dùng Edge headless. Fixture ngày chuyến là 15/01/2027; cần cập nhật ngày test khi đã qua thời điểm này. Các PNG mới gồm `m30-guest-seats-{width}.png`, `m30-profile-{width}.png`, `m30-seats-{width}.png`, `m30-booking-{width}.png`; 39 captures còn lại là regression M29. Evidence ignored cần giữ local để review.

## 5. Chưa được kiểm tra/nghiệm thu

- Không chạy backend thật, không tạo booking/hold/review/support case trong database, không chạy lại backend unit/integration. Không tuyên bố E2E thật.
- Register được kiểm tra bảo toàn returnTo khi chuyển login ↔ register; chưa submit đăng ký tài khoản mới trong browser lượt này. Login dùng response fixture.
- Không chạy mutation đổi ghế/chuyến, hủy toàn bộ/một phần hoặc gửi hỗ trợ bằng backend thật. Browser kiểm tra điều hướng/eligibility; suite frontend hiện có kiểm tra presentation và guards.
- Chưa nghiệm thu Safari/iOS/Firefox, thiết bị cảm ứng thật, screen reader, tải/concurrency hoặc mọi race phiên đăng nhập. Edge headless không thay nghiệm thu thiết bị thật.
- Tên location ngoài 20 kết quả public autocomplete cần khách chọn lại từ danh sách nếu muốn nhãn cụ thể; IDs/tên tuyến vẫn được giữ.
- Người dùng chưa duyệt giao diện và câu chữ cuối.

## 6. Ảnh hưởng đến nghiệp vụ cũ và Git

Hoàn thành 5/5 nhóm phạm vi triển khai (4 nhóm hành trình + giữ thiết kế M29). Dùng tokens/components/layout cũ; không thêm ảnh, banner, dependency hoặc trang bắt buộc. Không sửa dashboard OPERATOR/SYSTEM_ADMIN. CancellationSection dùng chung vẫn giữ heading/copy/action OPERATOR cũ.

Không sửa backend/API/DB/seeder/migration, inventory, hold algorithm, transaction, giá/voucher, refund/cutoff, M22 hoặc M27B. Chỉ thêm lần đọc autocomplete public khi route thiếu nhãn và lưu lựa chọn local trước khi mở hồ sơ. Server vẫn quyết định availability, eligibility và xác nhận giao dịch.

Baseline `main`, không có tracked changes trước khi làm; ba file untracked có sẵn: hai báo cáo M30.1/M30.1B và `tatus --short`. Không tìm thấy AGENTS.md áp dụng trong repo/các thư mục cha. Không đổi branch, identity, remote, stage, commit, push, merge. Hai báo cáo audit và `tatus --short` được giữ nguyên; file `tatus --short` vẫn 2.945 byte, thời gian sửa 12/10/2026 16:09:36. Mọi evidence/build output nằm trong đường dẫn ignored.

## 7. Việc còn lại M30.2B

1. Mở rộng public read DTO có whitelist: mô tả loại xe/seatCount, địa chỉ/hướng dẫn điểm đón trả được phép công khai; không xuất nguyên entity hoặc địa chỉ pháp nhân.
2. Chuẩn bị/duyệt mô tả 3 loại xe và 6 hướng dẫn địa điểm demo, ghi rõ mức xác thực, không biến địa chỉ khu vực thành cam kết điểm đón.
3. Cung cấp policy summary trước mua, đối chiếu rules đổi/hủy hiện hành; eligibility/cutoff theo hành trình, boarding và suspension vẫn do server quyết định.
4. Hiển thị thông tin trên Trip/Booking bằng khối thu gọn dùng M29; dữ liệu thiếu phải được giải thích rõ, không đoán tiện nghi/địa chỉ.
5. Test projection/privacy, segment cụ thể và policy parity; nghiệm thu backend thật cùng desktop/mobile cho luồng thông tin trước hold.

Không đưa passenger-per-seat (M30.2C), voucher discovery, gallery/ảnh hoặc quản trị metadata vào phạm vi M30.2A/B nếu chưa được chốt riêng theo roadmap.

## 8. Git status cuối

Branch: `main`. **10 tracked files modified, 6 untracked**; trong đó 3 untracked đã tồn tại trước, 3 file mới thuộc task. Không có staged changes.

```text
 M frontend/src/components/ui.tsx
 M frontend/src/features/booking/CancellationSection.tsx
 M frontend/src/features/customer/customer.css
 M frontend/src/features/customer/presentation.ts
 M frontend/src/features/marketplace/Marketplace.tsx
 M frontend/src/features/search/SearchForm.tsx
 M frontend/src/pages/BookingDetailPage.tsx
 M frontend/src/pages/SearchPage.tsx
 M frontend/src/pages/TripPage.tsx
 M frontend/tests/cancellation.test.mjs
?? docs/m30-1-visual-assets-audit.md
?? docs/m30-2a-customer-journey.md
?? docs/m30-product-experience-audit.md
?? frontend/tests/m30-2a-browser.cjs
?? frontend/tests/m30-2a.test.mjs
?? "tatus --short"
```

Dừng để người dùng review; chưa triển khai M30.2B.
