# BUSGO — M30.1: Audit hình ảnh và nội dung trực quan

Ngày audit: 31/10/2026. Phạm vi: đọc source và xem các ảnh local hiện có; chỉ tạo báo cáo này. Không chạy ứng dụng, gọi API, đọc dữ liệu thật hoặc xác nhận trạng thái dữ liệu đang triển khai. Các nhận xét desktop/mobile dưới đây căn cứ JSX/CSS, chưa phải kết quả kiểm thử trình duyệt mới.

## 1. Kết luận và cách đếm

- Có **7 vị trí logic đáng bổ sung hoặc cải thiện ảnh**: hero; nhận diện nhà xe trong khám phá; ảnh điểm đến trên thẻ tuyến; ảnh trong TripCard dùng chung; ngoại thất ở chi tiết chuyến; nội thất ở chi tiết chuyến; nhận diện ở hồ sơ công khai. Trong đó **4 vị trí chưa render ảnh** (khám phá nhà xe, thẻ tuyến, ngoại thất và nội thất chi tiết chuyến), **3 vị trí đã có cơ chế ảnh** cần nâng chất lượng/nhãn/fallback. Không nhân số vị trí theo số card hay số trang dùng chung TripCard.
- Bộ chuẩn bị ngay: **4 file mới** = **1 banner thay thế + 3 ảnh điểm đến**. Đây là mức tối thiểu đề xuất cho đợt 1, không phải số ảnh bắt buộc để ứng dụng hoạt động. Giữ lại 3 ảnh xe hiện có làm minh họa có nhãn, chưa yêu cầu tìm lại cả bộ xe.
- Đợt 2 tùy chọn: **3 logo nhà xe demo + 1 ảnh nội thất đã xác minh**. Đợt 3 thí điểm tùy chọn: **1 ảnh ngoại thất thật cho một xe đã xác minh**. Tổng danh mục thí điểm nếu làm cả ba đợt: **9 file mới**; không cần chuẩn bị 9 file ngay. Nếu chưa có nguồn ảnh đúng xe, bỏ qua 2 ảnh nội/ngoại thất gắn dữ liệu thật.
- Ảnh không giải quyết được các phần dữ liệu rỗng: giới thiệu nhà xe, chuyến sắp chạy, đánh giá và điều kiện ưu đãi. Không dựng số sao, tiện nghi, voucher hoặc chuyến giả để lấp chỗ.
- M30.2 có thể bắt đầu bằng static assets và thay đổi frontend nhỏ; chưa cần upload service, CDN hay migration.

## 2. Hiện trạng có bằng chứng

Đường dẫn trong báo cáo tính từ gốc repository. Các tên component được ghi để định vị cả khi số dòng thay đổi.

### 2.1. Các ảnh đang tồn tại

Đã mở xem trực tiếp và đọc metadata cả 4 file trong `frontend/public/images/busgo/`. Cả 4 giải mã được thành JPEG; không phát hiện file local hỏng. Điều này không chứng minh URL do backend trả về ở môi trường chạy đều truy cập được.

| File hiện có | Kích thước; dung lượng | Nội dung quan sát | Đường sử dụng và nhận xét |
|---|---|---|---|
| `hero-coach.jpg` | 1365×640; 166.189 byte | Phong cảnh và xe mang phong cách minh họa, màu xanh/vàng, ghim vị trí | `HomePage.tsx:9`, đường dẫn static `/images/busgo/hero-coach.jpg`. Có ảnh rồi; thay bằng ảnh có chất ảnh thật hơn là lựa chọn nâng chất lượng, không phải sửa ảnh thiếu. Không suy đoán nguồn gốc AI hoặc quyền sử dụng từ hình thức ảnh. |
| `bus-standard.jpg` | 800×533; 79.596 byte | Ngoại thất xe khách màu teal, thấy hàng ghế qua kính | `components/ui.tsx:151–161`, fallback TripCard; cũng được seed vào trường ảnh xe. Có thể giữ làm minh họa chung. |
| `bus-sleeper.jpg` | 800×533; 81.094 byte | Ngoại thất xe hai tầng kính, không phải ảnh chụp khoang giường | Seed tham chiếu bằng URL local. Tên file không chứng minh mẫu ghế/giường thực tế. |
| `bus-limousine.jpg` | 800×533; 82.703 byte | Ngoại thất xe khách; không thể xác nhận cấu hình limousine 22 chỗ chỉ từ ảnh | Seed tham chiếu bằng URL local. Chưa thấy nơi frontend chọn trực tiếp file này theo loại xe. |

`DemoDataSeeder.java:442–448` gán ba URL xe vào 7 xe demo thuộc ba nhà xe. Đây là **file static được tham chiếu qua dữ liệu backend**, không phải hai nguồn ảnh loại trừ nhau. Không chạy seeder trong audit.

### 2.2. Audit theo màn hình

| Màn hình/khu vực | Hiện trạng từ code | Kết luận thực tế / hướng M30.2 |
|---|---|---|
| Trang chủ — hero | `pages/HomePage.tsx:6–13`: tiêu đề, ảnh hero, form tìm kiếm, HomeDiscovery, hướng dẫn 3 bước và thông báo thanh toán mô phỏng | Đã có nội dung và ảnh. Ưu tiên 1 banner tốt hơn; giữ bố cục M29 và lời nhắc demo. Hero mang tính thương hiệu, không xác nhận đội xe của nhà xe nào. |
| Trang chủ — khám phá nhà xe | `features/marketplace/Marketplace.tsx`, `HomeDiscovery` dòng 26–31: API trả `PublicOperator.logoUrl`, nhưng card render `BusFront`, tên, Rating, mũi tên | Thiếu nhận diện ảnh trên card dù contract đã hỗ trợ logo. M30.2 có thể render logo nếu có, giữ icon khi thiếu/lỗi. Không cần logo cho tất cả nhà xe để ra đợt 1. |
| Trang chủ — tuyến đang hoạt động | Cùng `HomeDiscovery`: card có `RouteIcon`, tên tuyến, link tìm chuyến theo ID; không có ảnh. Backend `MarketplaceService.routes():41` giới hạn 8 tuyến; `MarketplaceRepository.routes()` trả ID điểm đầu/cuối | Đây là chỗ bổ sung ảnh điểm đến có hiệu quả rõ. Chỉ minh họa thành phố/vùng đến; không gọi ảnh địa danh là ảnh bến xe. Bắt đầu 3 thành phố, tuyến chưa có ảnh vẫn dùng icon. Không thêm tuyến không có trong API. |
| Trang chủ — chuyến sắp khởi hành | `HomeDiscovery` dùng lại TripCard, không có mẫu card riêng | Đã hưởng ảnh xe từ TripCard; không cần một bộ ảnh khác. |
| Tìm chuyến | `pages/SearchPage.tsx:48`, `.map(trip => <TripCard ...>)`; có số kết quả, sort, lọc, chip, sửa hành trình, loading/error/empty | Không thiếu banner riêng. Ảnh nên phục vụ chọn xe trong card, giữ phần lọc bằng icon/control. |
| Card kết quả | `components/ui.tsx:151–161`: ảnh, tên nhà xe/link hồ sơ, rating, giờ đón/trả theo lịch, dự kiến khi trễ, thời lượng, loại xe, tên tuyến, chỗ trống, giá, CTA | Card **không thiếu các thông tin cốt lõi**. Chưa có logo, ảnh nội thất hay biểu tượng tiện nghi. Không thêm Wi-Fi/WC/USB chỉ dựa tên loại xe: `Trip` hiện không có danh sách tiện nghi. Thumbnail desktop nhỏ, cần crop dễ nhận ra xe. |
| Chi tiết chuyến / chọn ghế | `pages/TripPage.tsx`, `TripSelection`: gọi `TripDetail`, hiển thị nhà xe/loại xe, LiveTripBlock, SeatMap, Journey, lịch trình, tóm tắt giá; không render `busImageUrl` dù đã có trong payload | Thiếu ảnh xe để đối chiếu trước khi chọn chỗ. Đề xuất khối ảnh nhỏ có thể thu gọn ở phần thông tin chuyến, tái dùng ảnh card; chưa cần gallery hoặc đổi layout lớn. Nội thất là cơ hội thứ hai, chỉ thêm nếu có ảnh đúng xe/cấu hình. |
| Sơ đồ ghế | `features/trip/SeatMap.tsx`: dữ liệu tầng/hàng/cột, button có `Armchair`, mã ghế, chú giải 3 trạng thái, điều hướng tầng | Dùng icon và CSS là đúng. Không thay sơ đồ tương tác bằng ảnh nội thất hay ảnh sơ đồ ghế, không làm mất tọa độ và trạng thái thật. |
| Checkout / đặt vé | `pages/BookingPage.tsx:27`: liên hệ, giữ chỗ/đếm ngược, Journey, voucher quote, tổng tiền và CTA | Không cần ảnh trang trí. Có thể tái dùng thumbnail sau này nhưng không ưu tiên, không yêu cầu ảnh mới. `Hold` không có ảnh; không thêm API chỉ để trang trí checkout. |
| Thanh toán | `pages/PaymentPage.tsx`, `PaymentPage`: QR Lucide kèm nhãn minh họa, không chuyển tiền; BookingSummary là dữ liệu hành trình/giá | Giữ QR minh họa có nhãn và ShieldCheck. Không chuẩn bị ảnh QR ngân hàng, logo ngân hàng hoặc banner thanh toán. |
| Vé của tôi | `pages/MyBookingsPage.tsx:19`: trạng thái, tuyến, nhà xe, giờ, ghế, giá, CTA; Empty có hướng dẫn theo trạng thái | Không có ảnh xe nhưng đây không phải thiếu sót cần khắc phục ngay. Icon Armchair/trạng thái đủ; `BookingListItem` không có image URL. |
| Chi tiết booking | `pages/BookingDetailPage.tsx:17`: hành trình, vận hành, khách/ghế, liên hệ, giá, đánh giá, hủy/đổi/hỗ trợ | Không cần ảnh mới. Tránh thêm hình khiến thông tin vé và thao tác quan trọng bị đẩy xuống. |
| Vé điện tử | `pages/TicketPage.tsx:13`: `QRCodeSVG` lấy `ticket.qrData`; vé VOID hoặc thiếu QR không render mã lên xe | QR là nội dung sinh từ dữ liệu, không phải asset cần tìm. Không chèn ảnh QR mẫu, ảnh vé hay ảnh khách hàng. |
| Hồ sơ nhà xe công khai | `Marketplace.tsx`, `OperatorProfilePage:33–39`: logo nếu `logoUrl` có giá trị, mô tả hoặc thông báo chưa cập nhật, tuyến, tên loại xe, TripCard, đánh giá | Có sẵn cơ chế logo; ảnh không tải được thì bị ẩn. Thiếu avatar thay thế có kích thước ổn định. Chưa có gallery ngoại/nội thất; không bắt buộc làm gallery ở portfolio. |
| Giới thiệu dịch vụ | `HomePage` có `home-guide`, 3 bước và thẻ “Mọi thông tin trong tầm tay” | Không thiếu section hướng dẫn. Dùng số bước hoặc icon, không cần thêm ảnh nhân viên/tổng đài/ứng dụng giả. |
| Đánh giá | `Marketplace.tsx:18,23–24`, `ReviewCard`: tên khách, sao, tuyến, thời gian, nội dung, phản hồi; không có trường ảnh khách/ảnh review | Không cần chân dung stock. Giữ sao/text; nếu cần nhận diện dùng chữ cái tên hoặc icon. Không tạo người và review giả. |

### 2.3. Fallback và rủi ro trình bày

1. **TripCard có fallback, nhưng chưa đầy đủ:** `illustrative = !trip.busImageUrl || failed`, `onError={() => setFailed(true)}` chuyển sang `bus-standard.jpg`. `presentation.ts:27` có alt “Ảnh xe khách minh họa, không phải ảnh xác nhận của nhà xe”. Không có nhãn minh họa nhìn thấy trên card. Nếu chính file fallback hỏng, code không có bước cuối đổi sang icon. `failed` cũng không reset theo URL trong component khi URL đổi mà instance được giữ lại; cần xử lý ở task frontend sau.
2. **Đường backend không đồng nghĩa ảnh thật:** khi seed cung cấp `/images/busgo/bus-sleeper.jpg`, `illustrative` là false và alt thành “Xe [loại] của [nhà xe]”. Cần nhãn minh họa nhìn thấy và nhận biết các URL minh họa đã biết. Không dựa duy nhất việc URL null hay khác null để xác nhận nguồn ảnh. Đây là việc ưu tiên hơn tìm thêm 3 ảnh ngoại thất chung.
3. **Logo:** `OperatorProfilePage` dùng `onError` đặt `hidden=true`; không có logo thì không render `<img>`. Có thể giữ tên/icon hoặc chữ cái thay thế; không lấy logo nhà xe khác. Việc đổi URL trên cùng phần tử cũng cần kiểm tra khôi phục trạng thái ẩn.
4. **Hero:** không có `onError`; nền teal vẫn do CSS giữ nhưng ảnh lỗi chưa được xử lý chủ động. Ảnh nằm trong vùng `aria-hidden`, alt rỗng phù hợp vai trò trang trí; không đặt thông tin bắt buộc trong ảnh.
5. **Không có ảnh lỗi được xác nhận từ API:** chưa truy cập môi trường chạy, nên không kết luận logo URL thực tế đang hỏng hay database thiếu ảnh. Kết luận “chưa có ảnh” chỉ áp dụng vị trí JSX không render ảnh hoặc schema chưa có trường.

### 2.4. Desktop/mobile và phần quản trị

- `features/customer/customer.css:72–75`: thumbnail desktop 78×72, `object-fit: cover`. Ảnh xe 3:2 hiện có bị crop gần vuông; cần để thân/đầu xe trong vùng an toàn, không nhúng chữ vào ảnh.
- Cùng file dòng 154–164: **ẩn thumbnail ở chiều rộng ≤1100px**, không chỉ điện thoại. Do đó thay file ảnh card đơn thuần chưa cải thiện card trên tablet/mobile. M30.2 cần quyết định một thumbnail nhỏ hoặc nút “Xem ảnh xe” ở chi tiết chuyến; không buộc hiện ảnh lớn trên mọi card.
- Dòng 165–173: hero mobile dùng cùng ảnh, full width nhưng opacity `.18`; chủ thể ảnh sẽ nhạt theo thiết kế M29. Không yêu cầu thêm banner mobile ngay. Kiểm tra crop, độ tương phản và vùng chữ ở cả hai cỡ.
- Checkout mobile giữ form → summary → confirm; thanh tổng tiền/CTA sticky. Chọn ghế có summary sticky và giới hạn chiều cao. Không thêm ảnh vào thanh sticky; đặt ảnh thông tin chuyến bên ngoài vùng thao tác ghế.
- `marketplace.css`, `.market-grid`: grid tự co theo chiều rộng, `.market-profile-heading img` 72×72, `object-fit: contain`. Logo nên vuông, có khoảng thở, giữ cùng khung khi fallback.
- Đã kiểm tra `OperatorHomePage.tsx` (dashboard điều hành), `OperatorDispatchPage.tsx` (bảng điều phối), `OperatorBusesPages.tsx` (đội xe/form), `features/operator/FleetMaintenance.tsx` (workspace đội xe), `pages/admin/AdminPages.tsx` (bảng nhà xe/nhân sự), và route dashboard System Admin trong `routes/router.tsx`. Các màn hình này tập trung KPI, trạng thái, bảng và thao tác; **không đề xuất ảnh trang trí**. Operator chỉ cần quản lý URL logo/ảnh nghiệp vụ khi có nhu cầu thật. Logo BusGo hiện dùng Lucide và chữ trong `CustomerLayout.tsx`; chưa cần làm lại logo thương hiệu.

## 3. Danh mục ảnh cần tự chuẩn bị

P1: bộ 4 file mới đợt 1. P2: tùy chọn sau bộ chính. P3: ảnh thí điểm gắn dữ liệu thật, không phải yêu cầu tìm ngay. “Demo” ở tên logo biểu thị bộ nhận diện hư cấu cho dữ liệu demo, không xác nhận là logo doanh nghiệp thật trùng tên.

| STT | Màn hình/khu vực | Loại ảnh cần có | Mục đích sử dụng | Số lượng tối thiểu | Tỷ lệ | Kích thước khuyến nghị (px) | Định dạng | Tên file đề xuất | Ưu tiên | Dùng lại | File/component frontend dự kiến |
|---|---|---|---|---:|---|---|---|---|---|---|---|
| 1 | Hero trang chủ | Xe khách trên cung đường, không logo nhà xe cụ thể | Nâng cảm giác nền tảng du lịch/đặt xe thực tế; thay banner đang có | 1 | 2:1 | 1920×960 | WebP | `hero-coach.webp` | P1 | Chỉ hero; chưa cần banner khác | `pages/HomePage.tsx`, `.hero-art` trong `features/customer/customer.css` |
| 2 | Thẻ tuyến đến Đà Lạt | Cảnh thật nhận diện Đà Lạt | Điểm nhấn khám phá, minh họa điểm đến | 1 | 4:3 | 1200×900 | WebP | `destination-da-lat.webp` | P1 | Các tuyến cùng điểm đến; không dùng làm ảnh bến xe | `features/marketplace/Marketplace.tsx` / `HomeDiscovery` |
| 3 | Thẻ tuyến đến Nha Trang | Cảnh biển/thành phố Nha Trang | Nhận diện điểm đến | 1 | 4:3 | 1200×900 | WebP | `destination-nha-trang.webp` | P1 | Các tuyến cùng điểm đến | `Marketplace.tsx` / `HomeDiscovery` |
| 4 | Thẻ tuyến đến TP.HCM | Cảnh thành phố TP.HCM | Dùng cho chiều về thay vì thêm ảnh từng tuyến | 1 | 4:3 | 1200×900 | WebP | `destination-ho-chi-minh.webp` | P1 | Tuyến từ Đà Lạt/Nha Trang về TP.HCM nếu API trả về | `Marketplace.tsx` / `HomeDiscovery` |
| 5 | Khám phá + hồ sơ nhà xe demo | Logo bộ nhận diện demo An Phú Express | Phân biệt nhà xe demo | 1 nếu làm P2 | 1:1 | 512×512 | PNG trong suốt | `operator-demo-an-phu.png` | P2 | Card khám phá và hồ sơ | `Marketplace.tsx` / `HomeDiscovery`, `OperatorProfilePage`, `ProfileForm` |
| 6 | Khám phá + hồ sơ nhà xe demo | Logo bộ nhận diện demo Minh Thành Limousine | Như trên | 1 nếu làm P2 | 1:1 | 512×512 | PNG trong suốt | `operator-demo-minh-thanh.png` | P2 | Như trên | Cùng các component dòng 5 |
| 7 | Khám phá + hồ sơ nhà xe demo | Logo bộ nhận diện demo Tây Nguyên Travel | Như trên | 1 nếu làm P2 | 1:1 | 512×512 | PNG trong suốt | `operator-demo-tay-nguyen.png` | P2 | Như trên | Cùng các component dòng 5 |
| 8 | Chi tiết chuyến — nội thất | Ảnh khoang của **một xe đã xác minh**, gắn đúng xe/cấu hình | Cho khách biết không gian ghế/giường thật; thí điểm 1 ảnh, chưa làm đủ 3 loại | 1 nếu có dữ liệu đúng; nếu không: 0 | 4:3 | 1200×900 | WebP | `bus-verified-01-interior.webp` | P2 có điều kiện | Chỉ các chuyến cùng xe/cấu hình được xác minh | `pages/TripPage.tsx` / `TripSelection`; manifest demo giới hạn hoặc gallery contract sau này |
| 9 | Card + chi tiết chuyến — ngoại thất thật | Ngoại thất của đúng xe đã xác minh ở dòng 8 | Thay minh họa bằng ảnh xe thật cho một trường hợp thí điểm | 1 nếu làm P3; nếu không: 0 | 3:2 | 1200×800 | WebP | `bus-verified-01-exterior.webp` | P3 có điều kiện | TripCard ở search/home/profile và TripPage của đúng xe | `components/ui.tsx` / `TripCard`, `pages/TripPage.tsx` |

Ba điểm đến P1 có căn cứ trong `DemoDataSeeder.seedLocations()` và `seedRoutes():324–335`: HCM↔Đà Lạt, HCM↔Nha Trang. **Không cam kết chúng đang xuất hiện ở mọi môi trường**: discovery chỉ trả tối đa 8 tuyến theo ID. M30.2 phải kiểm tra response trước khi gắn ảnh; không sửa seed/giả lập tuyến để ép dùng đủ 3 file. Với Buôn Ma Thuột, Đà Nẵng, Huế hay tuyến khác chưa có ảnh, giữ icon ở đợt đầu; không lấy ảnh Đà Lạt thay thế.

### 3.1. Ảnh được dùng lại, không cần chuẩn bị mới

- `bus-standard.jpg`, `bus-sleeper.jpg`, `bus-limousine.jpg`: giữ nguyên file đợt 1, không bắt người dùng tìm 3 ảnh mới chỉ để đổi định dạng. Tái dùng ở TripCard và khu vực ảnh chi tiết có nhãn “Ảnh minh họa loại xe”; không dùng làm bằng chứng tiện nghi hoặc đội xe thật.
- Nếu chưa có mapping loại xe đủ tin cậy, giữ fallback chung hoặc icon, không phân loại bằng suy đoán từ tên tiếng Việt. Mapping demo dùng ID đã kiểm tra trong môi trường hoặc cấu hình rõ ràng, không hardcode ID đoán từ thứ tự seed.
- Một ảnh thật đã được xác minh có thể dùng cho nhiều chuyến của **cùng xe**. Không gán rộng cho mọi xe cùng nhà xe hoặc cùng busType nếu chưa xác nhận tương đương.
- Không cần file riêng cho voucher, review, empty state, ghế, checkout, thanh toán, vé và dashboard quản trị.

## 4. Phân biệt ảnh thực tế và minh họa

| Nhóm | Có thể chuẩn bị | Điều kiện sử dụng |
|---|---|---|
| A — Tự tìm với quyền phù hợp | 1 hero không nhận diện nhà xe; 3 cảnh điểm đến đúng địa danh; ảnh xe chung nếu muốn thay bộ minh họa sau này | Người dùng tự tìm/cung cấp. Lưu nguồn, tác giả, điều kiện sử dụng và yêu cầu ghi công. Ảnh cảnh thành phố là minh họa hành trình, không xác nhận điểm đón/trả cụ thể. Tìm được trên mạng không tự động có quyền tái sử dụng. |
| B — Tự tạo bằng AI | Hero mang tính cảm hứng; bộ 3 logo **demo hư cấu**; ảnh xe chung có nhãn minh họa ngoài ngữ cảnh xác nhận nhà xe | Tránh logo/biển số/thương hiệu thật; kiểm tra bánh xe, cửa, ghế, chữ sai. Hero AI có thể hợp màu nhưng ít chất ảnh thật hơn mục tiêu lần này. Không ưu tiên AI cho cảnh điểm đến vì dễ sai địa danh. Không dùng logo AI thay logo chính thức của doanh nghiệp thật. Audit này không tạo ảnh. |
| C — Phải đúng dữ liệu | Logo chính thức nếu là nhà xe thật; ngoại thất/nội thất hiển thị như xe của chuyến; hình tiện nghi, bến/điểm đón nếu tuyên bố đó là địa điểm thật | Cần quyền dùng và đối chiếu nhà xe, xe, cấu hình, thời điểm. Ảnh C có thể do nhà xe cung cấp hoặc lấy từ nguồn cho phép, nhưng không được thay bằng AI/stock rồi gọi là ảnh thật. Thiếu ảnh thì ghi chưa có ảnh xác nhận hoặc dùng minh họa có nhãn. |

Nhóm A/B/C mô tả **cách dùng và mức xác thực**, không chỉ nơi tải ảnh: cùng ảnh xe stock có thể hợp lệ cho hero nhưng không hợp lệ cho gallery “xe của chuyến này”. Không xác nhận quyền của 4 file hiện tại vì source chưa cho thấy bằng chứng giấy phép đi kèm.

## 5. Khả năng tích hợp frontend/API/database

| Dữ liệu | Source đã kiểm tra | Đang lưu / hiển thị | Cần làm sau này |
|---|---|---|---|
| Logo nhà xe | `operator/entity/TransportOperator.java:17–18`, migration `V21__marketplace_reviews.sql`; `marketplace/MarketplaceDtos.java:14–19`, `MarketplaceRepository`, `MarketplaceService:96` | DB `transport_operators.logo_url`, tối đa 500 ký tự. PublicOperator có `logoUrl`; public profile dùng `<img>`. Operator `ProfileForm` nhận URL HTTPS hoặc đường dẫn nội bộ, PUT `/operator/marketplace-profile` đã tồn tại | Render logo ở discovery và làm fallback chỉ cần frontend. Sau khi có ảnh, gán URL qua luồng hồ sơ sẵn có trên môi trường demo được phép; đó là cập nhật dữ liệu, **chưa thực hiện M30.1**. Không cần schema/API mới cho một logo. |
| Ảnh xe | `fleet/entity/Bus.java:27–28`, `V9__add_bus_image_url.sql`, `trip/search/TripSearchDtos.java:25–35`, `TripSearchRepository:20,68`, `TripSearchService.detail()` | DB `buses.image_url`, nullable, tối đa 500 ký tự; search và detail trả `busImageUrl`. TripCard hiển thị, TripPage chưa hiển thị. DB lưu chuỗi URL chứ không lưu binary; seed dùng đường dẫn static | Render ảnh sẵn có ở chi tiết chỉ cần frontend. Nhưng quản trị ảnh qua form đội xe cần mở rộng API/DTO frontend: `FleetDtos.java:21–33`, `types/operator.ts`, `OperatorBusesPages.BusForm` **chưa nhận/trả imageUrl**. Không cần thêm cột DB chỉ để sửa URL ảnh đơn hiện có. |
| Ảnh loại xe / nội thất / gallery | `fleet/entity/BusType.java`, `FleetDtos.BusTypeResponse`, `TripSearchDtos.BusTypeSummary`, `types/customer.ts` | BusType có tên, số ghế, mô tả; không có image URL/gallery/amenities trong contract đã kiểm tra. Một `Bus.imageUrl` chưa đủ biểu diễn ảnh ngoại thất + nhiều ảnh nội thất và xác thực | Demo nhỏ có thể dùng manifest static được đối chiếu, với nhãn minh họa và phạm vi mapping rõ. Quản lý gallery thật theo từng bus/loại xe, thứ tự, chú thích, trạng thái xác minh cần thiết kế DTO/API và nơi lưu nhiều ảnh (schema mới hoặc cấu trúc tương đương) ở task riêng. |
| Ảnh điểm đến | `location/entity/Location.java`, `LocationDtos.java:6`; `MarketplaceDtos.Route:18`, `MarketplaceRepository.routes()` | Location có địa danh/địa chỉ/tọa độ/trạng thái, không có ảnh; public discovery route có ID điểm đầu/cuối, không có ảnh, không có province trong DTO tuyến | Đợt 1 dùng manifest local ánh xạ ID điểm đến đã kiểm tra sang file; không parse tên tùy ý hoặc giả định ID ổn định giữa môi trường. Chưa cần migration. CMS/CRUD ảnh địa danh động mới cần backend contract và lưu dữ liệu ảnh. |
| Ảnh trong booking/vé | `types/customer.ts`: Hold, Booking, BookingListItem, TicketBundle; `BookingSummary.tsx`, các page booking/ticket | Không có image URL trong các kiểu này; QR vé được sinh bằng SVG từ `qrData` | Giữ nguyên đợt đầu. Không mở rộng contract booking hay snapshot ảnh chỉ để trang trí. |

Các đường API ở bảng trên là path tương đối trong frontend; backend dùng prefix `/api/v1`. URL bắt đầu `/images/...` được browser tải từ origin frontend; phải có file trong `frontend/public/images/...` khi build/deploy. Backend trả URL không đồng nghĩa backend phục vụ bytes ảnh. Nếu về sau tách domain phải kiểm tra đường dẫn tương đối và host ảnh; không cần thêm hạ tầng cho demo hiện tại.

**Phân ranh phạm vi:**

- **Chỉ frontend/static:** thay hero; thêm ảnh vào card tuyến hiện hữu với manifest có kiểm tra; dùng logo từ payload; nhãn minh họa nhìn thấy; fallback icon; tận dụng `busImageUrl` ở detail; kiểm tra crop/responsive. Manifest mới là đề xuất M30.2, hiện chưa được tạo.
- **Thực sự cần backend/API nếu muốn tính năng đó:** operator sửa ảnh xe qua API hiện tại; quản trị gallery/nội thất/tiện nghi thật; quản lý ảnh địa danh động; endpoint ưu đãi công khai có lọc điều kiện. Trường URL ảnh xe đơn và logo đã có DB, không lập migration trùng.
- **Chưa cần ở portfolio/demo:** upload service, object storage/CDN, pipeline tự resize, DAM/CMS, tìm kiếm ảnh tự động, ảnh cho mọi dòng bảng, gallery nhiều góc của toàn bộ đội xe, ảnh lưu theo từng vé, triển khai M22/M27B.

## 6. Những nội dung trực quan cần task riêng

| Phát hiện có bằng chứng | Ảnh có giải quyết được không? | Task sau đề xuất |
|---|---|---|
| Voucher đã có: `BookingPage` chỉ cho nhập mã/kiểm tra; `features/operator/Vouchers.tsx` quản lý mã qua `/operator/vouchers`; `router.tsx` không có route ưu đãi cho khách, `CustomerLayout` không có link ưu đãi; HomeDiscovery/profile/card không giới thiệu mã | Không. Thiếu khả năng khám phá ưu đãi, không phải thiếu banner khuyến mãi | Task riêng về khám phá ưu đãi. Có thể bắt đầu bằng hướng dẫn dùng mã được cung cấp; danh sách voucher hợp lệ theo nhà xe/tuyến cần contract public và điều kiện thời hạn/đối tượng. Không công khai API operator, không vẽ mã/giảm giá giả lên banner. |
| Card có thông tin hành trình/giá/rating nhưng chưa có tiện nghi hoặc nút xem ảnh; thumbnail ẩn ở ≤1100px | Một phần | M30.2 ảnh/fallback và cách mở ảnh mobile; tiện nghi thật tách task dữ liệu. Dùng Lucide kèm nhãn cho tiện nghi chỉ khi có trường dữ liệu xác nhận. |
| Trang chủ đã có 4 section discovery, form và hướng dẫn; các section phụ thuộc API | Không thể kết luận thiếu nội dung từ một trạng thái dữ liệu giả định | Cải thiện phân cấp/ảnh trong section sẵn có. Nếu môi trường demo rỗng, chuẩn bị nội dung demo trung thực trong task dữ liệu riêng; không thêm section marketing dày đặc. |
| Profile có chuỗi “Nhà xe chưa cập nhật giới thiệu công khai.”, “Chưa có tuyến hoạt động.”, “Chưa có loại xe khả dụng.” | Logo chỉ giúp nhận diện, không thay lời giới thiệu | Task nội dung: mô tả ngắn nguồn gốc demo/thật, phạm vi phục vụ từ dữ liệu đã xác nhận. Không suy đoán thiếu mô tả trong DB; source chỉ chứng minh có fallback. |
| `Empty` trong `components/ui.tsx` dùng chung BusFront; search/my-bookings có CTA theo ngữ cảnh; discovery có text + CTA nhưng profile một số trạng thái chỉ có `<p>` | Không cần ảnh minh họa riêng | Task nhỏ thống nhất icon/phân cấp/thông điệp và hành động phù hợp. Không gọi các empty state đang có là “chưa làm”. |
| Review không có avatar/ảnh; thông tin thật gồm tên, sao, tuyến, thời gian, phản hồi | Không cần ảnh chân dung | Giữ cấu trúc; dùng icon/chữ cái nếu cần. Không thêm ảnh stock làm khách đã đi. |

## 7. Quy cách ảnh và cấu trúc thư mục dự kiến

Chỉ là cấu trúc đề xuất; chưa tạo thư mục hay ảnh trong M30.1.

```text
frontend/public/images/busgo/
  hero-coach.jpg                         # hiện có, chưa xóa ở audit
  bus-standard.jpg                       # hiện có; minh họa
  bus-sleeper.jpg                        # hiện có; minh họa
  bus-limousine.jpg                      # hiện có; minh họa
  hero-coach.webp                        # P1 mới, đổi tham chiếu ở M30.2
  destinations/
    destination-da-lat.webp              # P1
    destination-nha-trang.webp           # P1
    destination-ho-chi-minh.webp         # P1
  operators/
    operator-demo-an-phu.png             # P2 tùy chọn
    operator-demo-minh-thanh.png          # P2 tùy chọn
    operator-demo-tay-nguyen.png          # P2 tùy chọn
  buses/
    bus-verified-01-interior.webp         # P2 nếu đúng dữ liệu
    bus-verified-01-exterior.webp         # P3 nếu đúng dữ liệu
```

- Tên ASCII, chữ thường, gạch nối. `verified-01` là mã thí điểm của bộ ảnh; phải có bảng đối chiếu xe thật/demo trước khi tích hợp, không phải ID được suy đoán. Khi triển khai nhiều xe, dùng mã asset ổn định có mapping rõ.
- Người dùng có thể gửi bản gốc JPEG/PNG chất lượng tốt; tên/định dạng bảng là đầu ra tối ưu web dự kiến. Không cần tự xuất cả JPEG lẫn WebP cho mỗi ảnh. Không phóng ảnh nhỏ chỉ để đạt số pixel.
- Hero mục tiêu ≤350 KB, ảnh điểm đến/nội/ngoại thất ≤180 KB mỗi file, logo PNG ≤80 KB. Đây là ngân sách khuyến nghị, ưu tiên giữ chi tiết tốt; chất lượng WebP khoảng 75–85 để bắt đầu kiểm tra bằng mắt.
- Hero không nhúng slogan/chữ/giá/CTA; chủ thể xe lệch phải, có nền thoáng, kiểm tra crop ở vùng ảnh desktop 49% với `object-position: 62% center`. Tránh nền quá rối; mobile còn lớp làm mờ theo M29.
- Ảnh điểm đến: đúng địa danh, trời/ánh sáng nhất quán, chủ thể chính ở vùng giữa để crop; không cần panorama rộng hoặc ảnh riêng cho từng chiều tuyến. Ba ảnh đủ cho phạm vi P1, các địa danh khác giữ icon.
- Ảnh nội thất: đủ sáng, thấy cấu hình thực, không bóp rộng méo khoang, không thêm màn hình/USB/WC không có. Ảnh ngoại thất: góc 3/4, xe rõ, có khoảng thở để crop 78×72. Không tự chỉnh làm sai nhận diện xe.
- Logo: nền trong suốt hoặc sạch, khoảng thở khoảng 10–15%, đọc được ở 40–72 px; không kéo méo hoặc crop logo bằng `cover`. Nếu có SVG chính thức tin cậy có thể dùng thay PNG, không bắt buộc tạo thêm file.
- Hợp màu tokens M29 (`design-tokens.css`): teal `#0f766e`, nền cấu trúc `#102f2e`, nền sáng `#f6f8f7`, nhấn ấm `#d6a76b`. Chọn ảnh tự nhiên phù hợp; không nhuộm màu logo thật hoặc sửa ảnh đến mức làm sai sản phẩm.
- Khi cung cấp ảnh, kèm nguồn/quyền, địa danh hoặc nhà xe/xe áp dụng, ảnh thật hay minh họa và chú thích. Chỉ làm mờ biển số/khuôn mặt khi cần, không dùng ảnh có watermark hoặc thông tin cá nhân không cần thiết. Không kết luận giấy phép chỉ từ tên nguồn.

## 8. Thứ tự M30.2 sau khi người dùng cung cấp ảnh

### Đợt 1 — Ít file, hiệu quả rõ: 4 file mới, tái dùng 3 file xe

1. Kiểm tra 4 file P1: quyền dùng, địa danh, độ nét, dung lượng, tên. Thay hero bằng `hero-coach.webp`; giữ style và layout M29.
2. Gắn ba ảnh điểm đến vào **thẻ tuyến hiện hữu**, chỉ khi ID điểm đến đã được đối chiếu với response môi trường demo. Các thẻ khác giữ icon; không thêm “tuyến phổ biến” hay giá khởi điểm không có dữ liệu.
3. Sửa phân biệt ảnh minh họa/ảnh xác nhận trong TripCard, thêm nhãn nhìn thấy, có icon cuối khi ảnh fallback lỗi. Ưu tiên này không cần file mới.
4. Thêm khu vực ảnh nhỏ/thu gọn ở chi tiết chuyến, tái dùng ảnh đã có từ API; minh họa phải có nhãn. Cho mobile cách xem ảnh nhưng không đẩy sơ đồ ghế khỏi phần chính hoặc thay thanh CTA sticky.
5. Kiểm tra desktop 1440px, tablet 834/1100px và mobile 390/360px; đặc biệt điểm chuyển 1100px và 760px. Kiểm tra URL rỗng, URL lỗi, fallback local lỗi, logo lỗi, đổi chuyến/đổi URL, không tràn ngang, alt, CLS, thời gian tải. Chỉ hero cần tải sớm; ảnh bên dưới lazy-load và khai báo kích thước/tỷ lệ.

### Đợt 2 — Bổ sung có chọn lọc: tối đa 4 file mới

1. Ba logo demo để dùng chung discovery/profile. Xác nhận tên và bộ nhận diện là demo; nếu dữ liệu thực là doanh nghiệp thật thì dùng logo đúng doanh nghiệp, không dùng bộ demo. Có thể render logo từ payload trước, chưa cần chuẩn bị đủ cả ba.
2. Một ảnh nội thất cho xe thí điểm nếu đã có nguồn xác minh. Với demo thuần minh họa, chỉ dùng trong khối ghi rõ minh họa, không đưa vào phần cam kết xe/tiện nghi thật. Không mở rộng gallery cả đội xe chỉ để sử dụng ảnh vừa tìm.
3. Các task không cần ảnh: fallback logo, empty state hồ sơ, lời giới thiệu trung thực; nội dung ưu đãi được xử lý riêng, không gộp nghiệp vụ vào đợt ảnh.

### Đợt 3 — Dữ liệu ảnh thật và quản trị

1. Một ảnh ngoại thất thật cho xe thí điểm, gắn đúng `buses.image_url` sau khi được phép thay dữ liệu demo; tái dùng trên card và chi tiết. Nhu cầu đổi URL qua giao diện đội xe cần mở rộng DTO/API đã nêu, không cần cột ảnh đơn mới.
2. Khi thật sự có nhiều ảnh đúng xe: thiết kế gallery/API, mapping bus/trip, caption và thông tin xác minh; lúc đó mới đánh giá thay đổi DB. Một manifest frontend cố định không thay thế dữ liệu vận hành cập nhật.
3. Ảnh điểm đến được quản lý động, tiện nghi chuẩn hóa và khám phá voucher có contract là các task độc lập. Chưa làm upload/CDN nếu static assets đáp ứng portfolio.

## 9. Checklist tiếp nhận và kiểm chứng

- [ ] Thu đủ 4 file P1 theo tên ở mục cuối; nguồn/quyền được ghi lại.
- [ ] Hero không nhận diện sai nhà xe, không nhúng chữ; ba địa danh đúng thực tế.
- [ ] Chốt các ID điểm đến từ API demo khi tích hợp; không dùng thứ tự seed để đoán ID.
- [ ] Giữ 3 file xe hiện có; phân loại minh họa rõ cả khi URL được trả từ backend.
- [ ] Logo P2 đúng nhà xe hoặc đúng bộ nhận diện demo; có fallback khi thiếu/lỗi.
- [ ] Ảnh nội/ngoại thất tùy chọn có bảng đối chiếu xe/cấu hình và quyền sử dụng.
- [ ] Kiểm tra crop/responsive, ảnh lỗi, nhãn minh họa nhìn thấy, alt và kích thước ảnh.
- [ ] Không thêm tiện nghi, sao, review, mã voucher hay tuyên bố ảnh thật thiếu căn cứ.
- [ ] Khi triển khai M30.2 mới chạy build/kiểm tra UI phù hợp; không thay đổi giữ ghế, giá, QR, đặt vé hay thanh toán.

## 10. Xác nhận phạm vi M30.1

Chỉ tạo `docs/m30-1-visual-assets-audit.md`. Không sửa frontend/backend/database; không chạy migration/seeder, không tìm/tải/tạo ảnh, không thêm URL placeholder, không triển khai M22/M27B, không sửa design system M29, không commit/push/merge hoặc thay Git config/remote/branch. Không đọc/sửa file untracked `tatus --short`. Không chạy build/test vì chỉ có báo cáo Markdown; kiểm tra Git và whitespace để xác nhận phạm vi thay đổi. Git đầu vào không có thay đổi tracked và có duy nhất untracked `tatus --short`; đầu ra dự kiến thêm duy nhất báo cáo này.

## DANH SÁCH ẢNH CẦN CHUẨN BỊ NGAY

**4 file mới:**

- [ ] **1 banner xe khách/cung đường**, 1920×960 — `hero-coach.webp`.
- [ ] **1 ảnh Đà Lạt**, 1200×900 — `destination-da-lat.webp`.
- [ ] **1 ảnh Nha Trang**, 1200×900 — `destination-nha-trang.webp`.
- [ ] **1 ảnh TP.HCM**, 1200×900 — `destination-ho-chi-minh.webp`.

Giữ lại 3 ảnh xe hiện có, chưa cần tìm lại. Logo và nội thất để đợt 2; ảnh xe thật chỉ chuẩn bị khi có thể đối chiếu đúng xe. Có thể cung cấp bản gốc JPEG/PNG cùng nguồn và quyền sử dụng để tối ưu thành WebP ở M30.2.
