# Hướng dẫn cho AI agent làm việc trên dự án này

Đọc hết file này trước khi sửa bất cứ thứ gì. Nó chứa những ràng buộc mà **đọc mã nguồn
không suy ra được**, và một danh sách cạm bẫy đã thực sự làm hỏng việc ở dự án này.

Người dùng trao đổi bằng **tiếng Việt**. Chú thích trong mã nguồn và tài liệu cũng tiếng Việt,
**không dấu** (để tránh lỗi mã hoá trên Windows console). Giữ nguyên quy ước đó.

---

## 1. Dự án này là gì

Bài tập kết thúc học phần **Kiến trúc và Tích hợp hệ thống**. Sinh viên viết một
**Integration Service** độc lập nối hai hệ thống có sẵn của giảng viên:

- **UniSIS** (Student Information System) — `http://127.0.0.1:8001`
- **UniLearn** (LMS) — `http://127.0.0.1:8002`

Đây **KHÔNG phải một API nghiệp vụ.** Dịch vụ này chủ yếu là HTTP *client* của hai hệ thống
kia. Phần phục vụ vào chỉ gồm bộ nhận webhook, `/health` và vài endpoint `/admin/**`.
Đừng đề xuất thêm CRUD nghiệp vụ — nó sẽ sai đề.

**Trạng thái: đã xong.** 8/8 yêu cầu INT, 12/12 NFR, 84/84 ca kiểm thử xanh. Việc còn lại
chủ yếu là giấy tờ (báo cáo, sơ đồ, ảnh chụp), không phải mã nguồn.

---

## 2. Tài liệu gốc — đọc trước khi phán đoán

| Tài liệu | Vị trí | Vai trò |
|---|---|---|
| **Đề bài** | `C:\Users\ADMIN\Downloads\Dự án kết thúc môn.docx` | Nguồn sự thật cho mọi yêu cầu |
| Đề xuất giải pháp v2.0 | `C:\Users\ADMIN\Downloads\YTuong-GiaiPhap-Integration-Service-v2.docx` | Thiết kế đã chốt |
| Mã nguồn hai hệ thống nguồn | `C:\Users\ADMIN\Downloads\Integration_workshop` | **Chỉ đọc** — xem mục 3 |

Chú thích trong mã nguồn tham chiếu tới hai tài liệu trên bằng ký hiệu ngắn:

| Ký hiệu | Nghĩa |
|---|---|
| `muc 11.2`, `muc 16.6` | Mục của **đề bài** |
| `muc 3.3`, `muc 6.4` | Mục của **bản thiết kế v2.0** |
| `BV-1` … `BV-4` | Bốn bất biến (thiết kế mục 4.2) |
| `INT-01` … `INT-08` | Tám yêu cầu tích hợp |
| `NFR-01` … `NFR-12` | Mười hai yêu cầu phi chức năng |
| `I01`, `U02`, `T09` | Ca kiểm thử tích hợp / đơn vị / kịch bản chấm |

Khi được hỏi về một yêu cầu, **đọc lại đề bài rồi mới trả lời** — đừng dựa vào trí nhớ hay
suy diễn từ mã nguồn.

Trích văn bản từ file `.docx`:

```bash
unzip -o -q "duong/dan/file.docx" -d /tmp/doc && python -c "import re,io;x=open(r'/tmp/doc/word/document.xml',encoding='utf-8').read();print('\n'.join(''.join(re.findall(r'<w:t[^>]*>(.*?)</w:t>',p,re.S)) for p in re.findall(r'<w:p[ >].*?</w:p>',x,re.S)))"
```

---

## 3. Ranh giới TUYỆT ĐỐI không được vượt

Đề bài mục 3 đặt ra tám nguyên tắc. Ba điều dưới đây là thứ agent dễ vi phạm nhất:

### 3.1. Không sửa gì trong `Downloads\Integration_workshop`

Đó là mã nguồn của giảng viên. **Chỉ đọc.** Không sửa, không format, không thêm file —
**kể cả file log tạm**. Đã từng lỡ ghi 4 file log vào đó và người dùng yêu cầu khôi phục.

Chạy hai hệ thống thì redirect log vào thư mục scratchpad của phiên, không vào thư mục đó.

Nếu lỡ làm bẩn: bản gốc ở `Downloads\Integration_workshop.rar`, giải nén bằng
`C:\Program Files\WinRAR\UnRAR.exe`. **Chỉ khôi phục đúng file đã đụng vào** —
`run_sis.bat`, `run_lms.bat`, `install_dependencies.bat` và các `.pyc` đã bị người dùng sửa
từ 2026-08-08, ghi đè chúng là xoá mất công của họ.

### 3.2. Không tạo dữ liệu tay ở hệ thống ĐÍCH khi đang chứng minh tích hợp

Đề mục 9.7: với INT-01…05 và 07, thao tác phải phát sinh ở **UniSIS**, còn dữ liệu bên
**UniLearn phải do Integration Service tạo**. Với INT-06 và 08 thì ngược lại.

Tự gọi `POST /api/v1/users` lên UniLearn để "cho nhanh" là làm hỏng bằng chứng. Swagger cũng
không phải cửa sau — nó chỉ dùng để **đọc** ở phía đích.

### 3.3. Không đưa bí mật vào Git

`.env`, `.run/` đã nằm trong `.gitignore`. Không hard-code `CLIENT_SECRET` vào
`application.yml` hay bất kỳ file nào được commit. Không in token ra log —
`ApiClient.redact()` lo việc đó, đừng vòng qua nó.

---

## 4. Môi trường — cạm bẫy có thật

### 4.1. `PATH` ưu tiên Java 8, `JAVA_HOME` trỏ JDK 25

`java -version` ở terminal ra `1.8.0_501`. **Luôn** đặt lại trước khi gọi Maven:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

IntelliJ không dính lỗi này vì dùng SDK khai báo riêng.

### 4.2. Shell

Có cả Bash (Git Bash) lẫn PowerShell. Bash dùng đường dẫn `/c/Users/...`, nhưng **`python` là
bản Windows** nên phải truyền đường dẫn kiểu `C:/Users/...`. Trộn hai kiểu là lỗi
`FileNotFoundError` rất hay gặp.

### 4.3. Ba tiến trình, đúng thứ tự

| # | Việc | Cổng |
|---|---|---|
| 1 | `run_both.bat` trong `Integration_workshop` | 8001, 8002 |
| 2 | Integration Service | 8080 |

Bật ngược thứ tự thì mọi sự kiện nằm ở `RETRYING` — **đúng thiết kế**, không phải lỗi.

Trước khi khởi động, kiểm tra cổng trống; sau khi xong, **dừng tiến trình mình đã bật** để
người dùng chạy được từ IntelliJ.

---

## 5. Lệnh thường dùng

```bash
mvn -B test
```

84 ca, dưới 30 giây, **không cần mạng và không cần hai hệ thống nguồn** — WireMock giả lập cả hai.

```bash
mvn -B -DskipTests package
```

```bash
curl "http://localhost:8080/health?deep=true"
```

`?deep=true` mới thật sự gọi ra ngoài để lấy token; không có tham số thì chỉ báo cấu hình.

Cấu hình đọc theo thứ tự **biến môi trường > `.env` > `application.yml`**. Chỉ
`CLIENT_SECRET` là bắt buộc; mọi giá trị khác đã có mặc định đúng.

### Nhìn thẳng vào cơ sở dữ liệu

Khi `/admin/inbox` chưa đủ để kết luận, nối vào H2 **trong lúc dịch vụ vẫn chạy** —
`AUTO_SERVER=TRUE` cho phép kết nối thứ hai, không phải tắt gì:

```
jdbc:h2:file:<duong-dan-du-an>/data/integration;AUTO_SERVER=TRUE   user sa, khong mat khau
```

Thiếu `AUTO_SERVER=TRUE` sẽ báo *"Database may be already in use"*.

Hai điểm dễ vấp: bảng `flyway_schema_history` phải đặt nháy kép cho cả tên bảng lẫn tên
cột; và mốc thời gian trong H2 là **giờ địa phương**, lệch 7 tiếng so với số hiển thị trên
giao diện của giảng viên — cả hai đều đúng, xem bẫy #3 ở mục 7.

Cách nối bằng IntelliJ hoặc H2 Console, năm bảng, và các câu truy vấn đã kiểm chứng nằm ở
**`HUONG-DAN-KIEM-THU.md` Phần 7b**.

---

## 6. Kiến trúc — ý tưởng cốt lõi

**Hội tụ trạng thái thay vì xử lý sự kiện.** Mỗi hàm hỏi *"trạng thái đúng phải như thế nào,
và hiện tại nó đang ra sao"* rồi chỉ làm phần chênh lệch. Nhờ vậy chống trùng và khả năng chịu
sự kiện sai thứ tự là **thuộc tính tự nhiên**, không phải phần cài thêm.

```
webhook ─┐
poller  ─┼─→ inbox_event ─→ Worker ─→ 5 handler ─→ UniSIS / UniLearn
recon   ─┘   UNIQUE(source_system, event_id)
```

| Handler | Phục vụ | File |
|---|---|---|
| `ensureUser` | INT-01, INT-05 | `sync/EnsureUserHandler.java` |
| `ensureCourse` | INT-02, INT-07 | `sync/EnsureCourseHandler.java` |
| `ensureMembership` | INT-03, INT-04 | `sync/EnsureMembershipHandler.java` |
| `syncGrade` | INT-06 (LMS→SIS) | `sync/SyncGradeHandler.java` |
| `raiseAdvisingAlert` | INT-08 (LMS→SIS) | `sync/RaiseAdvisingAlertHandler.java` |

Thêm handler mới: cài `EventHandler`, đánh `@Component`. `Worker` tự tìm thấy qua
`List<EventHandler>`.

Các mảnh khác: `inbox/` (chống trùng, giành việc, retry) · `client/` (token, rate limit,
phân loại lỗi) · `reconcile/` (đối soát theo tập hợp) · `mapping/` (bộ nhớ đệm ID) ·
`audit/` (nhật ký) · `web/` (webhook, admin, health).

**Năm bảng.** `sync_state` và `alert_dedup` **đã bị loại bỏ có chủ đích** — xem `README.md`
mục "Ghi chú kỹ thuật". Đừng thêm lại: nguồn sự thật là hệ thống đích, không phải bảng cục bộ.

---

## 7. Cạm bẫy đã thực sự làm hỏng việc ở dự án này

Mỗi mục dưới đây từng tốn thời gian thật. Đừng lặp lại.

| # | Bẫy | Dấu hiệu | Cách tránh |
|---|---|---|---|
| 1 | **Chạy file jar cũ** | Log ghi `fn=(none) chua co handler` dù handler đã viết | `mvn package` trước khi `java -jar`. Trong IntelliJ thì Run tự biên dịch |
| 2 | **Mã hoá URL hai lần** | Nguồn trả `422`, log thấy `%253A` | **Không** gọi `URLEncoder` rồi ghép chuỗi. Dùng mẫu URI: `get("/x?a={v}", v)` |
| 3 | **Ba định dạng mốc thời gian** | Điểm mốc polling không tiến, đọc lại toàn bộ mỗi 10 giây | Luôn dùng `util/Timestamps.parse()`, đừng dùng `Instant.parse()` |
| 4 | **JDK HttpClient thử HTTP/2** | `EOFException` khi gọi `http://` | `ApiClient` đã ép `HTTP_1_1` — giữ nguyên |
| 5 | **Bộ che bí mật ăn cả thông báo lỗi** | Log ra `... : ***` vô nghĩa | Regex `redact()` có negative lookbehind cho `/`. Sửa cẩn thận |
| 6 | **`runId` của đối soát** | Lượt đối soát thứ hai không sinh việc nào | Mã sự kiện tổng hợp **phải** chứa `runId` mới mỗi lượt. Ca `I57` canh chỗ này |
| 7 | **Thứ tự kịch bản WireMock** | Nhánh "tạo mới" không bao giờ chạy | Đặt `willSetStateTo` trên bước **ghi**, không trên bước **đọc** |
| 8 | **Rò rỉ trạng thái giữa các ca kiểm thử** | Ca đứng một mình thì xanh, chạy cả bộ thì đỏ | Mỗi ca dùng mã nghiệp vụ riêng. `id_mapping` không tự xoá giữa các ca |
| 9 | **Tag ảnh Docker nổi** | `lstat /build/target: no such file` | Đã ghim `maven:3.9.9-eclipse-temurin-21`. Đừng đổi về tag nổi |
| 10 | **Thư mục làm việc** | `.env` không được nạp, `data/` mọc chỗ lạ | `.env` và `data/` tính theo **thư mục đang đứng**, không theo vị trí jar |

---

## 8. Hành vi mong đợi khi làm việc

### Kiểm chứng, đừng tuyên bố

Người dùng coi trọng bằng chứng thật hơn lời khẳng định. Chuẩn mực ở dự án này:

- Sửa mã → chạy `mvn test` → dán số liệu thật
- Nói tính năng chạy được → **chạy thật trên hai hệ thống nguồn** rồi dán kết quả
- Không dựng thử được → **nói rõ là chưa kiểm chứng**, đừng nói "chắc là chạy được"

Đã có tiền lệ: `Dockerfile` từng được coi là xong cho tới khi dựng thử và lộ ra **ba** lỗi thật.

### Sai thì sửa thẳng, đừng vòng vo

Trong dự án này đã có vài lần chẩn đoán sai (đổ cho kiến trúc CPU, đổ cho cache hỏng, trong
khi nguyên nhân là tag ảnh lỗi). Cách xử lý đúng: nêu dữ kiện bác bỏ, sửa kết luận, đi tiếp.
Đừng bịa nguyên nhân cho một hiện tượng chưa hiểu.

### Cảnh báo trước lệnh chạy lâu

Người dùng dễ tưởng bị treo rồi bấm dừng. Nói rõ "mất khoảng N phút" trước khi chạy
`mvn` lần đầu, `docker build`, hay bất cứ lệnh nào quá ~1 phút.

### Việc phá huỷ thì hỏi trước

Reset tenant, xoá `data/`, `wsl --shutdown`, ghi đè file của giảng viên — **hỏi trước**.
Reset tenant xoá sạch dữ liệu và cả bảng sự kiện; nếu người dùng chưa chụp ảnh bằng chứng thì
họ mất công làm lại.

---

## 9. Bản đồ tài liệu

| File | Nội dung |
|---|---|
| `README.md` | Tổng quan, tiến độ, endpoint, bản đồ mã nguồn |
| `HUONG-DAN-CHAY.md` | Chạy trên IntelliJ, cấu hình, bảng gỡ rối |
| `KICH-BAN-TEST.md` | Kịch bản chạy thử một mạch, ~25 phút, đủ 8 INT |
| `HUONG-DAN-KIEM-THU.md` | Tra cứu kiểm thử từng INT riêng lẻ |
| `docs/TEST-REPORT.md` | Test Report nộp cho giảng viên — đối chiếu 12 kịch bản chấm T01–T12 |
| `docs/DOI-CHIEU-NFR.md` | Đối chiếu 12 NFR kèm bằng chứng |
| `docs/evidence/*.md` | Bằng chứng đã thu thập cho từng INT |

**Cập nhật tài liệu khi sửa mã.** Người dùng đã bắt được vài chỗ tài liệu nói một đằng mã làm
một nẻo — đó là loại lỗi mất điểm khi bảo vệ. Đáng chú ý: bảng rủi ro từng hứa có kiểm tra
tenant lúc khởi động trong khi chưa hề cài đặt.

---

## 10. Bối cảnh còn để ngỏ

| Việc | Trạng thái |
|---|---|
| Tenant | Đang dùng `TEAM07`. Giảng viên là người **cấp** tenant; nếu được cấp mã khác thì chỉ sửa `TENANT_ID` trong `.env`, không đụng mã nguồn |
| Gọi `/admin/.../reset` của giảng viên | Đề không cấm, nhưng endpoint này nằm ngoài Phụ lục A và dùng khoá admin riêng. Trên bản cục bộ thì dùng được; môi trường lab chung nên hỏi giảng viên |
| Ảnh chụp giao diện | ✅ **41 ảnh** ở `docs/screenshots/`, đã chèn vào Phụ lục C của báo cáo. Chụp bằng cách cho chính trang web kết xuất `html2canvas` rồi POST về máy chủ nhận tạm ở cổng 9099 (script `shotserver.py` trong scratchpad) — máy không có Playwright và trình duyệt không mở cổng CDP. **Bẫy:** `html2canvas` kết xuất sai khi trang đã cuộn sâu; phải chụp cả phần tử bảng rồi cắt, đừng cắt theo vùng nhìn. Bản gốc 2× ở `docs/screenshots-goc/` — thư mục này **nằm trong `.gitignore`**, chỉ có ở máy |
| Bốn sơ đồ | ✅ đã dựng — `docs/diagrams/`, cả PNG lẫn mã `.puml`. Dựng lại bằng `C:\K15\plantuml.jar` + Graphviz; **nhớ đặt `JAVA_HOME` trước**, java trên PATH là Java 8 nên PlantUML sẽ báo `UnsupportedClassVersionError`. Xem `docs/diagrams/README.md` |
| Test Report | ✅ đã gộp — `docs/TEST-REPORT.md`. Cập nhật lại khi có thêm bằng chứng |
| Báo cáo bài tập lớn bản Word | ✅ `docs/BaoCao-BaiTapLon-Integration-Service.docx` — **105 trang**, 5 chương + phụ lục A–F, 46 hình, 52 bảng. **Bản chủ do người dùng giữ ở `C:\Users\ADMIN\Downloads\BaoCaoTichHop.docx`**; tệp trong `docs/` chỉ là bản sao để lên kho mã. Người dùng đã tự gộp trang bìa và chỉnh tay, nên **mọi sửa đổi phải áp vào tệp của họ bằng python-docx, tuyệt đối không sinh lại từ script**. Mục lục là trường `TOC` tự động — sửa xong nhớ nhắc họ `Ctrl+A` rồi `F9`. **Bẫy:** các khối mã trong tệp chỉ đặt `w:rFonts w:eastAsia="Consolas"`, thiếu `w:ascii`/`w:hAnsi`, nên đoạn mới thêm phải đặt đủ ba thuộc tính kẻo chữ Latinh rơi về Times New Roman |
| Kịch bản T10 | ✅ **đã chạy thật** — sự kiện `evt_91aed8d5c6db462c`: lần 1 lúc 14:01:15.445 nhận HTTP 503 → `RETRYING`, lần 2 lúc 14:01:16.505 → `DONE action=UPDATE`. Cách 1,06 giây, khớp bước đầu của dãy backoff. Trình bày ở mục 5.3.2 của báo cáo. **Bẫy:** `maybe_fail()` của giảng viên chỉ gắn trên endpoint **ghi** (POST/PATCH/DELETE), không có trên GET — thử `GET /api/v1/users` ở mức HIGH sẽ ra 20/20 thành công và tưởng nhầm là cơ chế hỏng. Chạy xong **luôn trả `failure_mode` về OFF** |
