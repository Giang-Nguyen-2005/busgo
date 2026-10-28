# M29.5 — Final hardening

Ngày nghiệm thu kỹ thuật: **28/10/2026, giờ Việt Nam**.

**M29.5 COMPLETE cho phạm vi hardening và demo local đã kiểm chứng — chờ người dùng nghiệm thu.** Backend thật đã được kiểm thử. Đây không phải chứng nhận production, kiểm thử mọi tổ hợp dữ liệu, hay tuyên bố mọi thao tác đều đã chạy E2E qua UI.

## Baseline và giới hạn thay đổi

- Branch `feature/v2-m29-final-hardening`, từ local `main` / HEAD `0857dc94e592b1b2d1c9b0f9c271ce9e4d535eac`. `git ls-remote` xác nhận main của cả origin và friend cùng commit này. Tracking ref origin/main cục bộ cũ không được dùng làm baseline.
- Đã đọc handoff M29.1–M29.4, M27A và M28, cấu hình backend/test và browser harness hiện có. Không tìm thấy AGENTS.md áp dụng trong repo hoặc các thư mục cha đã kiểm tra.
- Không commit, push, merge, sửa Git identity/remote, sửa migration, thay đổi nghiệp vụ tài chính, reset database, hoặc phát triển M30.
- HTML showcase và các file không liên quan không được chỉnh sửa. `tatus --short` giữ nguyên: 2.945 byte, thời gian sửa 12/10/2026 16:09:36.

## Lỗi xác nhận và sửa

| Mức | Phát hiện | Xử lý và bằng chứng |
| --- | --- | --- |
| P0/P1 | Không phát hiện lỗi sản phẩm ở các luồng đã kiểm chứng | Không suy rộng thành bảo đảm không còn lỗi ở mọi luồng |
| P2 | Kỳ tài chính và lịch sử đối soát in trực tiếp ngày ISO | Dùng formatter ngày Việt Nam có sẵn; giữ nguyên ngày filter, API và số tiền. Ảnh live finance cuối hiển thị `01/10/2026 – 28/10/2026` |
| P2 | Ngày gửi đơn, facts, lịch sử onboarding và hạn lời mời in trực tiếp timestamp | Dùng formatter giờ Việt Nam, nhãn múi giờ và fallback khi thiếu/không hợp lệ. Fixture trong browser timezone Los Angeles xác nhận `2026-10-23T20:00:00Z` thành `24/10/2026 03:00 (Việt Nam)` |
| P2, test harness | CoreDatabaseIT vẫn kỳ vọng V25 và danh sách bảng trước M27A | Kỳ vọng chính xác 26 migration và thêm sáu bảng V26; không bỏ assertion schema |
| P2, test harness | M28MigrationIT lấy bảng của schema hiện tại V26 để sao chép vào V24, tạo SQL lỗi với commission_policies | Lấy chính xác bảng từ schema đích V24 và giới hạn migration lên V25. Vẫn so sánh mọi dòng legacy trước/sau; M27AMigrationIT tiếp tục kiểm tra V25 → V26 |

Không xác nhận lỗi mới ở checkbox/support layout, drawer, loading/error state hoặc overflow trong các viewport đã chạy. Native date input có thể hiển thị theo locale của trình duyệt; các nhãn ngày dạng văn bản dùng giờ/ngày Việt Nam.

## Môi trường backend thật

Tạo mới instance **MySQL 9.2** chỉ bind `127.0.0.1:3329`, datadir `.tools/m29-browser/m29-5/mysql-data`. Không dùng instance/database có sẵn. Hai schema độc lập trong instance mới:

- `busgo_m28_test`: chỉ integration test; tên này đáp ứng allowlist sẵn có của M24BMigrationIT. Đây là schema mới tại cổng 3329, không phải dữ liệu milestone M28 cũ tại 3328.
- `busgo_m29_acceptance`: Flyway **V1–V26**, Hibernate validate, profile `dev,demo`; dữ liệu hư cấu do seeder có sẵn tạo mới. Có bootstrap system admin thử nghiệm, không gửi mail thật.

Spring Boot/JDK 17 tại `127.0.0.1:8099`; React/Vite tại `127.0.0.1:5195`, proxy `/api` tới 8099. Live harness không đăng ký API interception. Các schema upgrade UUID do migration test tạo nằm trên instance mới và được giữ lại; không xóa database. MySQL test không có mật khẩu, chỉ dùng loopback để chạy disposable tests.

Khởi chạy và kiểm thử gặp sandbox Windows chặn Git lock, Vite realpath, socket/JVM; các lệnh tương ứng được chạy lại ngoài hạn chế đó. Đây là lỗi môi trường thực thi, không phải test sản phẩm pass. Log lần đầu được giữ riêng. Migration thực tế là **26**, không phải con số 25 của handoff M28.

## Build và test

Toàn bộ evidence nằm trong `.tools/m29-browser/m29-5/` (ignored, không stage).

| Lớp kiểm thử | Kết quả | Log / report |
| --- | --- | --- |
| Frontend production build cuối | PASS TypeScript + Vite | `frontend-build-final.log` |
| Frontend unit/regression | **164 pass, 0 fail, 0 skip** | `frontend-tests-final.log` |
| Backend Maven unit | **66 pass, 0 fail, 0 skip** | `backend-unit-final.log`; cũng pass trong affected verify |
| Full MySQL integration run | **522 chạy, 520 pass, 1 failure, 1 error** ở hai harness nói trên | `backend-integration-final.log` — full command này **BUILD FAILURE**, không gọi là xanh |
| Chạy lại sau sửa | **47/47 pass**: CoreDatabaseIT 44, M27AMigrationIT 2, M28MigrationIT 1; Maven BUILD SUCCESS | `backend-affected-final.log` |
| Kết quả hợp nhất | Cả **522 ca integration duy nhất** có kết quả pass từ full run và affected rerun; không chạy lại toàn bộ suite lần hai | Hai log integration ở trên |
| CUSTOMER fixtures | 23 nhóm checks; 39 lượt capture/layout trong harness; 0 page/console errors, 0 unmatched API | `fixtures-customer/acceptance.json` |
| OPERATOR fixtures | 12 nhóm checks; 44 lượt layout/capture; 0 page errors | `fixtures-operator/acceptance.json` |
| Platform fixtures | 16 nhóm checks; 28 lượt layout/capture; status passed, 0 page errors | `fixtures-platform/final-results.json` |
| Ngày onboarding | PASS, chuyển ngày theo Việt Nam dù browser ở Los Angeles | `date-results.json`, `fixed-onboarding-dates-390.png` |
| Live frontend/backend | **24 checks pass**, 12 ảnh trọng tâm; 0 page exceptions | `live-results.json`, `live-browser-final.log` |
| Live booking inspector bổ sung | PASS, inventory theo hành trình và fetch booking thật, desktop/mobile | `inspector-result.json`, `live-booking-inspector-{1440,390}.png` |
| Whitespace | `git diff --check` PASS | `diff-check.log` |

Fixture runner giữ nguyên assertion và các viewport 1440/834/390/360 của harness cũ, chỉ đổi origin/output và tắt screenshot để tránh tạo hàng trăm ảnh. Trường `captures` trong các JSON fixture vì vậy là **lượt kiểm tra của harness, không phải số PNG mới**. Có 16 ảnh evidence cuối và một ảnh thất bại locator lịch sử; không dùng `live-failure.png` để nghiệm thu. Các lỗi locator đăng ký và desktop/mobile inspector đã sửa trong harness, không làm yếu assertion sản phẩm.

## Kết quả theo vai trò và luồng

| Luồng | Fixture / unit / integration | Frontend gọi backend thật |
| --- | --- | --- |
| CUSTOMER đăng ký, login, home, tìm chuyến | Validate ngày, autocomplete, filter drafts/URL, sort/pagination/back-forward | Đăng ký/login bằng form; home autocomplete đón/trả và ngày đi gửi search thật |
| Chọn chuyến, ghế và giữ chỗ | Hai tầng, giới hạn chọn ghế, conflict, polling, selection restore, expired hold | Mở trip bằng ID đã đọc từ API, chọn ghế, giữ chỗ rồi checkout thật; không tuyên bố click card search là E2E |
| Giá, voucher và đặt vé | Quote gross/discount/net qua fixture; MySQL M24B và commerce regression kiểm chứng tiền/voucher | Contact → tạo booking thật; không áp voucher trong lượt UI live |
| Payment, ticket, My Bookings | Lỗi/retry, CONFIRMED/CANCELLED không confirm lại, QR VOID/missing không render, trạng thái/pagination | Mock-confirm thật → ticket QR → My Bookings; ba booking hư cấu qua các lần chạy, lần cuối booking #3/trip #7 |
| Hủy, sửa, hoàn tiền mô phỏng, boarding | Các MySQL suite M17/M20/M21/M25 và domain regression; không sửa nghiệp vụ | Không chạy lại toàn bộ thao tác này bằng live UI |
| OPERATOR_ADMIN | Dashboard/dispatch, missing/mixed inventory, hold expiry, floors, modal, retries và guards | Dashboard, Dispatch, bookings, fleet, routes, staff, reports, finance, support tải từ API thật; inspector đọc contact/payment/ticket của booking thật |
| OPERATOR_STAFF | Cùng dispatch scenarios; không fetch dữ liệu quản trị trái quyền | Dashboard, Dispatch, bookings, support; finance và staff API trả 403; direct finance URL bị chặn |
| SYSTEM_ADMIN | Approve/reject/reissue, reveal/hide token, suspend/reactivate, policy activation/reconciliation payload, support decision, audit focus | Dashboard, applications, operators, finance, disputes, audit tải từ API thật. Queue applications/disputes ban đầu trống; không tuyên bố đã approve hay quyết định tranh chấp qua live UI |
| M26/M27A/M28 | MySQL: onboarding 30 + concurrency 6 + suspension 9; finance 12 + concurrency 6 + migration 2; support 26 + concurrency 6 + migration 1 đều pass sau affected recheck | Live finance ghi nhận collection mock từ booking; support case tạo qua API, browser render đúng HTML literal |

BOOKED, PAID và boarding giữ nghĩa độc lập. Missing inventory không trở thành AVAILABLE. Finance source/posted và khoản chưa định giá hoa hồng được tách rõ; live source và ledger cùng phản ánh ba khoản thu mô phỏng, không tự tạo tiền phải trả khi chưa có policy. Support/dispute không phát sinh hoàn tiền hay chỉnh booking.

## Security và session

- Live API: khách không được đọc `/admin/dashboard` hoặc `/operator/trips` (403); guest `/bookings` trả 401; STAFF finance/staff trả 403.
- Fixture router: CUSTOMER/SYSTEM_ADMIN bị chặn Dispatch; STAFF/CUSTOMER direct finance không tạo finance request.
- Harness platform thực hiện logout/login đổi sang identity khác và kiểm tra finance/platform/internal messages biến mất.
- Hai regression mới chạy response success và refresh success bị trì hoãn qua logout/account replacement: cache không nhận dữ liệu private cũ, refresh cũ không khôi phục tài khoản cũ. Test có sẵn kiểm tra late 403 không xóa phiên mới.
- Các assertion ownership/tenant/role, internal-note privacy, idempotency và concurrency nằm trong MySQL suites. Không tuyên bố đã kiểm thử mọi race có thể có hoặc mọi browser.

## Rà soát giao diện và evidence

Desktop **1440px**, mobile **390px** được kiểm tra với các trang live trọng tâm; harness fixture còn chạy 834/360. Các ảnh checkout, ticket, finance, onboarding, Dispatch và booking inspector đã được xem trực tiếp. Không document overflow ở các checks; sơ đồ ghế/bảng rộng cuộn trong vùng riêng. Drawer focus/Escape, filter draft, reduced motion và keyboard finance được kiểm chứng bằng harness có sẵn. Checkout có CTA cố định; ảnh full-page ghi lại vị trí viewport, không phải một bản thiết kế mới.

Xem `.tools/m29-browser/m29-5/evidence-index.md` để chọn ảnh cuối. Không sửa hoặc tái tạo showcase.

## File thay đổi

Production frontend (chỉ presentation):

- `frontend/src/features/finance/Finance.tsx`
- `frontend/src/features/onboarding/Onboarding.tsx`

Test / báo cáo:

- `backend/src/test/java/com/busgo/CoreDatabaseIT.java`
- `backend/src/test/java/com/busgo/M28MigrationIT.java`
- `frontend/tests/m29-5-session.test.mjs`
- `frontend/tests/m29-5-fixture-runner.cjs`
- `frontend/tests/m29-5-live.cjs`
- `frontend/tests/m29-5-dates.cjs`
- `docs/m29-final-hardening.md`

## Tái chạy và trạng thái môi trường

Các service test được giữ chạy để nghiệm thu: frontend 5195, backend 8099, MySQL 3329. Chỉ dùng dữ liệu test của instance này. Script khởi động và PID MySQL nằm trong evidence folder; không chạy trùng process. Account nhà xe demo dùng credentials có sẵn trong README; system admin test được cấu hình trong `start-live.ps1`. Trip demo có ngày đi 29/10/2026; chọn ngày này trên Dispatch, vì dashboard hôm nay 28/10 có thể trống hợp lệ.

Từ repo root:

```powershell
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
node frontend/tests/m29-5-fixture-runner.cjs customer
node frontend/tests/m29-5-fixture-runner.cjs operator
node frontend/tests/m29-5-fixture-runner.cjs platform
node frontend/tests/m29-5-dates.cjs
node frontend/tests/m29-5-live.cjs
```

Live harness chỉ dành cho server M29 test ở cổng cố định trên; mỗi lần chạy tạo customer/booking test mới. Fixture operator tái sử dụng `.tools/m29-browser/m29-3/acceptance.cjs` và `../fixtures.cjs` hiện có trên máy; các file đó ignored, phải giữ nếu tái chạy trên checkout khác. Playwright có thể truyền bằng `PLAYWRIGHT_MODULE` ở các harness hỗ trợ; một số harness cũ dùng đường dẫn runtime Windows sẵn có.

Maven dùng JDK `.tools/jdk17/jdk-17.0.20.1+1`, DB_URL `jdbc:mysql://127.0.0.1:3329/busgo_m28_test?connectionTimeZone=UTC`, DB_USERNAME `root`, password rỗng được truyền rõ bằng `-Dspring.datasource.password=`. Full command: `mvn.cmd -o -f backend/pom.xml verify -Pmysql-integration`. Affected rerun thêm `-Dit.test=CoreDatabaseIT,M28MigrationIT,M27AMigrationIT`.

## Rủi ro còn lại và mức sẵn sàng demo

- **Sẵn sàng demo local có điều kiện**: dùng đúng instance test, ngày chuyến và dữ liệu hư cấu nêu trên; chờ người dùng duyệt giao diện. Không có blocker P0/P1 đã xác nhận trong phạm vi này.
- M22 thanh toán thật vẫn chủ động trì hoãn; không SMTP thật, banking/settlement thật, production deployment hay M30.
- Không có một lượt full MySQL suite xanh sau sửa: dùng full run + affected recheck, được báo cáo tách biệt. Broad run có một log scheduler `ModificationExpiryJob` gặp mock Clock trả null trong test context; không làm fail test và không xuất hiện ở backend live. Chưa sửa production để che lỗi test-double này.
- MySQL 9.2 phát cảnh báo Flyway về phiên bản mới hơn phạm vi đã được Flyway kiểm chứng. Chưa chạy lại trên MySQL 8.4 trong compose.
- Một số thao tác nhạy cảm được xác minh bằng MySQL integration và browser fixtures, không bằng live UI; bảng coverage ở trên là giới hạn nghiệm thu. Queue live trống không chứng minh mọi populated state.
- Chưa kiểm thử tải, Safari/iOS/Firefox, screen reader chuyên dụng, thiết bị cảm ứng thật, mọi session race hay sự cố mạng dài hạn.
- Fixture records, screenshot và logs là local ignored artifacts; cần giữ hoặc sao lưu nếu muốn chia sẻ evidence ngoài máy.

Dừng công việc tại đây để người dùng nghiệm thu; toàn bộ thay đổi chưa commit/stage.
