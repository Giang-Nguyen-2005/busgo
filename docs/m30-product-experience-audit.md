# BUSGO — M30.1B: Audit toàn diện product experience

Ngày: **31/10/2026, giờ Việt Nam**. Repository: `C:\Users\Giang\busgo`.

## 1. Kết luận điều hành

BusGo hiện là **portfolio/demo có nghiệp vụ đặt vé tương đối sâu**, vượt mức website chỉ có màn hình tìm chuyến và checkout. Source đã có tìm kiếm theo chặng, sơ đồ ghế và hold, đặt vé, thanh toán/hoàn tiền mô phỏng, QR vé, hủy toàn bộ/một phần, đổi ghế/chuyến, voucher nhà xe, đánh giá, vận hành chuyến, thông báo, hỗ trợ/tranh chấp và quản trị nhiều vai trò. M29 đã tạo nền giao diện thống nhất và có bằng chứng kiểm thử đáng kể.

Khoảng cách lớn nhất với trải nghiệm website thương mại thực tế là **độ đầy đủ thông tin trước mua, khám phá ưu đãi, nội dung có nguồn xác thực, khả năng nhà xe quản lý nội dung đó và sự nối tiếp giữa các màn hình**. Thêm vài ảnh không xử lý hết các khoảng cách này. Không cần xây lại hệ thống đặt vé hoặc design system để cải thiện chúng.

**Nhận định tổng thể: 7/10 cho phạm vi portfolio/demo**, theo source và báo cáo milestone; không phải điểm user testing, phép đo chuyển đổi hay chứng nhận production-ready. Mức này phản ánh nền giao dịch tốt hơn mức trưởng thành của nội dung và khả năng khám phá sản phẩm. Chưa xác nhận lỗi giao dịch P0 mới trong audit; vẫn có lỗi nội dung và khoảng trống chức năng được xác nhận từ source.

Năm việc quan trọng nhất:

1. Nối trải nghiệm **Home/Search → thông tin chuyến đầy đủ → chọn ghế**, cung cấp thông tin loại xe, điểm đón/trả, quy tắc đổi/hủy và đường sang hồ sơ/đánh giá trước khi khách quyết định.
2. Khắc phục **nhãn ảnh và độ tin cậy nội dung**; chuẩn bị bộ ảnh cho đúng 3 nhà xe, 6 điểm đến, 3 loại xe hiện có, không gán ảnh minh họa thành xe thật.
3. Thêm **khám phá voucher theo ngữ cảnh**, dùng lại M24B để xác định tính phù hợp và báo giá; không chỉ nhập mã thủ công.
4. Mở **quản lý ảnh xe và nội dung dịch vụ qua giao diện nhà xe**, giữ tenant/role và quyền sở hữu danh mục dùng chung.
5. Nghiệm thu liên tục **mobile, keyboard, trạng thái lỗi/hết hạn và regression** xuyên suốt các luồng cũ, thay vì chỉ kiểm tra trang chủ đẹp.

## 2. Phương pháp, phạm vi và mức bằng chứng

- Audit đọc frontend thực tế, DTO/service/controller backend liên quan, seed catalogue và các tài liệu milestone. Không khởi động ứng dụng, không gọi API/live DB, không seed/migration, không tạo/tìm/tải ảnh, không chạy test/build mới trong lượt này.
- Giữ nguyên [M30.1](m30-1-visual-assets-audit.md) làm tham khảo kỹ thuật ảnh. Bộ 4 file trong báo cáo đó chỉ là một đợt tối thiểu cũ; **không phải giới hạn ảnh của M30.1B hoặc sản phẩm hoàn chỉnh**.
- Đường dẫn dưới đây tính từ gốc repo; component/method là vị trí đối chiếu chính. Các endpoint đề xuất ở phần 7 **chưa tồn tại**, không phải hướng dẫn gọi API hiện tại. Tất cả path API dùng prefix `/api/v1` ở backend.
- Không tham khảo website ngoài hoặc benchmark thương hiệu trong audit này; tiêu chí là các yêu cầu sản phẩm của người dùng và tính nhất quán trong source, tránh nhận xét xu hướng thị trường không có bằng chứng.

### 2.1. Đối chiếu milestone

| Tài liệu đã đối chiếu | Điều được công nhận | Giới hạn cần giữ |
|---|---|---|
| `docs/m29-ui-foundations.md`, `m29-customer-experience.md` | Tokens, shell, drawer/focus, search/filter, seat/checkout/ticket responsive đã có | M29.1–M29.2 chủ yếu fixture browser; không tự suy thành tất cả dữ liệu thật đều đã chạy |
| `docs/m29-operator-dispatch.md` | Dashboard/dispatch thực dùng API cũ, phân biệt booking/payment/boarding, missing inventory không thành chỗ trống | Không đề xuất thay thuật toán điều phối hoặc thêm trang trí dashboard |
| `docs/m29-platform-finance-support.md` | Phạm vi platform/finance/support được nghiệm thu UI bằng fixture | Không suy thành quyết định tranh chấp hoặc thanh toán ngân hàng thật |
| `docs/m29-final-hardening.md` | M29.5 có backend thật: build, 164 frontend tests, 66 backend unit; 522 integration có pass hợp nhất từ full run và affected rerun; 24 live checks | Full integration ban đầu 520 pass/1 failure/1 error, sau sửa 47/47 affected pass; không gọi full run ban đầu hoàn toàn xanh. Không mọi mutation đều được chạy lại live UI. Đây là bằng chứng lịch sử, chưa rerun trong M30.1B |
| `docs/m24b-promotions-vouchers.md` + source M24B | Voucher tài trợ bởi nhà xe, preview, reserve/redeem/release, snapshot giá, tương thích đổi/hủy | Chưa có discovery cho khách, platform subsidy/stacking/payment gateway ngoài phạm vi |
| `docs/m17-cancellation-recovery.md`, `m20-booking-modification.md`, `m21-partial-cancellation.md` + component tương ứng | Hủy/đổi/hoàn mô phỏng đã có; không báo là chưa triển khai | Phần “deferred” ở tài liệu cũ phải đối chiếu milestone mới; ví dụ hủy một phần đã có M21 |
| `docs/m23-notifications.md`, `m24a-marketplace.md`, `m25-live-trip-operations.md` + frontend/backend tương ứng | Inbox/email tùy cấu hình, review sau chuyến, ETA/lifecycle có thật trong source | Không GPS, không mặc định email đã gửi, không review giả hoặc rating từ ảnh |
| `docs/demo-data.md` + `DemoDataSeeder` | Catalogue hư cấu, create-only, ba ngày lịch tương đối khi seeder được chạy | Không phải snapshot database hiện tại; audit không xác nhận voucher/review đang active hoặc số chuyến đang còn chỗ |

### 2.2. Phân loại phát hiện

- **L — Lỗi đã xác nhận từ code:** câu chữ/điều kiện render có mâu thuẫn hoặc mô tả sai nguồn dữ liệu; không đồng nghĩa đã tái hiện trên browser.
- **UI — Thiếu trình bày/khám phá:** backend hoặc dữ liệu đã có nhưng chưa được dùng đủ trên màn hình.
- **DATA — Thiếu mô hình/contract hoặc cần bổ sung nội dung:** phân biệt rõ trường chưa có với trường có nhưng chưa biết giá trị live. Không kết luận DB rỗng từ fallback.
- **FUNC — Thiếu chức năng trải nghiệm:** chưa có endpoint/flow quản lý hay chọn lựa; không gọi là bug của nghiệp vụ đã cam kết.
- **UP — Nâng cấp tùy chọn:** có giá trị khi phục vụ quyết định thật, không mặc định bắt buộc.

## 3. Audit CUSTOMER từ đầu đến cuối

### 3.1. Home và khách chưa đăng nhập

`pages/HomePage.tsx` có hero, SearchForm, HomeDiscovery, hướng dẫn 3 bước và thông báo thanh toán mô phỏng. `HomeDiscovery` trong `features/marketplace/Marketplace.tsx` có 4 section: khám phá nhà xe, tuyến hoạt động, chuyến sắp chạy và review gần đây; mỗi section có loading/error/empty và CTA khi không có dữ liệu. **Không thiếu toàn bộ nội dung trang chủ**.

Guest xem Home/Search/profile/chi tiết/ghế được: `routes/router.tsx` và `common/security/SecurityConfig.java` cho phép các read endpoint công khai tương ứng. Khi bấm tiếp tục chọn ghế, `TripPage.TripSelection` lưu draft rồi yêu cầu login; `AuthPage.LoginForm/RegisterForm` bảo toàn `returnTo`; `selectionDraft.ts` khôi phục chỉ sau khi kiểm tra chỗ trống mới. Đây là luồng đã có, không cần làm guest checkout để “giống thật”. Nên giải thích sớm “đăng nhập để giữ chỗ/đặt vé”, tránh khách hiểu ghế đã được giữ khi mới chọn.

Các thiếu sót cụ thể:

- Nhà xe discovery dùng `BusFront` mặc dù PublicOperator có `logoUrl`; chưa tận dụng nhận diện thương hiệu.
- Tuyến dùng icon/tên, chưa có ảnh điểm đến; link tự chọn **ngày hôm nay**. Seed chủ yếu tạo chuyến ngày mai trở đi nên CTA có thể đến trang không có chuyến. Đây là lệch ngữ cảnh có điều kiện, không phải khẳng định API hỏng. Nên đưa người dùng đến form chọn ngày giữ ID/nhãn tuyến; không tự suy “ngày có chuyến”.
- Chuyến sắp chạy có cửa sổ 30 ngày và tối đa 6 chuyến trong `MarketplaceService.upcoming()`; section tuyến trả tối đa 8 trong `routes()`. Không gọi đây là toàn bộ catalogue hoặc “phổ biến” vì source không tính độ phổ biến.
- Chưa có ưu đãi discovery. Không thể biết có chiến dịch đang hoạt động nếu không đọc môi trường; có endpoint quản lý không có nghĩa Home có dữ liệu công khai được phép dùng.
- Hướng dẫn hiện nói đến tìm/chọn/thanh toán/vé, chưa hướng dẫn ngắn về đổi/hủy, cách tìm hỗ trợ hoặc điều kiện demo; nên dùng FAQ ngắn và link có ngữ cảnh. Không dựng chứng nhận, số khách, hotline hay cam kết “hoàn 100%” thiếu căn cứ.
- `CustomerLayout.tsx` có Hỗ trợ cho CUSTOMER nhưng guest không có đường hỏi trước đặt vé. M28 hỗ trợ hiện gắn booking; không mở hệ thống đó cho guest chỉ để thêm link. Có thể có FAQ công khai/static và nêu rõ hỗ trợ trong tài khoản sau đặt vé.

### 3.2. Search và so sánh lựa chọn

`SearchPage.tsx`, `SearchForm.tsx`, `FilterPanel.tsx`, `components/ui.tsx::TripCard` đã có tên/đánh giá nhà xe, loại xe, điểm đón/trả, giờ lịch/dự kiến khi trễ, thời lượng, giá/chỗ, số chỗ còn và CTA. Bộ lọc gồm nhà xe, loại xe, sao, số chỗ, giá, khoảng giờ; 6 sort, pagination, chip xóa từng lọc, reset, URL/back-forward; desktop sidebar/mobile sheet. Không có chuyến/loading/error đều đã có xử lý cụ thể.

Khoảng trống: ảnh card nhỏ và ẩn ở **≤1100px** (`customer.css:154–164`); chưa có ưu đãi phù hợp; chưa có so sánh cạnh nhau hoặc bảng lựa chọn lưu tạm. So sánh 2–3 chuyến có thể làm frontend sau khi thông tin card đầy đủ, nhưng xếp UP/P2: không ưu tiên hơn thông tin trước mua và voucher. Không đưa giá sau giảm làm giá mặc định nếu chưa biết số ghế, khách và điều kiện mã.

Điểm đón/trả hiện có **tên + giờ + ID**, không có địa chỉ trong `TripSearchDtos.PickupSummary/DropoffSummary/CustomerTripStop`. `Location` có address/tọa độ nhưng contract khách chưa đưa ra. Khách có thể hiểu đúng tuyến mà vẫn chưa biết đến đâu để lên xe. Cần cung cấp địa chỉ/hướng dẫn đã xác nhận ở detail/checkout; chưa cần bản đồ/GPS. Không dùng ảnh landmark thay ảnh bến thật.

### 3.3. Trip detail, ghế và thông tin tạo tin cậy

`TripPage.TripSelection` gộp chi tiết chuyến với chọn ghế: tên nhà xe/loại xe, LiveTripBlock, SeatMap, hành trình/điểm dừng có giờ, tổng tiền tạm tính và CTA. SeatMap giữ đúng floor/row/column và gap, có chú giải, tầng, mã ghế; chọn tối đa 5; polling ghế 15 giây, loại ghế không còn trống, giữ chỗ do server. Không cần tách thành hai page nếu một cụm thông tin thu gọn là đủ.

Khoảng trống được xác nhận:

- `TripDetail extends Trip` và backend detail có `busImageUrl` nhưng TripPage không render ảnh; chưa có gallery, amenity contract hoặc caption/xác thực nguồn ảnh.
- `Trip.operator` có rating tùy chọn nhưng detail chỉ in tên nhà xe, không dùng Rating hoặc link hồ sơ như TripCard. Nên có link “Thông tin và đánh giá nhà xe”; không cần tải tất cả review ngay trên seat page.
- Public `busType` chỉ có id/name; loại xe có mô tả ở danh mục operator nhưng chưa đưa vào detail cho khách. Demo seeder đặt mọi `SeatTemplate.seatType = STANDARD`, dù catalogue có giường nằm và limousine. `SeatMap` đọc nhãn seatType nên mô tả ghế chưa phản ánh hết cấu hình. Đây là **khoảng trống ngữ nghĩa dữ liệu demo**, không được chữa bằng đổi tọa độ hoặc đoán mọi STANDARD là giường nằm.
- Chính sách đổi/hủy đang nằm sau đặt vé trong các section eligibility/recovery. Chưa có bản tóm tắt trước mua. Cần read model giải thích quy tắc server hiện hành; không tự thêm policy nhà xe tùy chỉnh làm khác M17/M20/M21.
- Lịch trình hiện có tên/giờ/allowPickup/allowDropoff, nhưng thiếu địa chỉ và hướng dẫn boarding. Chỉ công bố dữ liệu liên hệ/địa chỉ được chủ sở hữu xác nhận cho mục đích công khai.

### 3.4. Checkout, thanh toán và phục hồi lỗi

`BookingPage.tsx` có đếm ngược hold, thông tin liên hệ, quote voucher, giá gốc/giảm/tổng, nút khóa khi đang kiểm tra, thông báo lỗi và link kiểm tra Vé của tôi trước khi đặt lại. Hết hold quay lại chọn ghế; `booking/recovery.ts` và TripPage có đường về booking đang chờ/đã xác nhận. Thanh toán dùng `/payments/mock-confirm`; `PaymentPage` ghi rõ mô phỏng, QR icon không chuyển tiền. Những nền tảng này cần giữ nguyên.

Thiếu chức năng thực sự: checkout chỉ nhập **contactName/Phone/Email**, không nhập tên riêng từng hành khách. `BookingDtos.CreateBookingRequest` không có passenger list, `BookingService` tạo BookingItem không set tên khách; `PaymentTicketService.passengerName()` dùng contact name khi item thiếu tên. Vì vậy đặt nhiều ghế có thể phát hành nhiều vé cùng tên liên hệ theo thiết kế hiện tại. UI đã nói rõ “chưa cung cấp tên riêng”; **không phải bug mất dữ liệu hành khách đã nhập**. Nếu muốn tên vé đúng từng khách, cần API, validation mapping ghế→hành khách, snapshots và regression, không chỉ thêm ô frontend. Trong roadmap trước mắt giữ mô hình contact-only và làm rõ nghĩa; passenger-per-seat tách subtask tùy chọn có nghiệm thu độc lập.

Thiếu UI/FUNC khác: chưa có selector voucher; khi mã hết lượt/hết hạn sau preview phải nhận lỗi server và cho chọn lại hoặc bỏ mã, không lặng lẽ bỏ giảm giá. Giá chỉ xác nhận bởi quote/booking server; không tính giảm cục bộ. Không thêm payment gateway hoặc dựng QR ngân hàng thật trong M30.

### 3.5. Sau đặt vé

- `MyBookingsPage` có lọc trạng thái, pagination, CTA xem vé/chi tiết; `BookingDetailPage` có hành trình, liên hệ, ghế, tiền, vận hành, hỗ trợ, BookingReviewSection, PartialCancellationSection, ModificationSection và CancellationSection. **Hủy/đổi/hoàn không phải chức năng còn thiếu.**
- `TicketPage` tạo QR bằng `QRCodeSVG(ticket.qrData)`; VOID hoặc thiếu qrData không render mã lên xe. Không cần ảnh QR mẫu, download PDF hoặc gửi mail tự động để đạt đợt M30 đầu.
- `CancellationSection` giải thích hạn, trạng thái và hoàn tiền mô phỏng; `PartialCancellation` có chọn item, quote, xác nhận, giữ ít nhất một khách; `Modification` có eligibility, alternative trips, map ghế cũ→mới, quote/hold/confirm/cancel, resume và lịch sử. Không thay endpoint để “rút ngắn” bằng cách bỏ bước quote/xác nhận.
- **Lỗi câu chữ xác nhận:** CancellationSection hiển thị với khách “Chỉ hủy toàn bộ đặt vé” trong khi cùng booking có section Hủy một phần. Sửa thành “Khu vực này hủy toàn bộ…” và dẫn sang phần hủy một phần khi phù hợp, không đổi quyền hay điều kiện backend.
- Trang booking detail nhiều section liên tiếp; đánh giá nằm trước các thao tác hủy/đổi. Có thể thêm mục lục/action strip và thu gọn lịch sử để khách tìm thao tác nhanh hơn; không tuyên bố layout bị lỗi hoặc có overflow khi chưa chạy browser.
- `Notifications.tsx` có bell, inbox, read/read-all, polling, deep link booking, preferences email và lịch sử gửi/retry. `NotificationItem.open` hiện chờ mark-read thành công mới navigate; lỗi đánh dấu đọc chặn mở booking. Đây là **điểm luồng bị ngắt theo source** nên cải thiện để link nội dung vẫn dùng được, mutation read là best effort; chưa có bằng chứng lỗi đang xảy ra live.
- M28 `Support` có tạo ticket gắn booking, trao đổi, trạng thái/actions từ server và cấp xem xét; support/dispute **không tự hoàn tiền**. `BookingReviewSection` chỉ cho review khi server trả eligibility, không chỉ dựa booking status hoặc tự dựng review từ ảnh.

## 4. Danh sách phát hiện và ưu tiên

P0 = lỗi cản giao dịch/an toàn tiền/quyền đã được chứng minh. P1 = ảnh hưởng đáng kể khả năng quyết định/đặt/quản lý vé. P2 = nâng chất lượng hoặc phạm vi có thể làm sau. Không gán P0 cho mục chỉ thiếu đẹp.

| ID | Loại | Phát hiện có bằng chứng | Mức | Phân ranh / xử lý |
|---|---|---|---|---|
| F01 | L | `ui.TripCard` coi URL khác null là ảnh xe nhà xe; seed gán cùng ảnh minh họa cho nhiều xe; alt “Xe…của…”; nhãn minh họa chỉ có trong alt fallback | P1 | Sửa semantics/nhãn nhìn thấy. Cần phân loại nguồn ảnh rõ; không phải chỉ đổi file |
| F02 | L | Câu “Chỉ hủy toàn bộ…” mâu thuẫn chức năng hủy một phần cùng trang | P1 | Sửa copy/link, giữ eligibility thật |
| F03 | FUNC/UI | M24B có quản lý và quote nhưng không có customer discovery/selector; Home/Search/Trip không biết ưu đãi | P1 | Endpoint đọc riêng và selector; không mở API quản lý |
| F04 | UI/DATA | Chi tiết không dùng ảnh/rating đã có; chưa có gallery/amenities/mô tả loại xe đầy đủ cho khách | P1 | Tận dụng field cũ trước; extension có nguồn dữ liệu sau |
| F05 | DATA/UI | DTO khách thiếu địa chỉ/hướng dẫn điểm đón/trả dù entity Location có address | P1 | Public projection có chọn lọc; cần kiểm tra chất lượng địa chỉ trước khi đưa ra |
| F06 | FUNC | Bus image URL có DB nhưng `FleetDtos` create/update/response và BusForm không hỗ trợ; không có quản trị gallery/amenity | P1 | Mở metadata management đúng tenant; không bắt sửa SQL/source khi đổi ảnh |
| F07 | UI/DATA | Home không dùng logo có sẵn; profile chỉ loại xe/tuyến dạng text; route profile chưa có CTA tìm theo tuyến | P1 | Link có context + logo, mô tả đủ; không cần làm directory lớn mới |
| F08 | UI | Thumbnail ẩn ≤1100px; TripPage chưa có đường xem ảnh bù | P1 | Mobile có nút/khối xem ảnh gọn, không làm khó chọn ghế |
| F09 | DATA | Demo 3 loại xe nhưng seatType mọi template STANDARD; mô tả accessibility chưa phân biệt cấu hình | P2 | Hiển thị busType/context đúng; chỉnh taxonomy/seed là task có đánh giá riêng, không sửa hình học ghế |
| F10 | FUNC | Thiếu tên hành khách từng ghế trong create-booking contract; vé dùng contact fallback | P2 | Quyết định sản phẩm riêng; cần backend nếu triển khai, giữ contact-only trung thực trước |
| F11 | UI | CTA route discovery gán ngày hôm nay, không theo search context hoặc ngày có dịch vụ | P1 | Cho chọn ngày; không giả định catalogue route nghĩa là có trip hôm nay |
| F12 | UI | NotificationItem chỉ navigate sau mark-read thành công | P2 | Tách mở nội dung khỏi xác nhận read, giữ retry; không phải lỗi dữ liệu booking |
| F13 | DATA | Seeder không tạo nội dung logo/public description, voucher hoặc review; fallback chỉ chứng minh code có đường thiếu dữ liệu | P1 | Chuẩn bị nội dung demo qua luồng hợp lệ sau này; không kết luận database đang rỗng hoặc không có campaign active |
| F14 | FUNC/DATA | Chính sách trước mua, public contact/boarding guidance chưa có read model đầy đủ | P1 | Công bố quy tắc hiện hữu và public fields chọn lọc, chưa làm policy engine |
| F15 | UI/UP | Detail booking dài; user phải tự tìm hủy/đổi/hỗ trợ giữa nhiều section | P2 | Nhóm tác vụ/mục lục, giữ confirmations/lịch sử |
| F16 | UP | Chưa có compare basket 2–3 chuyến | P2 | Frontend đủ cho so sánh trường hiện có; chưa bắt buộc, không bịa amenity/giá sau voucher |
| F17 | UI | Hero không onError, logo lỗi bị ẩn, TripCard fallback không có icon cuối và failed không reset theo URL | P2 | Fallback tập trung, không thể kết luận URL live đang lỗi |

**P0 hiện tại: chưa có lỗi mới được xác nhận.** Mọi chỉnh sửa tương lai chạm quote/hold/giá/quyền cần gate regression P0; nếu phát hiện lỗi ở gate mới phải dừng release và xử lý trước nội dung.

## 5. OPERATOR: quản lý được gì, còn phải làm ngoài giao diện gì?

| Nội dung/nghiệp vụ | Giao diện/API hiện có | Khoảng trống và quyền sở hữu đúng | Bằng chứng |
|---|---|---|---|
| Logo và giới thiệu công khai | `OperatorReviewsPage/ProfileForm`: admin sửa description/logoUrl; staff đọc, phản hồi review chỉ admin | Có quản lý URL rồi, không cần schema logo mới. Thiếu preview, kiểm tra ảnh lỗi và cách tìm chức năng vì nằm trong “Hồ sơ và đánh giá” | `Marketplace.tsx`, `MarketplaceDtos.ProfileInput`, `MarketplaceService.updateProfile` |
| Danh tính/liên hệ nhà xe | Entity có name/phone/email/address; quản trị nhà xe thuộc luồng admin/onboarding | Operator public profile contract chỉ có id/name/description/logo/rating; chưa có self-service sửa public contact riêng. Không cho operator tự sửa pháp nhân/trạng thái governance qua form nội dung | `TransportOperator`, `AdminOperatorDtos`, `MarketplaceDtos.Operator` |
| Xe | Tạo xe, đổi biển số/loại/trạng thái, readiness/bảo dưỡng | `Bus.imageUrl` có nhưng FleetDtos/BusForm thiếu field: thay URL hiện chưa có đường quản lý hỗ trợ qua UI/API fleet. Seed/source cung cấp URL ban đầu; không coi sửa SQL là workflow sản phẩm | `OperatorBusesPages`, `FleetMaintenance`, `fleet/FleetDtos.java`, `BusService` |
| Loại xe/mẫu ghế | Xem danh mục và SeatLayoutPreview; mô tả + số ghế | **Danh mục chỉ đọc có chủ đích**, không tự thêm CRUD cho nhà xe trên busType dùng chung. Ảnh minh họa loại xe có thể static, media xe riêng do nhà xe quản lý | `OperatorBusTypesPages`, `BusTypeController` GET-only, `BusType` |
| Nội thất/tiện nghi | Chưa có contract ảnh nhiều tấm, amenities hoặc quản lý chúng | Cần bus-scoped metadata/media, không gắn tiện nghi theo ảnh/tên loại. Giá trị “chưa xác nhận” khác với false | `FleetDtos`, `types/operator.ts`, `TripSearchDtos`, `MarketplaceDtos` |
| Tuyến/lịch trình | Đăng ký tuyến từ route-catalog, bật/tắt association, đọc stop/offset | Operator chưa tạo/sửa định nghĩa route/location dùng chung. Đây là ranh kiến trúc hiện tại, không bug. Muốn thêm catalogue cần quy trình quản trị riêng, chưa cần cho M30 | `OperatorRoutesPages`, `OperatorRouteController`, `RouteDtos` |
| Giá | FareEditor theo cặp điểm dừng, validate, xem lại rồi PUT bảng giá | Đã có UI, không cần sửa DB để đổi giá. Không trộn voucher vào giá gốc; giữ nguồn giá server | `features/operator/FareEditor.tsx`, `fareSchema.ts`, `OperatorRouteFareService` |
| Chuyến và vận hành | Tạo chuyến trên route/bus/time, dashboard/dispatch/lifecycle/crew/attendance | Đã có; ảnh/dịch vụ bổ sung không được thay inventory hay tự đổi trạng thái theo giờ | `OperatorTripsPages`, `TripWorkspace`, `OperatorDispatchPage`, M25/M29.3 |
| Voucher | Admin tạo/edit trước usage, activate/pause, staff read; effective state và usage | Hoàn thiện discovery khách bằng projection riêng, không thêm quyền quản trị cho customer/System Admin | `features/operator/Vouchers.tsx`, `promotion/VoucherController/Service`, M24B |

Nội dung cần chuẩn bị bên ngoài trong hiện trạng: file ảnh và quyền dùng; mapping media/địa danh; cấu hình catalogue ban đầu (đã có seed), mô tả dịch vụ đúng dữ liệu. Nội dung **không cần** sửa DB/source trực tiếp: logo/mô tả công khai, giá, tạo chuyến, voucher vì đã có API/UI. Audit không thực hiện bất kỳ thay đổi dữ liệu nào.

## 6. Bộ hình ảnh và nội dung cho toàn bộ catalogue demo

### 6.1. Phạm vi có căn cứ và cách đếm

`DemoDataSeeder.seedOperators/seedLocations/seedRoutes/seedBusTypes/seedBuses` và `docs/demo-data.md` định nghĩa:

- **3 nhà xe hư cấu:** An Phú Express, Minh Thành Limousine, Tây Nguyên Travel.
- **6 địa danh:** TP.HCM, Đà Lạt, Buôn Ma Thuột, Nha Trang, Đà Nẵng, Huế.
- **9 tuyến có hướng hoặc nhiều điểm dừng**, 13 liên kết nhà xe–tuyến; không cần 9 ảnh tuyến riêng vì có thể tái dùng 6 ảnh địa danh.
- **3 loại xe:** Limousine 22, giường nằm 34, ghế ngồi 40.
- **7 xe:** 51B-770.01/.02/.03 (An Phú), 50F-880.01/.02 (Minh Thành), 47B-660.01/.02 (Tây Nguyên). Seed mô tả lịch demo, không chứng minh có xe vật lý thật hoặc quyền dùng ảnh hãng xe trùng tên.

**Bộ hoàn thiện đề xuất: 16 asset sử dụng**, gồm 1 hero + 6 địa danh + 3 logo + 3 ngoại thất minh họa loại xe + 3 nội thất minh họa cấu hình. Tái dùng 3 file ngoại thất hiện có; cần người dùng chuẩn bị **13 file mới** (hero thay thế vẫn tính là một file mới). Nếu quyết định thay cả 3 ngoại thất hiện có: **16 file mới**, không phải 19. Không bị giới hạn ở 4 file.

Đây là bộ **demo minh họa trung thực**, không phải gallery xác nhận từng xe. Phương án xác thực từng xe là **7 bộ × 2 ảnh = 14 ảnh xe**, thay cho 6 ảnh minh họa loại xe: tổng **24 asset** gồm 1 hero + 6 địa danh + 3 logo + 14 ảnh xe. Chỉ theo phương án này khi có ảnh/bảng đối chiếu thật; **không yêu cầu tìm 14 ảnh stock rồi gán cho 7 biển số hư cấu**. Hai phương án thay thế nhau, không cộng dồn 16+24. Gallery của chuyến tái dùng media xe/loại xe phù hợp, không phát sinh một bộ ảnh cho từng trip/date.

### 6.2. Danh sách ảnh và cách tích hợp

| Loại / số lượng hợp lý | Tên file người dùng cung cấp | Tỷ lệ; px; định dạng | Sử dụng và tái dùng | Mức xác thực | Frontend / API / DB cần |
|---|---|---|---|---|---|
| Hero: **1** | `hero-coach.webp` | 2:1; 1920×960; WebP | Hero Home; một bản đủ desktop/mobile, crop theo M29 | Minh họa thương hiệu, không khẳng định xe nhà xe cụ thể | Static; đổi tham chiếu HomePage; không DB |
| Điểm đến: **6** | `destination-ho-chi-minh.webp`, `destination-da-lat.webp`, `destination-buon-ma-thuot.webp`, `destination-nha-trang.webp`, `destination-da-nang.webp`, `destination-hue.webp` | 4:3; 1200×900; WebP | Card tuyến Home, giới thiệu điểm đến có link tìm kiếm; cùng thành phố dùng chung các chiều tuyến | Cảnh đúng địa danh, được phép dùng; không mô tả là bến xe/điểm đón | Demo dùng manifest ánh xạ location ID đã kiểm tra; dynamic CMS chưa cần. Route API giới hạn 8 không phải lý do bỏ các địa danh còn lại khỏi bộ catalogue |
| Logo nhà xe demo: **3** | `operator-demo-an-phu.png`, `operator-demo-minh-thanh.png`, `operator-demo-tay-nguyen.png` | 1:1; 512×512; PNG trong suốt hoặc SVG chính thức tin cậy | Discovery, public profile, header thông tin chuyến nếu có; không ảnh riêng từng màn hình | Bộ nhận diện hư cấu cho demo. Nếu dùng doanh nghiệp thật phải dùng logo được cho phép của đúng doanh nghiệp | `logoUrl`/ProfileForm đã có DB/API; render logo Home chỉ frontend |
| Ngoại thất theo loại: **3 hiện có** | Giữ `bus-standard.jpg`, `bus-sleeper.jpg`, `bus-limousine.jpg`; nếu thay: `bus-type-standard-exterior.webp`, `bus-type-sleeper-exterior.webp`, `bus-type-limousine-exterior.webp` | Hiện 800×533 JPEG; ảnh thay 3:2, 1200×800 WebP | TripCard, phần xe ở TripDetail, giới thiệu loại xe; không tính thêm ảnh cho gallery | **Minh họa loại xe**, không phải xác nhận biển số/nhà xe. Ngoại thất không tự chứng minh nội thất/tiện nghi | Trước mắt static mapping có nhãn; contract media đầy đủ để quản lý động về sau |
| Nội thất theo cấu hình: **3** | `bus-type-standard-interior.webp`, `bus-type-sleeper-interior.webp`, `bus-type-limousine-interior.webp` | 4:3; 1200×900; WebP | Gallery thông tin loại xe trong detail; tái dùng theo cấu hình đúng và nhãn | Minh họa có ghi rõ, phù hợp 2+2 / giường hai tầng / ghế 1+1. Không gán “nội thất thực tế chuyến” khi không có xác nhận | Static đủ cho demo minh họa; phải có cách xác định busType, alt/caption. Muốn cam kết xe cụ thể cần media bus-scoped/backend |
| Phương án thay thế: **14 ảnh xác nhận / 7 xe** | `bus-51b-770-01-{exterior,interior}.webp`; tương tự `51b-770-02`, `51b-770-03`, `50f-880-01`, `50f-880-02`, `47b-660-01`, `47b-660-02` | Ngoại 3:2 1200×800; nội 4:3 1200×900; WebP | Card dùng cover, TripDetail dùng cùng bộ; mọi ngày chạy cùng xe dùng lại khi cấu hình còn đúng | Chỉ cung cấp nếu thực sự có nguồn xác nhận tương ứng; tên biển số demo là khóa minh họa catalogue, không chứng nhận xe ngoài đời | Cần bus→media/caption/loại nguồn. Một `buses.image_url` chỉ đủ cover; gallery nhiều ảnh cần mở rộng contract/lưu trữ. Không có ảnh thì giữ bộ minh họa có nhãn |
| Banner khuyến mãi, ảnh nhà xe/bến, ảnh hướng dẫn bổ sung: **0 bắt buộc** | Chưa yêu cầu | Nếu sau này dùng promo banner: 2:1, 1200×600 WebP | Voucher card nên dùng text/icon và terms lấy API; bến chỉ thêm khi có thông tin boarding đáng dùng | Không ảnh logo ngân hàng/QR/hành khách stock; không số giảm giá nhúng trong ảnh | Không thêm media chỉ để có section. Ảnh bến thật cần nguồn/địa chỉ khớp, task riêng |

Không yêu cầu banner cho từng tuyến, ảnh riêng từng ngày/chuyến, ảnh cho mỗi review, ảnh trang trí tài chính/dispatch, hoặc thêm địa danh/nhà xe/loại xe không tồn tại. Ảnh loại xe **khác mục đích** ảnh nhà xe; logo không thay thế thông tin doanh nghiệp hoặc bằng chứng chất lượng dịch vụ.

### 6.3. Người dùng cần cung cấp ngoài ảnh

| Gói nội dung | Số lượng / nội dung tối thiểu | Cách dùng trung thực |
|---|---|---|
| Nhà xe | **3 mô tả**, mỗi mô tả khoảng 80–150 từ; logo, tên hiển thị; phạm vi tuyến từ catalogue; thông tin liên hệ nào được phép công khai | Nêu nhà xe demo hư cấu; không lấy thành tích/pháp nhân của doanh nghiệp trùng tên |
| Loại xe | **3 mô tả cấu hình** ngắn; giải thích số ghế/tầng, điểm khác biệt; danh sách tiện nghi nào có căn cứ | Kế thừa seed 22/34/40; không suy Wi-Fi, WC, USB, nước uống từ chữ limousine |
| Điểm đón/trả | **6 bản mô tả địa điểm/hướng dẫn** theo catalogue hiện tại, và địa chỉ công khai nếu có nguồn đúng | Seed hiện có địa chỉ khu vực mang tính demo; chưa đủ gọi là vị trí lên xe ngoài đời đã xác minh. Không yêu cầu bịa địa chỉ cụ thể |
| Ảnh và quyền | Một danh mục nguồn/tác giả/quyền, thật hay minh họa, chủ thể, ID/mã áp dụng, caption; thông tin nguồn không nhất thiết công khai toàn bộ | Người dùng tự tìm/tạo/cung cấp; audit không làm thay. Với nguồn AI: ghi minh họa, không giả là xe thật |
| Ưu đãi demo | Một vài campaign nếu muốn demo (đề xuất **1–2 campaign cho nhà xe đã có**, không bắt đủ cả 3), điều kiện hợp lệ, chủ sở hữu, thời hạn theo ngày demo, quyền công khai | Tạo qua UI/API M24B trong task triển khai được phép; audit không tạo. Không hardcode mã/số giảm vào source/banner |
| Hướng dẫn/chính sách | Bản FAQ ngắn: login/hold, contact vs passenger, mô phỏng, đổi/hủy, hỗ trợ, vé VOID | Nội dung dựa quy tắc đang thực thi; không tạo chính sách mới để hợp lời quảng cáo |

Không yêu cầu người dùng tự sáng tác review hoặc sao. Demo review cần đi qua booking hợp lệ, chuyến completed và eligibility, hoặc dùng fixture có ghi rõ chỉ ở môi trường kiểm thử, không trộn vào dữ liệu công khai như đánh giá thật.

### 6.4. Thư mục, chất lượng và fallback

Cấu trúc **đề xuất**, chưa tạo file/thư mục ảnh:

```text
frontend/public/images/busgo/
  hero-coach.webp
  bus-standard.jpg / bus-sleeper.jpg / bus-limousine.jpg  (hiện có)
  destinations/destination-{ho-chi-minh,da-lat,buon-ma-thuot,nha-trang,da-nang,hue}.webp
  operators/operator-demo-{an-phu,minh-thanh,tay-nguyen}.png
  bus-types/bus-type-{standard,sleeper,limousine}-interior.webp
  buses/bus-{ma-xe}-{exterior,interior}.webp              (chỉ khi chọn bộ xác thực)
```

Đây là ký pháp liệt kê, không phải một file có dấu ngoặc trong tên. Ngoại thất loại xe thay thế nếu có đặt cạnh nội thất trong `bus-types/`. Thư mục chỉ rõ nguồn minh họa và nguồn theo xe, nhưng **metadata/nhãn UI mới là thứ quyết định cách mô tả**, không dựa tên thư mục duy nhất.

Ảnh không watermark/chữ quảng cáo/giá nhúng, không phóng lớn từ ảnh nhỏ, crop giữ chủ thể. Hero mục tiêu ≤350 KB, ảnh nội dung ≤180 KB, logo ≤80 KB; nhận bản gốc JPEG/PNG nếu chưa có WebP. Ảnh hero hợp teal `#0f766e`, nền `#102f2e`, nhấn `#d6a76b`, không đổi tokens M29. Logo dùng contain; ảnh xe/destination dùng cover với vùng an toàn; có width/height hoặc aspect-ratio, lazy-load bên dưới, alt và caption phù hợp.

Ảnh lỗi phải có fallback cuối bằng icon/chữ tên, không tiếp tục ảnh gãy; đổi URL/chuyến phải reset lỗi; minh họa có nhãn nhìn thấy trên desktop và mobile. Không thay mã QR bằng bitmap, không ảnh hóa sơ đồ ghế, không đưa ảnh vào sticky CTA. Có thể dùng ảnh AI cho hero/logo demo/minh họa chung do người dùng tự chuẩn bị; không dùng AI để cam kết nội thất/tiện nghi/xe thật.

## 7. Voucher: hợp đồng và luồng đề xuất dựa M24B

### 7.1. Vì sao khách khó tìm dù nhà xe tạo được

`VoucherController` hiện chỉ có `/operator/vouchers` scoped admin/staff; `VoucherService.list/view` là quản lý, gồm actor/usage/effective state, không phải DTO công khai. Khách chỉ có `POST /bookings/voucher-quote` nhận holdToken + mã và tùy chọn voucherCode khi tạo booking. `BookingPage` có input mà không có list/selector; Home/Search/Trip không query campaign. `SecurityConfig` chưa có rule công khai dành cho voucher discovery. **Thiếu một read model và một flow khám phá**, không phải thiếu engine giảm giá.

Không xác nhận có mã active live trong audit. `DemoDataSeeder` không tạo voucher; nếu người dùng đã tạo qua UI thì có thể có, cần kiểm tra bằng API được phép trong task sau. Không hiển thị một campaign chỉ vì persisted state là ACTIVE: nó còn có thể chưa bắt đầu, hết hạn hoặc hết lượt.

### 7.2. Các quy tắc phải tái dùng

`VoucherService.normalize/evaluate/usage/exhausted`, `BookingService.voucherQuote/create`, `PromotionMoney`, V24 và handoff M24B xác nhận:

1. Mã unique **trong từng nhà xe**, không global; customer có thể thấy cùng code ở hai nhà xe. Chọn theo voucher ID + operator, quote vẫn dùng code theo trip's operator.
2. Chỉ WEB CUSTOMER, nhà xe/tuyến đủ điều kiện commerce; không tự mở cho PHONE/assisted/System Admin.
3. State ACTIVE; **thời điểm hiện tại** nằm `[startsAt, endsAt)`; không mặc định chiến dịch dựa ngày khởi hành. Nếu muốn departure-date campaign đó là nghiệp vụ mới, chưa đề xuất ở M30.
4. `operator_route_id` là **ID liên kết tuyến của nhà xe**, khác route ID trong search result và khác cặp pickup/dropoff. Backend tự resolve từ trip; không nhận operatorRouteId đoán từ frontend.
5. Gross của đúng hành trình × số ghế phải đạt minimumSpend; PERCENT/FIXED có cap, BigDecimal/rounding theo engine; phải còn ≥0,01 payable mỗi ghế. Frontend không tính lại hoặc làm tròn thành miễn phí.
6. Total/per-customer capacity tính REDEEMED + RESERVED còn hạn; RELEASED/reservation hết hạn không tiêu lượt. Không công bố chính xác còn bao nhiêu lượt nếu chưa có chủ đích sản phẩm.
7. Quote/list là snapshot không reserve. Tạo booking revalidate/lock và reserve atomically; payment redeem, expiry/unpaid cancellation release. Hủy đã thanh toán không trả lại lượt; đổi chuyến không tự áp mã mới. Snapshot gross/discount/net lịch sử giữ nguyên nghĩa M24B/M20/M21.

### 7.3. Hợp đồng mới đề xuất — chưa triển khai

**Quyền công khai cần là quyết định rõ:** hiện Terms không có visibility flag. Không được tự hiểu mọi campaign ACTIVE là được quảng bá. Đề xuất thêm `customerVisible` (mặc định false với dữ liệu cũ, operator admin chủ động bật) và projection an toàn. Nếu chọn quy ước mọi mã active đều public thì phải được chốt như quyết định sản phẩm riêng; báo cáo này không mặc định điều đó. Visibility chỉ kiểm soát discovery, **không làm mã riêng tư đang hợp lệ mất khả năng nhập tay**.

| Endpoint đề xuất | Actor/input | Output tối thiểu và giới hạn |
|---|---|---|
| `GET /public/promotions` | Guest hoặc user; page/size giới hạn; optional operatorId/route filter đã validate | Chỉ campaign opted-in, nhà xe/tuyến hoạt động, trong thời gian/còn capacity toàn cục ở snapshot. DTO: id, operator{id,name}, code, name, description công khai, discountType/value, maxDiscount, minimumSpend, startsAt/endsAt, phạm vi tuyến công khai. `eligibility=REQUIRES_CONTEXT`; không có net price hoặc lời “áp dụng cho bạn” |
| `GET /trips/{tripId}/promotions?pickupLocationId=...&dropoffLocationId=...` | Guest/user, resolver hành trình hiện hữu; optional seatCount 1–5 để xem tham khảo, không phải giá tin cậy cuối | Danh sách opted-in đúng operator/operatorRoute và điều kiện hiện tại có thể kiểm tra. Trạng thái `REQUIRES_LOGIN`, `REQUIRES_SEATS`, `CONDITIONAL`; user-specific result nếu auth thì cache private. Không cần quote N chuyến khi render search |
| `POST /bookings/voucher-options` | Chỉ CUSTOMER, body `{holdToken}`; server resolve owner, trip, route, seats, current fare | Các campaign opted-in cho hold này, eligibility/reason công khai phù hợp. Quote dùng cùng logic engine; không reserve. Có thể trả gross/discount/net cho từng option bằng batch có giới hạn; không đổi semantics endpoint quote cũ |
| `POST /bookings/voucher-quote` **đã có** | CUSTOMER; `{holdToken,voucherCode}` | Giữ contract success `eligible=true` hoặc error envelope 409 theo mã hiện tại; chọn option/nhập tay đều đi qua bước này trước submit |
| `POST /bookings` **đã có** | CUSTOMER; optional voucherCode | Revalidate và reserve authoritative; không nhận discount/net do client gửi |

Không cần triển khai đủ 3 endpoint discovery ngay: **làm voucher-options + checkout trước**, sau đó public/promotions, cuối cùng trip-scoped enrichment nếu cần. Search có thể dùng batch read theo các trip đang hiển thị trong task riêng hoặc một summary trong search response; không N+1 gọi quote/hold cho mỗi card. Dùng service eligibility dùng chung/pure read helpers, không sao chép một engine tính discount khác. Việc tách helper phải giữ lock order trip→operator→inventory→voucher/usage khi booking; discovery không giữ lock đặt chỗ hay reserve lượt.

Bổ sung visibility cần DTO/UI + migration additive nếu lưu trong DB; không thêm bảng promotion thứ hai trùng M24B. Cache guest/public và user-specific phải tách; không cache public kết quả giới hạn cá nhân. Kiểm tra anonymous/auth routes và role M26 trước khi expose.

### 7.4. Hiển thị theo từng bước

- **Home:** thẻ ưu đãi thật, tên nhà xe, phạm vi, điều kiện, thời hạn và CTA chọn hành trình. Có thể copy code; không nhận “đã áp dụng”. Nếu không có campaign đủ điều kiện, ẩn section hoặc thông báo gọn, không dựng voucher demo trong JSX.
- **Search:** badge “Có ưu đãi nhà xe — xem điều kiện” chỉ khi có projection tương ứng; giữ giá/chỗ là giá gốc hiện hành. Không strike-through giá gốc để in giá sau mã khi chưa đủ ngữ cảnh; không sắp xếp theo net ước đoán.
- **TripDetail:** show campaign theo operator + tuyến của trip, điều kiện minimumSpend, số ghế và login rõ; khi guest chỉ lưu lựa chọn gợi ý, không coi đó là reserve. Không làm mất selection khi xem terms.
- **Checkout:** selector theo owned hold, hiển thị đủ tên nhà xe/mã/điều kiện/reason; chọn một mã → quote server → gross/discount/net; vẫn nhập tay cho mã không quảng bá. Không có mã phù hợp vẫn đặt giá gốc bình thường. Hold hết hạn thì dừng options/quote và quay lại flow chọn ghế.
- **Sau đặt:** dùng committed price snapshot trong PromotionPrice/BookingSummary/ticket; pause/expire/visibility đổi không làm mất giảm đã cam kết. Hủy/đổi dùng net thực và entitlement M24B, không lấy giá từ campaign mới.

### 7.5. Riêng tư, race và nghiệm thu

Không trả `created_by`, `updated_by`, raw usage/history, customer IDs, tổng doanh thu, chi tiết lượt khách khác hoặc campaign draft/paused/private. Không dùng response map SELECT * của quản lý làm DTO public. Code public chỉ xuất nếu operator opt-in; mã private nhập tay không bị lộ qua thử tìm kiếm. Rate/page limits và trường projection nhỏ, không cần hệ marketing phức tạp.

Nghiệm thu gồm: cùng code khác operator; sai operatorRoute dù cùng tên tuyến; đúng thời điểm start/end UTC/VN; minimumSpend theo 1 vs nhiều ghế; phần trăm/cap/fixed/cent rounding; hết total/per-user; reservation hết hạn; pause/suspension giữa options→quote→create; hai khách tranh lượt cuối; quote không reserve; mã private nhập tay; logout đổi tài khoản không giữ options riêng tư; create fail rollback không tiêu lượt; paid cancellation không hoàn lại lượt; M20/M21 giữ net/refund đúng. Những ca này kiểm tra dùng engine/server và bộ regression M24B hiện có, không chỉ test chuỗi hiển thị.

## 8. Điểm hoàn thiện theo màn hình

Thang 10 mang tính đánh giá source: 0–3 thiếu luồng cốt lõi; 4–6 có luồng nhưng thiếu thông tin/khám phá; 7–8 đủ nghiệp vụ demo với khoảng trống rõ; 9–10 cần nội dung và kiểm chứng trải nghiệm rộng hơn. Không lấy trung bình cơ học các dòng để tuyên bố chất lượng thực tế; điểm tổng thể ở mục 1 là đánh giá chuyên môn có giới hạn.

| Màn hình / vai trò / điểm | Chức năng hiện có | Điểm mạnh | Thiếu sót xác nhận | Cải thiện | Ưu tiên | Cần backend? | File/component liên quan | Tiêu chí nghiệm thu |
|---|---|---|---|---|---|---|---|---|
| Home — guest/customer — **6/10** | Search, operator/routes/upcoming/reviews, guide | API data và trạng thái rỗng/lỗi thật, nói rõ mock | Logo chưa dùng, thiếu ảnh địa danh/ưu đãi, tuyến chọn hôm nay | Nội dung có context, FAQ/link đúng | P1 | Ảnh/link: không; promo: có | HomePage, HomeDiscovery, CustomerLayout | 6 địa danh có mapping hoặc icon; không fake trip/rating; guest chọn tuyến/ngày không mất context |
| Search — guest/customer — **8/10** | Bộ lọc, 6 sort, pagination/chips/editor | Server result count, URL restore, sheet | Không promo, chưa có ảnh mobile/địa chỉ chi tiết | Card đầy đủ và link detail/promo có điều kiện | P1 | Summary promo/địa chỉ: có | SearchPage, SearchForm, FilterPanel, TripCard | Payload/sort/pagination không đổi; ảnh có lối xem mobile; giá/chỗ không giả net |
| Trip detail — guest/customer — **6/10** | Lịch trình, ops, ghế, giá | Cùng luồng chọn ghế, không thêm bước bắt buộc | Không ảnh, rating/link, mô tả/policy/địa chỉ đầy đủ | Cụm thông tin thu gọn + media/trust | P1 | Field cũ: không; enrich: có | TripPage, TripSearchDtos/Service, LiveTripBlock | Xem xe/nhà xe/điểm đón/quy tắc trước hold, không che ghế/CTA |
| Seat selection — customer/guest trước login — **8/10** | Tầng/geometry, max 5, polling/draft | Server revalidate/hold, xử lý conflict | Ngữ nghĩa STANDARD trong demo, thiếu giải thích login sớm | Nhãn đúng context và hold vs draft rõ | P1/P2 | Nhãn context: không; taxonomy nếu sửa: có | SeatMap, selectionDraft, TripPage, DemoDataSeeder | Tầng/gap/mã giữ nguyên, 6th seat không chọn, conflict/expiry/login restore pass |
| Auth — guest — **8/10** | Login/register/returnTo | Giữ đường quay lại qua register/login | Chưa có nhắc login sớm ở hành trình mua | Thông báo tại CTA giữ chỗ | P2 | Không | AuthPage, auth/access, TripPage | Chọn ghế→register→login về đúng trip và revalidate, không mất context |
| Checkout — customer — **7/10** | Contact/hold/voucher quote/giá/submit | Disable unresolved quote, retry recovery | Chỉ mã nhập tay; không per-passenger | Selector; contact-only rõ | P1 | Options: có; passenger tùy chọn: có | BookingPage, BookingDtos/Service, VoucherService | Quote/create same authority, hold expiry không submit, mã lỗi có đường bỏ/chọn lại |
| Payment — customer — **8/10** | Mock confirm, deadline/recovery | Nhãn mô phỏng rõ, không giả chuyển tiền | Không cần gateway để hoàn thiện demo | Giữ đường về booking và trạng thái | P2 | Không cho nội dung | PaymentPage, BookingSummary | Pending/confirmed/cancelled không confirm sai; không bấm lặp thu hai lần |
| My Bookings — customer — **8/10** | Status tabs, list, price, ticket/detail links | Empty theo filter, pagination | Chưa thấy nhanh điều kiện action từ list | Có thể action summary gọn, không ảnh mới | P2 | Tùy projection; không bắt buộc | MyBookingsPage | Empty/filter/back đúng, mọi link tới booking thuộc user |
| Booking detail — customer — **7/10** | Contact/price/ops/support/review/đổi/hủy | Nghiệp vụ sâu và độc lập | Copy hủy mâu thuẫn; section dài | Action navigation/copy đúng | P1/P2 | Chủ yếu không | BookingDetailPage, CancellationSection | Hủy phần/toàn rõ nghĩa, không bỏ warning/eligibility/server history |
| Ticket — customer — **8/10** | QR per ticket, VOID handling | QR từ server, valid/void phân biệt | Tên vé fallback contact có giới hạn đã công bố | Giải thích, không giả per-passenger | P2 | Chỉ nếu mở passenger contract | TicketPage, PaymentTicketService | QR valid đọc được; VOID/missing không render; giá snapshot không đổi |
| Đổi/hủy/hoàn — customer/admin nhà xe — **8/10** | Eligibility/quote/hold/confirm/history/refund | Trước/sau tiền rõ, resume | Khó khám phá trước mua; không phải thiếu engine | Tóm tắt rule trước mua, link đúng | P1 | Rule read model: có | Modification, PartialCancellation, CancellationSection, booking services | Cutoff/boarding/suspension và net refund không đổi; quote phải xác nhận |
| Public operator profile — guest/customer — **6/10** | Logo/description/routes/types/trips/reviews | Review và chuyến gắn API | Tuyến text chưa link tìm; no public contact/media; fallback logo ẩn | Route CTA/nhận diện/nội dung đúng | P1 | Contact/media: có; links: không | Marketplace.OperatorProfilePage, MarketplaceDtos | Logo fallback, route chọn ngày, không công khai pháp nhân/contact riêng tư |
| Notifications — customer/operator — **7/10** | Inbox/bell/read/deep link/preferences/delivery | Polling và retry/email state | Mở booking bị phụ thuộc mark-read | Link vẫn mở khi read fail | P2 | Không bắt buộc | Notifications, notificationsApi | Read fail không chặn xem nội dung; session cache riêng; keyboard dùng được |
| Support — customer/operator/admin — **8/10** | Ticket/chat/status/escalation/dispute | Action do server, privacy scope | Guest chưa có FAQ trước mua; guest ticket không thuộc scope hiện tại | FAQ static, link booking context | P2 | FAQ không; không mở guest flow | Support, supportModel, CustomerLayout | Khách không thấy note nội bộ; decision không tự hoàn tiền; retry/version conflict rõ |
| Operator profile/reviews — admin/staff — **7/10** | Sửa logo/description, reply | Có phân quyền và public preview link | Chưa preview ảnh/fallback, khó tìm trong mục review | Đặt nhãn nội dung rõ, preview | P1 | Trường cũ không | Marketplace.ProfileForm/OperatorReviewsPage | Admin sửa đúng tenant, staff read, URL lỗi không lưu như ảnh xác nhận |
| Operator fleet/types — admin — **5/10 về nội dung bán vé** | Fleet/readiness/maintenance, catalogue read-only | Nghiệp vụ xe đã có, không cần rebuild | Không imageUrl editor/gallery/amenity | Media/metadata editor scoped bus | P1 | Có; cover không cần cột mới | OperatorBusesPages, FleetMaintenance, OperatorBusTypesPages, FleetDtos | Đổi ảnh không sửa plate/status/inventory; foreign bus 404/403; data cũ fallback |
| Operator routes/fares/trips — admin — **8/10** | Catalogue attach, fare editor, create trips/ops | Giá/chặng và quyền rõ | Thiếu public boarding guidance; catalogue vẫn quản trị tập trung | Enrich thông tin công khai, giữ scope | P1/P2 | Projection có | OperatorRoutesPages, FareEditor, OperatorTripsPages, RouteDtos | Fare/segment/overlap guards giữ nguyên; không cho sửa shared route ngoài quyền |
| Operator vouchers — admin/staff — **8/10** | CRUD terms chưa dùng, state, usage | Lifecycle/restrictions đầy đủ | Không opt-in public/discovery khách | Visibility và preview public card | P1 | Có | Vouchers, VoucherDtos/Service/Controller | Private không public, đổi visibility không reprice booking, staff không mutate |
| Dashboard/dispatch/System Admin — vai trò đúng — **8/10 trong phạm vi M29** | Vận hành, governance, finance/support | Đã có M29 hardening, rõ mock | Không xác nhận thiếu ảnh trang trí; catalogue content governance chưa là CMS | Chỉ regression và quyền, không redesign | P2 | Không cho thay đổi hình ảnh | OperatorHome/Dispatch, AdminPages, Onboarding, Finance, Support | Missing inventory/booking/payment/boarding độc lập; không ảnh hóa bảng hoặc lộ PII |

## 9. Những thay đổi thật sự cần backend

| Hạng mục | Tận dụng hiện có | Bổ sung cần thiết | Chưa cần |
|---|---|---|---|
| Public trip/operator information | busImageUrl, busType name, operator rating/logo/description, stops, Location.address | DTO chọn lọc description/seatCount/public boarding guidance; policy summary lấy từ quy tắc server; public contact nếu chủ sở hữu cho phép | Route/location CMS, bản đồ/GPS, policy engine tự chỉnh theo nhà xe |
| Cover ảnh xe qua giao diện | `buses.image_url` hiện có | FleetDtos/BusService/API frontend/BusForm nhận-trả URL, validate/authorization, phân loại ảnh minh họa hay xác nhận | Migration thêm lại image_url, upload service |
| Gallery và amenities đúng xe | quan hệ trip→bus→operator/busType | Bus-scoped media có thứ tự/caption/kind/illustrative; danh sách amenity xác nhận; read projection cho khách. Schema additive cho dữ liệu mới nếu quản lý động | CMS đa tenant tổng quát, version mọi ảnh theo từng vé |
| Voucher discovery | Tables V24, pricing/eligibility/lifecycle/read quote | Visibility opt-in, customer options/public projection, security/cache, typed DTO; UI chọn mã | Engine giảm giá thứ hai, platform subsidy, stacking/loyalty |
| Per-seat passenger nếu được chọn làm | BookingItem.passengerName/Ticket.passengerName có sẵn | Create request có mapping chắc chắn theo ghế, validation, persist/snapshot, tests đổi/hủy/ticket; không cần thêm cột tên nếu schema đã đủ | Giấy tờ cá nhân/định danh bắt buộc, account hành khách phụ |

Ảnh điểm đến/static FAQ, logo discovery từ payload, route CTA, Rating/link public profile, nhãn minh họa, fallback và tổ chức action booking **có thể làm frontend**. Chỉ thêm API/DB khi frontend thực sự thiếu nguồn dữ liệu/đường quản lý, không vì muốn mọi asset đi qua một upload service.

## 10. Roadmap M30.2–M30.6: nhỏ, độc lập và nghiệm thu từng đợt

Tách mỗi tiểu mục thành task/PR riêng nếu triển khai. Không yêu cầu hoàn thành cả milestone mới được review một phần. Mỗi task có fallback để dùng được dữ liệu cũ. Thứ tự giữ ưu tiên CUSTOMER → nội dung/ảnh → voucher → operator content → regression tổng; kiểm tra responsive/role tại từng task, không đợi M30.6 mới kiểm tra.

### M30.2 — Liền mạch hành trình CUSTOMER và rõ thông tin trước mua

**M30.2A — Nối điều hướng và sửa nội dung có sẵn (P1, frontend).**

- Mục tiêu/phạm vi: route discovery cho chọn ngày, public profile route CTA, link hồ sơ/rating từ chi tiết, giải thích chọn ghế chưa giữ chỗ/login; sửa copy hủy toàn bộ. Tổ chức link hủy/đổi/hỗ trợ có ngữ cảnh trong detail booking. Không thêm trang bắt buộc hoặc thay design system.
- File dự kiến: `HomePage.tsx`, `Marketplace.tsx`, `TripPage.tsx`, `BookingDetailPage.tsx`, `CancellationSection.tsx`, `features/customer/presentation.ts`, CSS customer scoped; tests customer hiện có.
- Backend/API: không bắt buộc; dùng dữ liệu đã có. Route CTA chuyển vào form chọn ngày hoặc SearchForm prefill, không tự đoán ngày có chuyến.
- Cần người dùng cung cấp: duyệt lời hướng dẫn FAQ ngắn nếu muốn; không phụ thuộc ảnh mới.
- Nghiệm thu: guest Home→tuyến→chọn ngày→search→ghế→register/login→khôi phục selection; link profile/back không mất journey; copy phân biệt hủy toàn bộ/một phần; giá/hold/payload giữ nguyên.
- Rủi ro: làm mất returnTo/search context, ảnh hưởng selector/CTA sticky; phải kiểm tra browser back và draft restore, không bypass server inventory.

**M30.2B — Thông tin điểm đón, loại xe và quy tắc trước mua (P1, API nhỏ + UI).**

- Mục tiêu/phạm vi: đưa mô tả busType/seatCount, địa chỉ/hướng dẫn đã được phép công khai và policy summary vào khối chi tiết thu gọn; tách thông tin hiện chưa xác nhận khỏi cam kết dịch vụ. Tái dùng rules đổi/hủy hiện hành, chưa cho tùy chỉnh policy.
- File dự kiến: `trip/search/TripSearchDtos.java`, `TripSearchService.java`, `marketplace/MarketplaceDtos.java` nếu enrich profile, `types/customer.ts`, `TripPage.tsx`, `BookingPage.tsx`; bổ sung test projection/rule summary phù hợp.
- Backend/API: mở rộng detail read DTO; không đưa nguyên entity/địa chỉ pháp nhân vào public. Static FAQ đủ cho mô tả chung ban đầu, nhưng không thay server eligibility khi bấm thao tác.
- Cần cung cấp: 3 mô tả loại xe, 6 hướng dẫn địa điểm demo có ghi rõ mức xác thực, quy tắc hiển thị được đối chiếu source. Không ép biến địa chỉ khu vực demo thành vị trí đón thật.
- Nghiệm thu: khách biết lên xe ở đâu/được thông tin gì/chính sách nào trước hold; dữ liệu thiếu hiển thị trung thực; cutoff dựa giờ đón đúng hành trình, boarding/suspension vẫn do server quyết định.
- Rủi ro: public PII, copy policy lệch engine, nhầm stop với route origin. Giữ projection whitelist và test segment cụ thể.

**M30.2C — Tên hành khách từng ghế (P2, task tùy chọn độc lập).**

- Mục tiêu/phạm vi: chỉ khi chốt cần vé có tên riêng, thêm danh sách tên theo ghế; chưa cần giấy tờ/tuổi/giới tính. Nếu chưa chọn task này, giữ contact-only có chú thích.
- File dự kiến: `BookingPage`, `BookingDtos/Service`, types customer, `PaymentTicketService` và test M8/M9/M20/M21 ảnh hưởng.
- API: extend create request backward compatible, kiểm tra đủ/không trùng/matching ghế hold; không tin seat IDs ngoài hold. Cột passengerName đã có, đánh giá schema trước khi thêm migration.
- Cần cung cấp: quyết định contact-only hay tên riêng bắt buộc/tùy chọn và wording; không cần danh sách người thật để test.
- Nghiệm thu: 2 ghế/2 tên ra 2 vé đúng; request cũ còn hợp lệ theo quyết định; đổi ghế giữ người, hủy một phần đúng item, contact không bị thay bằng hành khách.
- Rủi ro: mapping sai người/ghế và thay ticket snapshots. Không gộp task này vào việc đổi nhãn form.

### M30.3 — Nội dung và media theo catalogue hiện có

**M30.3A — Bộ nhận diện và ảnh khám phá (P1).**

- Mục tiêu/phạm vi: tích hợp 1 hero, 6 địa danh, 3 logo; render logo discovery/profile thống nhất, nội dung 3 nhà xe và FAQ ngắn. Giữ section và tokens M29, không đổi toàn bộ layout.
- File dự kiến: `public/images/busgo/**`, `HomePage`, `Marketplace`, CSS customer/marketplace; manifest mới dự kiến `features/customer/visualAssets.ts` nếu cần, không phải file đã tồn tại.
- Backend/API: không cần cho static mapping hoặc logo/description hiện có; gán URL/mô tả qua ProfileForm trong môi trường được phép sau này. Không sửa create-only seeder để ghi đè catalogue cũ.
- Cần cung cấp: 10 file mới thuộc hero/destination/logo + 3 mô tả nhà xe, nguồn/quyền và mapping; có thể nghiệm thu riêng từng gói ảnh.
- Nghiệm thu: đủ 6 địa danh trong manifest nhưng chỉ render theo data thực; tuyến chưa có trip không bị quảng cáo có chỗ; logo/URL lỗi có fallback; mobile giữ rõ chữ/CTA.
- Rủi ro: hardcode location IDs, crop sai, logo demo bị hiểu là doanh nghiệp thật. Mapping theo ID đã xác nhận của môi trường, có nhãn demo/manifest kiểm chứng.

**M30.3B — Ảnh xe/loại xe và gallery gọn (P1).**

- Mục tiêu/phạm vi: nhãn minh họa nhìn thấy, fallback cuối/reset lỗi, reuse 3 ngoại thất và 3 nội thất mới; khối xem ảnh ở TripDetail phục vụ cả mobile. Gallery chỉ 2 ảnh đại diện/cấu hình, không cần carousel tự chạy.
- File dự kiến: `components/ui.tsx`, `TripPage`, visual manifest, `customer.css`, media component nhỏ nếu cần. Chỉ enrich public media DTO khi có nguồn quản lý động, không giả định field đã có.
- Backend/API: minh họa demo có thể frontend/static; muốn media theo bus thật cần hợp đồng và persistence trong M30.5B. Trước đó tuyệt đối không gán minh họa thành gallery xác nhận từng xe.
- Cần cung cấp: 3 nội thất minh họa đúng cấu hình; hoặc bộ ảnh xác nhận thay thế đã chọn ở mục 6, không lấy cả hai bộ bắt buộc.
- Nghiệm thu: card/search/home/profile cùng semantics nguồn ảnh; chi tiết có đường xem trên ≤1100px; ảnh lỗi không đẩy vỡ layout; guest mở/đóng ảnh không mất selection/không gọi hold.
- Rủi ro: nhầm ảnh nhà xe, modal focus/scroll hoặc ảnh nặng làm khó chọn ghế. Không đổi thuật toán seat/hold.

### M30.4 — Khám phá voucher và giá minh bạch

**M30.4A — Options theo hold và chọn voucher tại checkout (P1).**

- Mục tiêu/phạm vi: visibility opt-in và read DTO phù hợp hold; selector + nhập tay + quote server. Nghiệm thu checkout trước khi mở quảng bá rộng.
- File dự kiến: `promotion/VoucherDtos/Service/Controller` hoặc customer controller mới scoped rõ, `BookingService/Controller`, `SecurityConfig`, `BookingPage`, `Vouchers.tsx`, `api/errors.ts`, tests promotions/M24B; migration additive **chỉ cho visibility nếu cần lưu**.
- Backend/API: `POST /bookings/voucher-options`, extend Terms/management view cho customerVisible mặc định false; giữ nguyên contract quote/create/lifecycle và quyền role/tenant.
- Cần cung cấp: 1–2 campaign qua UI vào môi trường demo được phép, quyền quảng bá, thời hạn phù hợp; không cần ảnh campaign.
- Nghiệm thu: các ca mục 7.5; private mã vẫn nhập tay được nhưng không lộ qua list; gross−discount=net; quote không reserve; booking tranh lượt cuối đúng; data cũ không tự public.
- Rủi ro: race quota/lock, schema upgrade, đổi net refund, cache chéo user; bắt buộc regression M24B/M20/M21 trước nghiệm thu.

**M30.4B — Ưu đãi Home/Search/TripDetail (P1, sau A).**

- Mục tiêu/phạm vi: DTO public và UI có điều kiện; không biến mọi ACTIVE thành public, không đặt giá giảm giả ở card. Cung cấp context/deep link chọn mã sau khi có hold.
- File dự kiến: public promotion read controller/DTO/service, `SecurityConfig`, `HomeDiscovery`, `SearchPage/TripCard`, `TripPage`, query keys/cache; search summary/batch nếu cần.
- Backend/API: chọn lần lượt public list rồi trip context; bounded query để tránh N+1; giữ anonymous/public vs personal eligibility riêng.
- Cần cung cấp: copy tên/mô tả công khai và campaign opt-in có thật; không banner giảm giá nhúng số.
- Nghiệm thu: guest nhìn điều kiện, user thấy eligibility đúng context, zero campaigns không placeholder; pause/expiry/suspend mất khả năng áp dụng mới nhưng booking cũ giữ snapshot; không đổi sort theo net chưa xác nhận.
- Rủi ro: lộ campaign private/usage, ước tính giảm sai, stale cache. Chỉ whitelist DTO và nói rõ cần kiểm tra khi đặt.

### M30.5 — Nhà xe quản lý được nội dung khách nhìn thấy

**M30.5A — Cover ảnh xe và profile preview (P1).**

- Mục tiêu/phạm vi: URL cover và loại nguồn trong fleet form, preview ảnh/logo/mô tả công khai; rõ nơi quản lý hồ sơ. Dùng file local/HTTPS được phép, không thêm upload service.
- File dự kiến: `fleet/FleetDtos.java`, `BusService.java`, `OperatorBusesPages`, `types/operator.ts`, `api/operatorApi.ts`, `Marketplace.ProfileForm`, media validators; test ownership/field validation.
- Backend/API: extend fleet DTO và persistence cho `imageUrl` sẵn có; nguồn minh họa/xác nhận có metadata mới thì schema additive nhỏ hoặc cùng hợp đồng media sau B, không migration trùng cover.
- Cần cung cấp: URL local của bộ ảnh, classification và quyền; public contact/description do người dùng phê duyệt.
- Nghiệm thu: admin nhà xe chỉ sửa bus của mình, staff không mutate, đổi cover không đổi status/maintenance/ghế/giá; dữ liệu cũ vẫn fallback; logo edit phản ánh preview công khai.
- Rủi ro: ảnh sai tenant, UI cho phép sửa pháp nhân/governance, URL ảnh lỗi. Không mở quyền dùng chung hoặc server-fetch ảnh tùy ý chỉ để preview.

**M30.5B — Gallery và tiện nghi có dữ liệu thật (P1 có điều kiện/P2 nếu chưa có nguồn).**

- Mục tiêu/phạm vi: media theo bus, 1 cover + nội thất có caption/nguồn/illustrative, danh sách tiện nghi đã xác nhận; public projection kế thừa theo trip→bus. Không để operator chỉnh busType/location dùng chung.
- File dự kiến: fleet DTO/service/repository/entity media/amenity nhỏ nếu cần, migration additive, public detail projection, types customer/operator, bus detail media form, TripPage. Thiết kế/tên schema phải chốt khi triển khai; đây chưa là migration được tạo.
- Backend/API: GET/PATCH metadata media bus-owned hoặc subresource, validation URL/type/order, ownership và audit thích hợp; dữ liệu “không biết” không tự chuyển thành có tiện nghi.
- Cần cung cấp: bộ media xác nhận hoặc bộ minh họa có nhãn, amenity facts theo bus/cấu hình. Nếu chưa có nguồn thì dừng ở A và bộ minh họa M30.3, không dựng dữ liệu.
- Nghiệm thu: sửa gallery qua UI không cần SQL/source, ảnh đúng chuyến/xe, data cũ không bể; rename busType không đổi mapping; không coi ảnh WC/USB minh họa là amenity xác nhận.
- Rủi ro: schema/media scope sai, dùng dữ liệu loại xe chung thay cam kết xe riêng, regress query payload. Không thay snapshot giá/seat inventory.

### M30.6 — Mobile, accessibility và browser regression tổng

**M30.6A — Tác vụ sau đặt vé và khả năng sử dụng (P1/P2).**

- Mục tiêu/phạm vi: action navigation booking, NotificationItem link độc lập mark-read, focus/keyboard cho media/voucher selector, empty/error có đường tiếp tục; guest FAQ rõ ranh hỗ trợ gắn booking.
- File dự kiến: `BookingDetailPage`, `Notifications`, `Support` nếu cải thiện copy/link, UI media/voucher, `foundations/Drawer`, CSS scoped **chỉ khi lỗi được xác nhận**.
- Backend/API: không bắt buộc; giữ permission/actions từ server. Không thêm guest support ticket hoặc cho dispute tự thay money.
- Cần cung cấp: wording đã duyệt; không cần ảnh mới.
- Nghiệm thu: keyboard mở/đóng selector/gallery, Escape/focus restore, retry không khóa navigation, mark-read fail vẫn mở đúng booking, lịch sử/CTA đọc được ở mobile; private cache sạch sau logout.
- Rủi ro: focus mất, CTA bị che, mở nhầm booking, tác vụ destructive thiếu xác nhận. Không bỏ confirmations để giảm click.

**M30.6B — Gate nghiệm thu toàn hành trình (P1).**

- Mục tiêu/phạm vi: build/tests liên quan và browser E2E theo vai trò, data thường/rỗng/lỗi/stale/session; matrix 1440/1100/834/390/360, kiểm tra ranh 1100/760, keyboard/reduced motion, thêm browser engine khác nếu môi trường có.
- File dự kiến: `frontend/tests/m29-customer-browser.cjs` và harness/operator/platform hiện có, fixture API; test backend M24B, booking/hold/payment, M20/M21/M26 ảnh hưởng; báo cáo nghiệm thu mới. Không sửa production chỉ để làm test pass nếu chưa xác định nguyên nhân.
- Backend/API: không thêm tính năng; smoke backend thật chỉ trên môi trường test/demo được phép, không reseed/reset dữ liệu đang có. Phân biệt fixture/live, lịch sử/lần chạy mới và UI-read/mutation.
- Cần cung cấp: bộ ảnh đã duyệt, ngày demo có chuyến và campaign có hiệu lực; tài khoản thử đúng vai trò được cấu hình an toàn.
- Nghiệm thu: không overflow toàn trang, QR valid/VOID đúng, hold expiry/seat conflict/voucher race/session/ownership pass, build green, không console/page exception chưa giải thích; ghi riêng mọi phần chưa được kiểm chứng. Không tuyên bố Safari/thiết bị thật pass nếu chỉ chạy viewport Edge.
- Rủi ro: fixed date fixture hết hạn, môi trường khác data seed, broad suite ảnh hưởng migration; dùng clock/date rõ, không sửa dữ liệu thật để “làm xanh”.

**Các task P2 có thể để sau M30.6:** compare 2–3 chuyến, download PDF vé, quản lý địa danh động. Chỉ làm nếu có mục tiêu sử dụng và tiêu chí nghiệm thu riêng; không kéo chúng vào critical path.

## 11. Những việc không nên làm

- Không làm lại M29, thêm dashboard ảnh lớn hoặc biến ứng dụng thành landing page marketing dài. Ưu tiên thông tin phục vụ quyết định và quản lý đặt vé.
- Không tự công bố nội thất/tiện nghi, voucher, rating, hotline, chứng nhận, lượt khách hay giá “từ…” không có căn cứ server/nội dung được phép.
- Không gán ảnh stock/AI cho nhà xe/biển số cụ thể như ảnh xác nhận; không lấy ảnh landmark làm địa điểm lên xe.
- Không làm lại voucher engine, tự tính net frontend, thêm stacking/platform subsidy/loyalty, sửa M24B refund entitlement hoặc cho paid cancellation trả lại lượt.
- Không đưa upload/CDN/CMS phức tạp, GPS/map/WebSocket, real payment M22 hoặc settlement M27B vào M30. Không làm tròn mất cent có ý nghĩa.
- Không mở quyền customer đọc API quản lý voucher; không cho operator sửa pháp nhân/governance/catalogue chung chỉ vì đang xây form nội dung; không lộ email/phone cá nhân/notes nội bộ.
- Không báo các nghiệp vụ hủy/đổi/hoàn/thông báo/hỗ trợ là “chưa có”; không dùng tài liệu deferred cũ để phủ nhận milestone mới.
- Không tạo review giả để lấp section, không reseed/update SQL dữ liệu thực, không thêm nhà xe/tuyến/loại xe chỉ để có thêm ảnh.
- Không gộp media, passenger contract, promotion discovery và content management vào một PR lớn. Không tự nhận production-ready từ điểm audit hoặc bộ ảnh đẹp.

## 12. Checklist nghiệm thu portfolio/demo chuyên nghiệp

### Thông tin và hành trình

- [ ] Guest tìm kiếm/xem detail/profile được; biết lúc nào cần login; register/login quay lại đúng lựa chọn và kiểm tra ghế mới.
- [ ] Route CTA cho chọn ngày có ngữ cảnh; không hứa có chuyến/chỗ từ catalogue route.
- [ ] Card có nhà xe, loại xe, giờ/ngày lịch/dự kiến, đón/trả, thời lượng, giá/chỗ, số chỗ và CTA nhất quán desktop/mobile.
- [ ] Detail có thông tin xe, đường xem ảnh, nguồn ảnh/caption, link nhà xe/review, địa chỉ/hướng dẫn phù hợp mức xác thực, quy tắc đổi/hủy trước mua.
- [ ] Thông tin không biết được ghi rõ; không nhầm “chưa xác nhận” với tiện nghi không có hoặc có.

### Nội dung và quản lý

- [ ] Bộ catalogue 3 nhà xe/6 địa danh/3 loại xe có ảnh/nội dung tương ứng, hoặc fallback có chủ đích; không thêm entity không tồn tại.
- [ ] Có nguồn/quyền/mapping của bộ ảnh; 13 file mới theo phương án minh họa hoặc bộ thay thế đã chọn; không bắt đủ mọi ảnh xác nhận khi không có xe thật.
- [ ] Logo/description qua UI sẵn có; cover/gallery/amenity sau M30.5 có đường quản lý rõ, đúng tenant và role, không cần sửa DB/source trực tiếp.
- [ ] Public projection không lộ liên hệ riêng tư/PII, campaign private, usage hoặc note nội bộ; shared catalogue không bị operator sửa ngoài quyền.

### Voucher và tiền

- [ ] Discovery chỉ quảng bá opted-in, có điều kiện/time/scope; không công bố giá sau giảm khi chưa đủ context.
- [ ] Checkout có danh sách phù hợp hold và nhập tay; quote server, 1 mã/booking, trường hợp lỗi cho chọn lại/bỏ mã.
- [ ] Gross/discount/net đúng ở quote, booking, payment, ticket, sau M20/M21; không định giá lại booking cũ từ campaign hiện tại.
- [ ] Quote không reserve, booking revalidate/reserve; last-use race, per-user quota, pause/expiry/suspension/rollback đúng.
- [ ] Mọi thanh toán/thu thêm/hoàn tiền trong demo được ghi rõ mô phỏng; không có QR chuyển tiền thật.

### Đặt vé và sau đặt

- [ ] Ghế đúng geometry/chặng/tầng/max 5; draft khác hold; conflict và expiry có hướng khôi phục.
- [ ] Contact-only hay per-passenger được chốt rõ; nếu có tên riêng thì mapping vé/ghế/đổi/hủy được kiểm thử.
- [ ] Vé QR chỉ lấy từ dữ liệu server; VOID/missing QR không dùng lên xe; booking/payment/boarding giữ nghĩa độc lập.
- [ ] Hủy toàn bộ/một phần và đổi ghế/chuyến dễ tìm, có eligibility/quote/xác nhận/history; cutoff/boarding/suspension theo server.
- [ ] Notifications mở được nội dung kể cả mark-read lỗi; hỗ trợ gắn đúng booking; dispute không tự hoàn tiền; review theo eligibility sau chuyến.

### Mobile, accessibility và bằng chứng

- [ ] Ảnh/selector/gallery thao tác được ở mobile; không đẩy ghế hoặc che CTA; hero chữ đọc được; fallback không nhảy layout.
- [ ] Focus/labels/keyboard/Escape/return focus, target thao tác phù hợp, không dựa màu hoặc ảnh để truyền thông tin duy nhất; reduced motion.
- [ ] Empty/loading/error/stale/offline giả lập, expired hold, voucher fail, login/logout/account switch và ownership/role đều có checks phù hợp.
- [ ] Build/test liên quan và browser regression pass; log nêu rõ fixture/live, viewport/browser đã chạy, limitations; không tái sử dụng kết quả M29 như lần chạy M30 mới.

## 13. Phạm vi thay đổi của audit này

Chỉ tạo **`docs/m30-product-experience-audit.md`**. Giữ nguyên báo cáo M30.1. Không sửa frontend/backend/database, không tạo/tải/tìm ảnh, không thao tác dữ liệu, không seed/migration, không đổi branch/identity/remotes, không commit/push/merge. Không đọc/sửa `tatus --short`.

Baseline Git có 2 file untracked: báo cáo M30.1 và `tatus --short`; không có diff tracked/staged. Sau audit dự kiến thêm duy nhất báo cáo M30.1B. Kiểm tra cuối là Git status/diff và nội dung báo cáo; không chạy build/test cho thay đổi tài liệu thuần túy này.
