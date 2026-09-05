# Hướng dẫn chạy Integration Service trên IntelliJ IDEA

Tài liệu này chỉ nói về **Integration Service**. Hai hệ thống nguồn UniSIS và UniLearn
do giảng viên cấp được chạy riêng (xem mục 1).

---

## 0. Chuẩn bị một lần

### 0.1. JDK

Dự án biên dịch ở mức Java 21 (`maven.compiler.release=21`). Máy đang có
**Temurin JDK 25** tại:

```
C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot
```

> **Cạm bẫy trên máy này:** biến `JAVA_HOME` đã trỏ đúng JDK 25, nhưng `PATH` lại
> ưu tiên **Java 8** đứng trước. Gõ `java -version` ở terminal sẽ ra `1.8.0_501`.
> IntelliJ không bị ảnh hưởng vì nó dùng SDK khai báo trong Project Structure,
> nhưng khi chạy Maven ngoài terminal thì phải tự đẩy JDK 25 lên đầu `PATH`.

### 0.2. Trỏ SDK trong IntelliJ

1. `File` → `Project Structure` → `Project`
2. **SDK**: chọn `temurin-25` (nếu chưa có: `Add SDK` → `JDK` → trỏ vào đường dẫn ở trên)
3. **Language level**: `21`
4. `Apply` → `OK`

Kiểm tra thêm: `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven`
→ `Importing` → **JDK for importer** cũng để `temurin-25`.

### 0.3. Nạp phụ thuộc

Mở panel **Maven** bên phải → bấm nút `Reload All Maven Projects` (biểu tượng hai mũi tên).
Lần đầu mất khoảng 2–4 phút vì phải tải toàn bộ cây phụ thuộc Spring Boot. Đừng tưởng treo.

---

## 1. Chạy hai hệ thống của giảng viên trước

Integration Service cần UniSIS và UniLearn đã sẵn sàng, nếu không mọi sự kiện sẽ
nằm ở trạng thái `RETRYING`.

Trong VS Code, mở thư mục `C:\Users\ADMIN\Downloads\Integration_workshop` rồi chạy
`run_both.bat`, hoặc chạy riêng từng cái:

```
py -3.12 -m uvicorn sis.app.main:app --host 127.0.0.1 --port 8001
py -3.12 -m uvicorn lms.app.main:app --host 127.0.0.1 --port 8002
```

Xác nhận trước khi sang bước 2:

- <http://127.0.0.1:8001/health> → `{"status":"ok","system":"UniSIS"}`
- <http://127.0.0.1:8002/health> → `{"status":"ok","system":"UniLearn"}`

> Đừng ghi file gì vào thư mục đó ngoài những gì hai chương trình tự tạo trong `data/`.
> Bản gốc nằm ở `Integration_workshop.rar` nếu cần phục hồi.

---

## 2. Run Configuration

> **Đã tạo sẵn.** Thư mục `.run/` ở gốc dự án có sẵn hai cấu hình, IntelliJ tự nhận:
>
> | Tên | Công dụng |
> |---|---|
> | `IntegrationService` | Chạy ứng dụng, đã điền sẵn toàn bộ biến môi trường |
> | `All Tests` | Chạy `mvn test` |
>
> Chỉ cần chọn `IntegrationService` ở hộp thả xuống góc trên bên phải rồi bấm **Run**.
> Nếu chưa thấy, bấm `File` → `Reload All from Disk`.
>
> Thư mục `.run/` **nằm trong `.gitignore`** vì file cấu hình chứa `CLIENT_SECRET` —
> đề bài cấm đưa secret lên kho mã. Khi đổi sang môi trường lab thật, sửa giá trị
> `CLIENT_SECRET` và `SIS_URL`/`LMS_URL` ngay trong `.run/IntegrationService.run.xml`
> hoặc qua hộp thoại `Edit Configurations…`.

### Nếu cần tạo lại bằng tay

`Run` → `Edit Configurations…` → `+` → **Spring Boot** (nếu bản Community không có
thì chọn **Application**).

| Trường | Giá trị |
|---|---|
| **Name** | `IntegrationService` |
| **Main class** | `vn.thanhdo.integration.IntegrationApplication` |
| **Module** | `uni-integration-service` |
| **JRE** | `temurin-25` |
| **Working directory** | `$MODULE_WORKING_DIR$` (mặc định là được) |

### Cấu hình — chọn MỘT trong ba cách

> **Chỉ `CLIENT_SECRET` là bắt buộc.** Mọi thứ khác đã có mặc định đúng trong
> `application.yml`: tenant `TEAM07`, `SIS_URL` `127.0.0.1:8001`, `LMS_URL`
> `127.0.0.1:8002`, polling bật, webhook bật.

**Cách 1 — file `.env` ở gốc dự án (gọn nhất, khuyến nghị).**
Ứng dụng tự đọc nhờ dòng `spring.config.import` trong `application.yml` — tính năng sẵn
có của Spring Boot, không cần thư viện. Không phải khai báo biến nào trong IDE.

```
TENANT_ID=TEAM07
CLIENT_SECRET=student-secret
ENABLE_WEBHOOK=false
```

Tạo bằng cách sao chép `.env.example` thành `.env` rồi sửa giá trị. File `.env` nằm trong
`.gitignore` nên bí mật không bao giờ lên kho mã.

**Cách 2 — dùng Run Configuration có sẵn.** Thư mục `.run/` đã điền sẵn mọi biến;
chỉ việc chọn `IntegrationService` rồi bấm Run.

**Cách 3 — gõ tay vào ô Environment variables.** Trên Windows IntelliJ ngăn cách bằng
dấu **chấm phẩy**:

```
CLIENT_SECRET=student-secret
```

Thứ tự ưu tiên: biến môi trường **thắng** `.env`, và `.env` **thắng** `application.yml`.
Nên khi cần đổi nhanh một giá trị mà không sửa file, cứ đặt biến môi trường đè lên.

Ý nghĩa từng giá trị:

| Biến | Ý nghĩa |
|---|---|
| `TENANT_ID` | Tenant của mình. **Phải giống hệt** ở cả UniSIS lẫn UniLearn |
| `CLIENT_SECRET` | Gói chạy cục bộ dùng mặc định `student-secret`. Môi trường lab thật thì lấy theo phiếu tài khoản |
| `ENABLE_POLLING` | Xương sống bảo đảm không mất sự kiện — **luôn để `true`** |
| `ENABLE_WEBHOOK` | Để `false` khi chạy cục bộ cho log gọn. Bật lên khi cần độ trễ thấp |

`SIS_URL` và `LMS_URL` không cần khai báo: `application.yml` đã mặc định
`http://127.0.0.1:8001` và `http://127.0.0.1:8002`. Chỉ đặt khi giảng viên cấp URL khác.

> **Không bao giờ** gõ secret thật vào `application.yml` rồi commit. File `.env` đã
> nằm trong `.gitignore`; `.env.example` chỉ là tài liệu, Spring Boot không tự đọc nó.

Bấm `Apply` → `OK`, rồi bấm nút **Run** (tam giác xanh) hoặc `Shift+F10`.

---

## 3. Xác nhận chạy đúng

Chờ dòng này trong cửa sổ Run:

```
Started IntegrationApplication in X seconds
```

Mở trình duyệt: <http://localhost:8080/health?deep=true>

Phải thấy `"canAuthenticate": true` ở **cả** `sis` lẫn `lms`:

```json
{
  "status": "UP",
  "tenantId": "TEAM07",
  "sis": { "baseUrl": "http://127.0.0.1:8001", "canAuthenticate": true },
  "lms": { "baseUrl": "http://127.0.0.1:8002", "canAuthenticate": true },
  "inbox": { "RECEIVED": 0, "RETRYING": 0 },
  "modes": { "webhook": false, "polling": true, "worker": true }
}
```

Nếu `canAuthenticate` là `false` → sai `CLIENT_SECRET` hoặc hai hệ thống chưa chạy.

Bỏ `?deep=true` thì `/health` không gọi ra ngoài, không tốn lượt trong ngân sách
100 request/phút.

---

## 4. Chạy kiểm thử

**Toàn bộ:** panel Maven → `Lifecycle` → nhấp đúp `test`.

**Một lớp:** mở file test, bấm mũi tên xanh cạnh tên lớp → `Run`.

**Một ca:** bấm mũi tên xanh cạnh tên phương thức.

Bộ kiểm thử **không cần mạng và không cần hai hệ thống nguồn** — WireMock giả lập
cả hai. Hiện có 27 ca, chạy hết dưới 20 giây.

| Lớp | Nội dung |
|---|---|
| `SisStudentMappingTest` | Kiểm thử thuần: ánh xạ trạng thái, ghép tên, đọc mốc thời gian, che bí mật |
| `EnsureUserIntegrationTest` | INT-01, INT-05, chống trùng, retry 503, 409, mất ánh xạ, polling |

---

## 5. Thử một luồng INT-01 hoàn chỉnh

1. Mở UniSIS: <http://127.0.0.1:8001/> → đăng nhập Tenant `TEAM07`,
   Client ID `WEB-CONSOLE`, Secret `student-secret`
2. Mở UniLearn: <http://127.0.0.1:8002/> → cùng tenant. **Kiểm tra góc trên bên phải
   hiển thị đúng `TEAM07` ở cả hai** — trộn tenant là nguyên nhân số một khiến dữ liệu
   không bao giờ khớp
3. UniLearn → **People** → tìm mã sắp tạo, xác nhận **chưa có** (ảnh trạng thái trước)
4. UniSIS → **Sinh viên** → **+ Hồ sơ mới** → nhập mã mới, ví dụ `SV07LAB03`,
   Trạng thái `ACTIVE` → **Lưu**
5. UniSIS → **Dòng sự kiện** → ghi lại `eventId` của `student.created`
6. Chờ tối đa 10 giây (nhịp polling)
7. UniLearn → **People** → nhấn ↻ → phải có **đúng 01 User**, `External ref` bằng mã
   vừa tạo, `Type = LEARNER`, `Access = ENABLED`

Xem tầng xử lý ở giữa: <http://localhost:8080/admin/inbox> — phải thấy
`status=DONE`, `action=CREATE`.

**Thử tiếp INT-05:** UniSIS → sửa trạng thái sang `SUSPENDED` → UniLearn phải chuyển
`Access = DISABLED` mà **`id` không đổi**.

> Theo mục 9.7 của đề: khi chứng minh INT-01, dữ liệu bên UniLearn **phải do
> Integration Service tạo**. Đừng bấm tạo tay User bên LMS — thao tác đó không được
> tính là bằng chứng tích hợp.

---

## 6. Endpoint quản trị

### Swagger UI

Cách xem dễ nhất, khỏi gõ `curl`:

<http://localhost:8080/swagger-ui.html>

Bấm **Try it out** trên từng endpoint là gọi được ngay. Đặc tả thô ở
<http://localhost:8080/v3/api-docs>.

> Hai hệ thống nguồn cũng có Swagger riêng do FastAPI tự sinh — dùng nó để xác minh
> trạng thái thật theo mục 9.8 của đề:
>
> | Địa chỉ | Nội dung |
> |---|---|
> | <http://127.0.0.1:8001/docs> | Swagger UI UniSIS |
> | <http://127.0.0.1:8002/docs> | Swagger UI UniLearn |
> | <http://127.0.0.1:8001/redoc> | Bản ReDoc, dễ đọc khi tra cứu |
>
> Cách dùng: gọi `POST /api/v1/auth/token` với `clientId`, `clientSecret`, `tenantId`
> → chép `accessToken` → bấm **Authorize** ở góc trên bên phải → dán token vào →
> giờ mọi endpoint đều gọi được. Nhớ che token khi chụp ảnh cho báo cáo.

### Danh sách endpoint

| Endpoint | Công dụng |
|---|---|
| `GET /health` | Trạng thái, tenant, ngân sách tần suất còn lại, tồn đọng inbox |
| `GET /admin/inbox` | Danh sách sự kiện; lọc `?status=RETRYING` |
| `GET /admin/inbox/summary` | Đếm theo từng trạng thái |
| `GET /admin/events/{eventId}` | Toàn bộ vòng đời một sự kiện theo mã tương quan |
| `GET /admin/dead-letter` | Sự kiện lỗi dữ liệu cần rà soát |
| `POST /admin/inbox/{id}/requeue` | Đưa lại vào hàng đợi — dùng để chứng minh `NOOP` |
| `POST /admin/drain` | Chạy ngay một lượt xử lý, khỏi chờ lịch |
| `POST /webhooks/sis`, `/webhooks/lms` | Bộ nhận callback (chỉ khi bật webhook) |

`GET /admin/inbox` chính là **tầng 3** trong quy trình gỡ lỗi bốn tầng của đề. Khi bảo vệ,
chiếu trang này lên màn hình thay vì cuộn log trong terminal.

---

## 7. Gỡ rối

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `canAuthenticate: false` | Sai `CLIENT_SECRET`, hoặc hai hệ thống chưa chạy | Kiểm tra `/health` của 8001 và 8002 trước |
| Sự kiện đứng ở `RETRYING` | Hệ thống đích chưa sẵn sàng | Đúng thiết kế — bật hai hệ thống lên, sự kiện tự thử lại theo backoff 1-2-4-8-16-32 giây |
| Sự kiện vào `DEAD_LETTER` | Lỗi dữ liệu, không phải lỗi tạm thời | Xem `lastError` ở `/admin/inbox`; sửa xong thì `requeue` |
| `403 TENANT_MISMATCH` | Trộn tenant | `TENANT_ID` phải khớp tenant đang đăng nhập trên cả hai giao diện |
| Nhận `429` liên tục | Vượt 100 request/phút | Bộ điều tiết đã chừa biên 80/100; nếu vẫn dính thì giảm nhịp polling |
| `Port 8080 already in use` | Còn tiến trình cũ | Đổi `PORT=8081` trong Environment variables, hoặc tắt tiến trình cũ |
| Terminal báo `java version 1.8` | `PATH` ưu tiên Java 8 | Chỉ ảnh hưởng khi chạy Maven ngoài terminal — xem lệnh ở mục 8 |
| Sự kiện có loại chưa hỗ trợ | Chưa làm INT-02…INT-08 | Bị đánh dấu `DONE / NOOP` kèm cảnh báo trong log; sau khi bổ sung handler thì `requeue` để xử lý lại |

---

## 8. Chạy ngoài terminal (không dùng IntelliJ)

Trong **PowerShell** tại thư mục dự án — lưu ý phải đẩy JDK 25 lên đầu `PATH`:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; $env:TENANT_ID="TEAM07"; $env:CLIENT_SECRET="student-secret"; mvn spring-boot:run
```

Đóng gói thành file jar:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; mvn -DskipTests package
```

Chạy file jar đã đóng gói:

```powershell
$env:TENANT_ID="TEAM07"; $env:CLIENT_SECRET="student-secret"; & "$env:JAVA_HOME\bin\java.exe" -jar target\uni-integration-service-1.0.0.jar
```

> ⚠️ **Thư mục làm việc quan trọng.** Cả `.env` lẫn thư mục `data/` đều tính theo **thư mục
> đang đứng**, không phải theo vị trí file jar. Chạy từ chỗ khác thì `.env` không được nạp và
> cơ sở dữ liệu sẽ mọc ở nơi khác. Luôn `cd` vào thư mục chứa `.env` trước — hoặc truyền cấu
> hình qua biến môi trường như lệnh trên, vốn không phụ thuộc thư mục.
>
> Đã kiểm chứng: chép **một mình file jar** sang thư mục trống, đặt cạnh nó một file `.env`,
> chạy lên bình thường và tự tạo `data/` ngay tại đó. Không cần mã nguồn, không cần IDE.

---

## 9. Dữ liệu và trạng thái

Cơ sở dữ liệu tích hợp là H2 chế độ file, nằm ở `./data/integration.mv.db` trong thư mục
dự án. Nó **tồn tại qua các lần khởi động lại** — đúng yêu cầu của bản kiểm tra trước khi
nộp: *"chạy sau khi restart mà không mất mapping hoặc sự kiện đang chờ"*.

Muốn làm lại từ đầu thì dừng ứng dụng rồi xoá cả thư mục `data/`. Flyway sẽ tạo lại
sáu bảng khi khởi động lần sau.

Thư mục `data/` đã nằm trong `.gitignore` — không commit dữ liệu tenant lên kho mã.
