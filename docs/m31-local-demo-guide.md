# Chạy demo M31 trên máy local

Hướng dẫn dùng lại **fixture đã nạp đủ M31.5** tại `C:\Users\Giang\busgo`. Lịch demo từ **14 đến 27/11/2026**, giờ Việt Nam. Đây là MySQL test đã có dữ liệu. Launcher ứng dụng thông thường trong README dùng một database khác.

| Thành phần | Giá trị |
| --- | --- |
| Frontend | `http://127.0.0.1:5186` |
| Backend | `http://127.0.0.1:8186/api/v1` |
| MySQL | `127.0.0.1:3352`, schema `busgo_m312_test`, user `busgo` |
| Container / project | `busgo-m312-catalogue-mysql-1` / `busgo-m312-catalogue` |
| Volume | `busgo-m312-catalogue_m312_data` tại `/var/lib/mysql` |

## Khởi động trên workspace đã bàn giao

Mở Docker Desktop và PowerShell. Cần bộ JDK 17, JAR backend, Node, dependencies frontend và cấu hình riêng hiện có. Nếu container, volume hoặc cấu hình tài khoản bị thiếu, dừng để khôi phục fixture tương ứng. Khởi tạo database trống sẽ không cung cấp bộ lịch này.

```powershell
Set-Location C:\Users\Giang\busgo
powershell.exe -NoProfile -File .\.tools\m31-6\Start.ps1
Invoke-RestMethod http://127.0.0.1:8186/api/v1/health
Invoke-RestMethod http://127.0.0.1:5186/api/v1/health
```

Đợi hai health check trả về `UP` rồi mở frontend. Script kiểm tra project, volume, cổng MySQL, 28 migration thành công (bao gồm V28) và 385 chuyến trước khi chạy backend. Log nằm trong `.tools/m31-6/`. Script và runtime config là tài nguyên **local ignored**. Trên máy khác, cần bàn giao volume và cấu hình qua kênh riêng.

## Chạy từng dịch vụ

Nếu chỉ cần MySQL, dùng PowerShell từ thư mục repo:

```powershell
$container = 'busgo-m312-catalogue-mysql-1'
$db = docker inspect $container | ConvertFrom-Json
if (!$db -or $db.Config.Labels.'com.docker.compose.project' -ne 'busgo-m312-catalogue' -or
    !($db.Mounts | Where-Object { $_.Name -eq 'busgo-m312-catalogue_m312_data' -and $_.Destination -eq '/var/lib/mysql' }) -or
    $db.HostConfig.PortBindings.'3306/tcp'[0].HostIp -ne '127.0.0.1' -or
    $db.HostConfig.PortBindings.'3306/tcp'[0].HostPort -ne '3352') { throw 'Sai fixture' }
docker start $container
# Đợi MySQL sẵn sàng rồi kiểm tra schema và số chuyến.
$baseline = 'SELECT CONCAT(DATABASE(),CHAR(124),COUNT(*)) FROM trips;' |
  docker exec -i $container sh -c 'MYSQL_PWD=$MYSQL_PASSWORD exec mysql -N -u$MYSQL_USER $MYSQL_DATABASE'
if ($baseline -ne 'busgo_m312_test|385') { throw 'Kiểm tra lại fixture trước khi mở backend' }
```

Để chạy frontend và backend riêng, mở hai terminal ở repo sau khi xác minh MySQL:

```powershell
# Terminal backend
powershell.exe -NoProfile -File .\.tools\m31-6\Run.ps1 -Service backend
```

```powershell
# Terminal frontend
powershell.exe -NoProfile -File .\.tools\m31-6\Run.ps1 -Service frontend
```

Backend dùng profile `dev`, JVM chạy JAR trên loopback 8186 với `--spring.flyway.enabled=false`. Không bật profile `demo`, runner import, bootstrap admin hoặc gửi mail. Job nền được trì hoãn 168 giờ cho buổi demo ngắn. Frontend là Vite dev mode `m31-local`, `API_PROXY_TARGET=http://127.0.0.1:8186`, `VITE_API_BASE_URL=/api/v1` và `VITE_DEMO_MEDIA=true`. Nếu JAR cần build lại, dùng JDK 17 và `mvn.cmd -o -f backend/pom.xml package -DskipTests` với Maven cache của workspace.

Nếu chỉ có volume và file cấu hình riêng, có thể thay hai lệnh `Run.ps1` bằng các lệnh foreground dưới đây. Xác minh container/schema như trên trước, đồng thời kiểm tra `SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;` trả 28 và entry version 28 có success=1.

```powershell
# Terminal backend, tại thư mục repo
$localConfig = Get-Content .tools/m31-6/runtime.local.json -Raw | ConvertFrom-Json
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3352/busgo_m312_test?connectionTimeZone=UTC'
$env:DB_USERNAME = 'busgo'
$env:DB_PASSWORD = $localConfig.databasePassword
$env:JWT_SECRET = $localConfig.jwtSecret
$env:MAIL_ENABLED = 'false'
$env:BUSGO_SYSTEM_ADMIN_BOOTSTRAP_ENABLED = 'false'
& '.tools/jdk17/jdk-17.0.20.1+1/bin/java.exe' '-Dfile.encoding=UTF-8' -jar backend/target/busgo-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8186 --spring.profiles.active=dev --spring.flyway.enabled=false --busgo.notifications.initial-delay=PT168H --busgo.booking.seat-hold-cleanup-initial-delay=PT168H --busgo.booking.payment-expiry-initial-delay=PT168H --busgo.booking.modification-expiry-initial-delay=PT168H
```

```powershell
# Terminal frontend, tại thư mục repo
Set-Location frontend
$env:API_PROXY_TARGET = 'http://127.0.0.1:8186'
$env:VITE_API_BASE_URL = '/api/v1'
$env:VITE_DEMO_MEDIA = 'true'
node.exe node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5186 --strictPort --mode m31-local
```

## Đăng nhập

| Vai trò | Tài khoản fixture |
| --- | --- |
| Quản trị An Phú Express | `operator.admin@anphu-demo.example` |
| Quản trị Minh Thành Limousine | `operator.admin@minhthanh-demo.example` |
| Quản trị Tây Nguyên Travel | `operator.admin@taynguyen-demo.example` |
| Nhân viên An Phú | `operator.staff@anphu-demo.example` |
| SYSTEM_ADMIN test | `system.admin@m31-demo.example` |

Trên máy fixture, lấy mật khẩu của đúng tài khoản từ `.tools/m31-6/runtime.local.json` bằng trình chỉnh sửa riêng trên máy. Các mục `operators`, `staff`, `admin`, `customer` chứa cặp đăng nhập tương ứng. Tránh hiển thị file này trong terminal ghi log, màn hình trình chiếu hoặc bản chụp. Tài khoản admin đã tồn tại. Mở lại demo không cần cấp lại role hay đổi mật khẩu.

## Kịch bản giới thiệu

1. Chọn **14, 20 hoặc 27/11/2026**. Tìm TP.HCM → Đà Lạt để so sánh hai nhà xe và hai giá 250.000đ / 300.000đ. Chọn tuyến từ Home rồi đổi điểm trả sang Phan Thiết để xem tên tuyến cập nhật đúng.
2. Mở TP.HCM → Phan Thiết ngày 14/11: An Phú, ghế ngồi 40 chỗ, giá 180.000đ, có ảnh. Voucher `M31-AP-FIX20` giảm 20.000đ khi đủ điều kiện. Có thể dừng ở bước xem giá để giữ nguyên hai booking lịch sử.
3. Thử Buôn Ma Thuột → Pleiku của Tây Nguyên, hoặc Đà Nẵng → Huế của Minh Thành. Operator vào **Đội xe** và chọn **ngày vận hành** trong danh sách chuyến/điều phối. Admin vào **Nhà xe** để xem tổng xe và tổng chuyến.
4. Home có ba mã công khai đủ điều kiện hiển thị tại thời điểm kiểm tra: `M31-AP-FIX20`, `M31-AP-MIN50`, `M31-MT-PCT10`. Trạng thái công khai không bảo đảm mã dùng được cho mọi hành trình hoặc mức chi tiêu.

Lịch và thời hạn voucher là dữ liệu cố định. Khi ngày hệ thống thay đổi, Home “sắp khởi hành”, quyền giữ ghế và danh sách ưu đãi có thể khác. Sau khi lịch đã qua, dùng evidence để xem lại hoặc chuẩn bị một fixture mới riêng cho đợt demo tiếp theo.

## Dừng dịch vụ

Nếu đã dùng `Start.ps1`, chạy từ repo:

```powershell
powershell.exe -NoProfile -File .\.tools\m31-6\Stop.ps1
docker inspect --format '{{.State.Status}}' busgo-m312-catalogue-mysql-1
docker volume inspect --format '{{.Name}}' busgo-m312-catalogue_m312_data
```

Nếu chạy foreground, nhấn Ctrl+C trong từng terminal của frontend/backend rồi chạy `docker stop busgo-m312-catalogue-mysql-1`. Container dừng, volume và dữ liệu vẫn được giữ lại.
