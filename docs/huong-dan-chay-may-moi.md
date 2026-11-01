# Chạy BusGo trên máy mới

Docker **không bắt buộc**. Dự án cần MySQL; `docker-compose.yml` chỉ chạy
MySQL 8.4. Backend Java và frontend React chạy trực tiếp trên máy.
Bạn có thể chọn Docker để dựng database, hoặc cài MySQL riêng.
Launcher tự động dưới đây dùng Docker Desktop; cách không dùng Docker vẫn được
giữ ở phần chạy thủ công.

Hướng dẫn dưới đây dùng **Windows + PowerShell**, dành cho chạy thử local.

## 1. Chuẩn bị

- JDK **17**; đặt `JAVA_HOME` và `PATH` trỏ đúng JDK.
- Maven **3.6.3 trở lên** (dự án chưa có Maven Wrapper).
- Node.js **22.12 trở lên**, kèm npm.
- Một trong hai: Docker Desktop có Compose v2, hoặc MySQL Server **8.4**.
- Git nếu lấy mã nguồn bằng `git clone`; cũng có thể giải nén mã nguồn được gửi.
- Internet cho lần đầu tải thư viện và image MySQL nếu dùng Docker.

Mở PowerShell mới sau khi cài, kiểm tra:

```powershell
java -version
mvn -version
node --version
npm --version
```

`mvn -version` cũng phải hiển thị Java 17. Lấy mã nguồn đầy đủ, mở terminal
tại thư mục gốc `busgo` (nơi có `docker-compose.yml`). Không cần sao chép
`node_modules`, `backend/target` hoặc database từ máy cũ để chạy demo mới.

## 2. Chạy bằng launcher trên Windows

Hỗ trợ Windows PowerShell **5.1** hoặc PowerShell **7** trên Windows. Sau khi cài
các công cụ ở bước 1, mở Docker Desktop, chọn Linux containers và đợi engine sẵn
sàng. Launcher ưu tiên `JAVA_HOME` hợp lệ trỏ JDK 17, sau đó tìm JDK 17 trong PATH,
`.tools/jdk17`, các thư mục cài đặt Windows thông dụng và registry. Nó kiểm tra cả
`java`, `javac` và Java Maven thực sự dùng; chỉ truyền JAVA_HOME/PATH cho process
con, không đổi terminal/hệ thống và không tải JDK. Nếu chỉ có Java 8/không tìm thấy
JDK 17, script báo lỗi để bạn cài hoặc cấu hình JAVA_HOME một lần.
Mở PowerShell tại thư mục gốc repository:

```powershell
.\start-busgo.ps1
```

Đợi dòng `BusGo ready: http://localhost:5173`, rồi mở URL đó. Lần đầu có thể mất
nhiều phút để tải MySQL image, Maven/npm dependencies, build backend và seed demo.
Script chờ health, DB locations, chuyến mẫu ngày mai và API qua proxy trước khi
báo thành công. Backend có thể chờ tối đa 15 phút, frontend tối đa 15 phút;
thời gian từng lệnh tải/build cũng có giới hạn. Không cần mở riêng các terminal
backend/frontend. Các worker chạy ẩn và được quản lý cùng process con của chúng.

Dừng hoặc chạy lại:

```powershell
.\stop-busgo.ps1
.\start-busgo.ps1
```

Start lần hai tái sử dụng worker đã xác minh, chỉ tạo phần thiếu; không sinh lại
password/JWT hoặc tạo backend/frontend trùng. Stop dừng frontend, backend và đúng
MySQL của launcher, giữ nguyên volume, config và log; không dừng Docker Desktop.
Nếu Docker không truy cập được, stop vẫn có thể dừng worker đã xác minh và báo
MySQL chưa được kiểm tra/dừng, thay vì tự đoán.

**Database được cô lập:** project Compose cố định `busgo-local-demo`, named volume
`busgo-local-demo_mysql_data`, schema `busgo_launcher_demo`, MySQL chỉ bind
`127.0.0.1:3308`. Không dùng `.env` ở gốc, MySQL cũ trên 3307 hoặc schema test
M30.4B. Docker context từ xa bị từ chối. Backend/frontend dùng cổng 8080/5173;
nếu các cổng đang thuộc ứng dụng khác, launcher báo lỗi và không kill ứng dụng đó.
Hãy dừng phiên chạy thủ công trước khi dùng launcher. Chỉ chạy một checkout của
launcher trên cùng Docker engine vì project/volume/cổng được cố định.
Compose v2 trở lên có `up --wait-timeout` được hỗ trợ. Override riêng của launcher
bật `log_bin_trust_function_creators=1` để user schema tạo được các trigger của
migration hiện có trên MySQL 8.4; không thay cấu hình MySQL cũ. Nếu schema launcher
đã có migration failed (ví dụ V18 từ lượt thử trước), launcher dừng trước backend;
cần kiểm tra và duyệt phục hồi riêng, không tự repair hoặc xóa/reset dữ liệu.

**Cấu hình riêng:** `.busgo-local/config.json`, `compose.env` và
`compose.override.yml` được tạo một lần; dùng số ngẫu nhiên mật mã cho hai mật
khẩu MySQL và JWT Base64 32 byte. Toàn bộ `.busgo-local/` bị Git ignore và có
Windows ACL riêng cho user hiện tại và SYSTEM. Secret nằm dạng plaintext trong
file local và môi trường process cần dùng; không gửi folder này cho người khác,
không đưa lên Git và không chỉnh password bằng tay. Mất config khi volume còn
tồn tại hoặc config/container/volume không khớp sẽ làm launcher từ chối chạy;
cần phục hồi cấu hình gốc hoặc điều tra có chủ đích, không xóa database để chữa lỗi.
Docker context và engine ID cũng được lưu, nên đổi sang engine local khác không
âm thầm tạo database trống thay cho dữ liệu cũ.
Đổi chỗ/đổi tên thư mục checkout cũng cần kiểm tra lại ownership/config, không
âm thầm nhận lại tài nguyên của đường dẫn cũ.

**Logs và state:** xem `.busgo-local/logs/launcher.log`, `docker.log`,
`backend-<run-token>.log`, `frontend-<run-token>.log` và `worker-<run-token>.log`.
Không in password/JWT vào console hoặc log. State có PID, thời điểm tạo, executable,
run token và Windows Job Object; PID không khớp hoặc không kiểm tra được command
line/job sẽ bị từ chối, không kill mù theo PID. Với Flyway/seed lỗi, xem log;
không tự repair/reset. Launcher chỉ thu hồi các tài nguyên mới khởi động trong
lượt start thất bại, sau khi xác minh ownership.

**Nếu Execution Policy chặn script:** đọc/kiểm tra mã nguồn trước; có thể mở một
phiên Windows PowerShell chỉ áp dụng policy cho process đó:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-busgo.ps1
# Dừng bằng phiên tương tự nếu shell hiện tại vẫn chặn script:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\stop-busgo.ps1
```

Không cần đổi policy toàn máy. Group Policy của máy tổ chức có thể chặn ngay cả
process policy; khi đó cần quản trị viên cho phép. Launcher không tự cài công cụ,
khởi động Docker Desktop, sửa PATH/JAVA_HOME hệ thống hoặc vượt Group Policy.

Tài khoản demo vẫn là các tài khoản ở bước 5 bên dưới; khách hàng tự đăng ký.
Không phải mọi chức năng M30 có sẵn dữ liệu minh họa: campaign voucher, booking,
vé đã thanh toán và lịch sử cần chuẩn bị qua giao diện. Thanh toán/hoàn tiền vẫn
là mô phỏng. Nếu ứng dụng chạy liên tục qua nhiều ngày và không còn chuyến mẫu
ngày mai, stop/start để seeder bổ sung lịch còn thiếu; không xóa dữ liệu.

Kiểm tra launcher không cần database:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\launcher\launcher.tests.ps1
```

Bộ kiểm tra dùng mocks cho Docker
và process vô hại cho Windows Job Object; không thay thế kiểm chứng startup thật
MySQL/backend/frontend trên máy của bạn.

## 2A. Chạy thủ công: database bằng Docker

Mở Docker Desktop và đợi engine chạy. Kiểm tra:

```powershell
docker compose version
```

Tại thư mục gốc, nếu chưa có `.env`:

```powershell
Copy-Item .env.example .env
notepad .env
```

Điền hai mật khẩu riêng cho máy mới, ví dụ chỉ dùng local:

```dotenv
MYSQL_DATABASE=busgo_db
MYSQL_USER=busgo
MYSQL_PASSWORD=BusgoLocal_2026!
MYSQL_ROOT_PASSWORD=BusgoRootLocal_2026!
MYSQL_PORT=3307
```

Sau đó:

```powershell
docker compose up -d --wait
docker compose ps
```

MySQL của Docker được truy cập từ máy qua `localhost:3307`.
Chuyển đến bước 3.

## 2B. Chạy thủ công: database không dùng Docker

Cài và khởi động MySQL Server 8.4. Giả sử MySQL chạy ở cổng **3306**.
Đăng nhập bằng tài khoản quản trị qua MySQL Workbench hoặc MySQL CLI,
chạy SQL sau trên database mới:

```sql
CREATE DATABASE busgo_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'busgo'@'localhost' IDENTIFIED BY 'BusgoLocal_2026!';
GRANT ALL PRIVILEGES ON busgo_db.* TO 'busgo'@'localhost';
```

Thay mật khẩu ví dụ bằng mật khẩu bạn chọn. Nếu database/tài khoản đã tồn tại,
dùng thông tin tương ứng hoặc tạo database riêng; không xóa dữ liệu cũ.
Không cần tạo bảng hay import SQL thủ công: backend dùng Flyway để tạo và
cập nhật bảng khi khởi động. Source hiện có **27 migration, V1–V27**, gồm V27
thêm quyền công khai voucher với mặc định `FALSE`; voucher cũ không tự công khai.
Nên dùng database demo riêng. Nếu dùng database đã có dữ liệu cần giữ, sao lưu
trước khi nâng phiên bản; khởi động backend có thể áp dụng migration còn thiếu.

## 3. Chạy backend — terminal 1

Từ thư mục gốc, đặt biến môi trường trong **chính terminal sẽ chạy backend**:

```powershell
# Docker mặc định dùng 3307. MySQL cài riêng thường dùng 3306:
$env:DB_URL = 'jdbc:mysql://localhost:3307/busgo_db?connectionTimeZone=UTC'
$env:DB_USERNAME = 'busgo'
$env:DB_PASSWORD = 'BusgoLocal_2026!'

# Sinh khóa JWT ngẫu nhiên cho local lần đầu (32 byte, mã hóa Base64):
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$jwtRandom.Dispose()

Set-Location backend
mvn spring-boot:run '-Dspring-boot.run.profiles=dev,demo'
```

**Nếu dùng cách 2B, sửa `3307` thành cổng MySQL thực tế (thường là `3306`).**
`DB_PASSWORD` phải khớp mật khẩu user `busgo` đã đặt ở bước 2.
File `.env` ở gốc chỉ được Docker Compose đọc; Spring không tự đọc file này.
Giữ lại khóa JWT ở nơi lưu bí mật local được Git ignore và nạp lại cùng giá trị
cho các lần chạy sau; không đưa khóa vào frontend hoặc commit lên Git.

Giữ terminal chạy. Backend mặc định ở `http://localhost:8080`.
Profile `dev,demo` tạo tài khoản và chuyến xe mẫu; chỉ `dev` sẽ không tạo mẫu.
Lần đầu Maven cần tải thư viện, Flyway cần tạo bảng nên có thể mất thêm thời gian.
Chờ log hoàn tất demo seed và ứng dụng không thoát do lỗi trước khi dùng thử.
Seed giữ mật khẩu/status và dữ liệu đã có, có thể thêm bản ghi/chuyến còn thiếu
hoặc từ chối khi gặp xung đột định danh; không dùng profile demo trên database thật.

## 4. Chạy frontend — terminal 2

Mở terminal mới tại thư mục gốc:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Mở [http://localhost:5173](http://localhost:5173).
Mặc định frontend chuyển tiếp API sang backend cổng 8080 nên không cần tạo
file cấu hình frontend. Cả hai terminal phải tiếp tục chạy.

## 5. Kiểm tra và dùng thử

Trong terminal khác:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health | ConvertTo-Json -Depth 5
Invoke-RestMethod http://localhost:8080/api/v1/locations | ConvertTo-Json -Depth 5
```

Health phải có `data.status` bằng `UP` nhưng không tự kiểm tra database hoặc seed;
API locations kiểm tra thêm khả năng
đọc database. Trên website, tìm **Bến xe TP. Hồ Chí Minh → Bến xe Đà Lạt**,
chọn **ngày mai theo giờ Việt Nam**. Seed tạo lịch cho ba ngày bắt đầu từ
ngày mai tại thời điểm backend khởi động.

| Vai trò | Email | Mật khẩu demo ban đầu |
| --- | --- | --- |
| Quản trị nhà xe | operator.admin@anphu-demo.example | DemoOperator!2026 |
| Nhân viên nhà xe | operator.staff@anphu-demo.example | DemoStaff!2026 |

Khách hàng tự đăng ký trên giao diện. Tài khoản quản trị hệ thống không được
tạo tự động bởi demo; nếu cần thử vai trò này, xem mục System administrator
trong [deployment.md](deployment.md). Thanh toán và hoàn tiền đều mô phỏng.
Seed không đặt lại mật khẩu đã đổi và không sao chép dữ liệu từ máy cũ.

## 6. Tắt và chạy lại

- Nhấn `Ctrl+C` trong từng terminal frontend/backend để dừng.
- Nếu dùng Docker, tại thư mục gốc chạy `docker compose stop` để dừng MySQL.
- Lần sau chạy `docker compose up -d --wait` nếu dùng Docker; chạy lại bước 3
  và `npm run dev` trong `frontend`. Không cần `npm ci` lại trừ khi thư viện thay đổi.
- Biến `$env:...` chỉ tồn tại trong terminal hiện tại. Terminal backend mới
  cần đặt lại biến. Sinh JWT mới sẽ làm token đăng nhập cũ không còn hợp lệ;
  đăng nhập lại, hoặc lưu và dùng lại khóa local riêng của bạn.
- Dữ liệu MySQL Docker được giữ trong volume. Đổi mật khẩu trong `.env`
  không đổi mật khẩu của database đã khởi tạo. Không dùng `docker compose down -v`
  để xử lý lỗi đăng nhập vì lệnh đó xóa volume dữ liệu.

## Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Không nhận lệnh `java`, `mvn`, `node` | Kiểm tra cài đặt/PATH, mở terminal mới; Maven phải dùng Java 17. |
| Docker không kết nối được engine | Mở Docker Desktop và đợi engine khởi động. |
| `Communications link failure` / connection refused | Kiểm tra MySQL đang chạy và cổng trong `DB_URL`: Docker 3307, cài riêng thường 3306. |
| `Access denied for user` | Đối chiếu username/password thực tế của MySQL với biến backend; `.env` không tự cấu hình Spring. |
| Thiếu `JWT_SECRET` / `DB_PASSWORD` | Đặt biến trong cùng terminal chạy Maven. |
| Cổng 3307, 8080 hoặc 5173 đang bận | Dừng ứng dụng đang chiếm cổng; nếu đổi cổng MySQL phải sửa cả `DB_URL`. |
| Cần đổi backend sang 8081 | Đặt `$env:SERVER_PORT='8081'` trước khi chạy backend; ở terminal frontend đặt `$env:API_PROXY_TARGET='http://localhost:8081'` rồi chạy Vite. |
| Giao diện mở được nhưng API lỗi | Kiểm tra backend và health; dùng `npm run dev` vì `npm run preview` không có API proxy phát triển. |
| Không có chuyến mẫu | Chạy đúng `dev,demo`, tìm ngày mai; nếu backend đã chạy qua nhiều ngày, khởi động lại để bổ sung lịch mẫu mới. |
| Flyway migration/validation lỗi | Giữ log và database để kiểm tra phiên bản/schema; không sửa migration cũ, tự chạy repair hoặc xóa volume. |
| `Demo fixture collision` | Dừng để kiểm tra dữ liệu xung đột; dùng `dev` để xem môi trường hoặc chọn database demo riêng, không reset/reseed cưỡng ép. |

Khi gửi dự án cho bạn, gửi đủ mã nguồn và hướng dẫn này. Bạn đó tự tạo `.env`
và khóa JWT cho máy mới; không cần gửi file `.env` của máy hiện tại.
