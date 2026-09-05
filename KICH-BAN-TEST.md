# Kịch bản chạy thử một mạch — đủ 8 yêu cầu trong một câu chuyện

Khác với `HUONG-DAN-KIEM-THU.md` (tra cứu từng INT riêng lẻ), tài liệu này là **một mạch
liên tục**: một sinh viên, một lớp học phần, đi qua toàn bộ INT-01 → INT-08 rồi ba kịch bản
chấm. Mỗi bước dùng kết quả của bước trước.

Dùng để **tự chạy thử một lần cho quen tay**, và để tập dượt trước buổi bảo vệ.

| | |
|---|---|
| Thời gian | ~25 phút (chưa tính chụp ảnh) |
| Nhân vật | Sinh viên `SV07T01` · Lớp `SEC-TEAM07-T01` |
| Tenant | `TEAM07` |

> Hai mã trên **chưa từng được dùng**, nên chắc chắn không đụng `409`. Nếu chạy lại lần hai,
> đổi `T01` thành `T02`, `T03`… rồi thay đồng loạt.

---

## Bước 0 — Chuẩn bị (3 phút)

### 0.1. Bật ba tiến trình, đúng thứ tự

| # | Việc | Chờ đến khi |
|---|---|---|
| 1 | `run_both.bat` trong `Downloads\Integration_workshop` | Hai cửa sổ cmd hiện `Application startup complete` |
| 2 | Kiểm tra <http://127.0.0.1:8001/health> và <http://127.0.0.1:8002/health> | Cả hai trả `{"status":"ok"}` |
| 3 | IntelliJ → chọn `IntegrationService` → **Run** ▶ | Log hiện `Started IntegrationApplication` |

### 0.2. Xác nhận nối được cả hai bên

Mở <http://localhost:8080/health?deep=true> → phải thấy `"canAuthenticate": true` ở **cả**
`sis` lẫn `lms`.

### 0.3. Mở ba tab trình duyệt, để cạnh nhau suốt buổi

| Tab | Địa chỉ | Đăng nhập |
|---|---|---|
| **A — UniSIS** | <http://127.0.0.1:8001/> | Tenant `TEAM07`, Client ID `WEB-CONSOLE`, Secret `student-secret` → **Mở UniSIS** |
| **B — UniLearn** | <http://127.0.0.1:8002/> | Tenant `TEAM07`, Client ID `WEB-CONSOLE`, Secret `student-secret` → **Kết nối** |
| **C — Inbox** | <http://localhost:8080/admin/inbox> | không cần đăng nhập |
| **D — Swagger tích hợp** | <http://localhost:8080/swagger-ui.html> | không cần đăng nhập |

> Ô Tenant điền sẵn `TEAM01` — **phải sửa thành `TEAM07`** ở cả hai. Kiểm tra góc trên bên
> phải cả hai màn hình đều hiện `TEAM07` trước khi đi tiếp.

### 0.4. Ba giao diện Swagger — dùng để làm gì

Đề mục 9.8 cho phép dùng **response GET từ Swagger** làm bằng chứng xác minh, bên cạnh ảnh
giao diện. Có ba Swagger khác nhau, đừng nhầm:

| Swagger | Địa chỉ | Dùng để |
|---|---|---|
| UniSIS | <http://127.0.0.1:8001/docs> | Xác minh dữ liệu **nguồn**, đọc `/events` |
| UniLearn | <http://127.0.0.1:8002/docs> | Xác minh dữ liệu **đích**, nhất là ID nội bộ |
| Integration Service | <http://localhost:8080/swagger-ui.html> | Bấm `requeue`, `reconcile`, `drain`, xem inbox |

Hai cái đầu do FastAPI tự sinh, **cần đăng nhập**. Cái thứ ba là của mình, không cần.

#### Cách đăng nhập Swagger của UniSIS và UniLearn

Làm một lần cho mỗi hệ thống, token dùng được 12 giờ:

1. Mở <http://127.0.0.1:8001/docs>
2. Tìm `POST /api/v1/auth/token` → bấm vào để mở rộng → **Try it out**
3. Sửa ô Request body thành:

```json
{ "clientId": "WEB-CONSOLE", "clientSecret": "student-secret", "tenantId": "TEAM07" }
```

4. **Execute** → trong Response body, chép **toàn bộ** giá trị của `accessToken`
   (chuỗi dài bắt đầu bằng `eyJ...`)
5. Cuộn lên đầu trang → bấm nút **Authorize** 🔓 ở góc phải
6. Dán token vào ô → **Authorize** → **Close**

Từ giờ mọi endpoint đều gọi được. Làm y hệt cho <http://127.0.0.1:8002/docs>.

> ⚠️ Khi chụp ảnh Swagger cho báo cáo, **che ô token và Client Secret**. Đề mục 9.8 yêu cầu
> rõ điều này.

> 💡 Nếu Swagger báo `401`, token đã hết hạn hoặc chưa bấm **Authorize** — làm lại từ bước 2.

### 0.5. Ghi lại trước khi bắt đầu

Tab B → **Người dùng**: ghi số lượng User hiện có → `______`
Tab B → **Khóa học**: ghi số lượng Course hiện có → `______`

Cuối kịch bản sẽ đối chiếu lại hai số này.

---

## Bước 1 — INT-01: tạo sinh viên (3 phút)

**Tab B → Người dùng** → tìm `SV07T01` → phải **không có kết quả**. 📸

**Tab A → 02 Hồ sơ sinh viên** → bấm **+ Hồ sơ mới**. Hộp thoại *"Tạo hồ sơ sinh viên"*:

| Ô nhập | Điền chính xác |
|---|---|
| Mã sinh viên | `SV07T01` |
| Chương trình | `CNTT` |
| Họ & tên đệm | `Nguyen Thi` |
| Tên | `Mai` |
| Email | `mai.t01@sv.edu.vn` |
| Khóa | `2026` |
| Trạng thái | `ACTIVE` |

→ **Lưu thay đổi**. 📸

**Tab A → 08 Dòng sự kiện** → tìm `student.created` của `SV07T01`.
Ghi `eventId` → `evt_________________` 📸

**Chờ 10 giây.** Tab C → làm mới → tìm dòng `businessKey: "SV07T01"`:

```json
{ "eventType": "student.created", "status": "DONE", "action": "CREATE", "attempt": 1 }
```
📸

**Tab B → Người dùng** → ↻ → tìm `SV07T01`:

| Cột | Phải thấy |
|---|---|
| Username | `SV07T01` |
| External ref | `SV07T01` |
| Display name | **`Nguyen Thi Mai`** — họ trước, tên sau |
| Email | `mai.t01@sv.edu.vn` |
| Type | `LEARNER` |
| Access | `ENABLED` |
| User ID | một **số**, khác `SV07T01` |

**Ghi lại User ID** → `______` (bước 6 và 7 cần dùng) 📸

### Xác minh bằng Swagger — bằng chứng dạng thứ hai

Swagger UniLearn <http://127.0.0.1:8002/docs> → `GET /api/v1/users` → **Try it out** →
ô `externalRef` điền `SV07T01` → **Execute**.

Response body phải là mảng có **đúng một phần tử**, với `"externalRef": "SV07T01"` và một
`"id"` là số. 📸 *(che token)*

> ✅ **Đạt INT-01.** Số ở cột User ID là ID nội bộ do LMS tự sinh — bằng chứng cho nguyên tắc
> "không giả định ID nội bộ hai hệ thống giống nhau".

---

## Bước 2 — INT-05: đổi trạng thái (2 phút)

**Tab A → 02 Hồ sơ sinh viên** → tìm `SV07T01` → **Sửa** → đổi **Trạng thái** thành
`SUSPENDED` → **Lưu thay đổi**. 📸

**Chờ 10 giây.** Tab B → **Người dùng** → ↻:

| Kiểm tra | Phải thấy |
|---|---|
| Access | **`DISABLED`** |
| User ID | **vẫn là số cũ** — không sinh User thứ hai |
📸

Đổi ngược lại `ACTIVE` → Access quay về `ENABLED`.

> ✅ **Đạt INT-05** cả hai chiều.

---

## Bước 3 — INT-02: mở lớp học phần (3 phút)

**Tab A → 03 Danh mục đào tạo** → ghi lại một mã học phần và một mã giảng viên:

- Mã học phần → `______` (ví dụ `INT402`)
- Mã giảng viên → `______` (ví dụ `GV0001`)

**Tab B → Khóa học** → tìm `SEC-TEAM07-T01` → **không có**. 📸

**Tab A → 04 Lớp học phần** → **+ Mở lớp**. Hộp thoại *"Mở lớp học phần"*:

| Ô nhập | Điền |
|---|---|
| Mã lớp | `SEC-TEAM07-T01` |
| Học phần | chọn `INT402` |
| Giảng viên | chọn **`GV0001`** |
| Học kỳ | `2026-1` |
| Sĩ số tối đa | `60` |
| Ngày bắt đầu | `2026-08-15` |
| Ngày kết thúc | `2026-12-15` |
| Trạng thái | `OPEN` |

→ **Lưu thay đổi**. 📸

**Chờ 10 giây.** Tab B → **Khóa học** → ↻ → tìm `SEC-TEAM07-T01`:

| Cột | Phải thấy |
|---|---|
| External code | `SEC-TEAM07-T01` |
| Title | **`INT402 - <tên học phần>`** — phải chứa mã học phần |
| Term | `2026-1` |
| Teacher | `GV0001` |
| State | `PUBLISHED` |

**Ghi lại Course ID** → `______` (bước 4 cần đối chiếu) 📸

> ⚠️ Đừng bấm **+ Tạo khóa học** bên UniLearn. Mục 12.8 của đề: tự tạo dữ liệu ở hệ thống
> đích thì không được tính là bằng chứng tích hợp.

> ✅ **Đạt INT-02.**

---

## Bước 4 — INT-07: đổi giảng viên (2 phút)

**Tab A → 04 Lớp học phần** → tìm `SEC-TEAM07-T01` → **Điều chỉnh** → đổi **Giảng viên**
sang `GV0002` → **Lưu thay đổi**. 📸

**Chờ 10 giây.** Tab B → **Khóa học** → ↻:

| Kiểm tra | Phải thấy |
|---|---|
| Teacher | **`GV0002`** |
| Course ID | **vẫn là số ở bước 3** |
| Tổng số khóa học | **không tăng** |
📸

### Xác minh bằng Swagger — đây là chỗ Swagger có giá trị nhất

Tiêu chí *"ID LMS Course giữ nguyên"* khó chứng minh bằng ảnh danh sách, nhưng Swagger thì
chứng minh thẳng thừng.

Swagger UniLearn → `GET /api/v1/courses/{course_id}` → **Try it out** → nhập **đúng số
Course ID ghi ở bước 3** → **Execute**.

Response phải trả về `200` với:

```json
{ "id": <so cu>, "externalCode": "SEC-TEAM07-T01", "teacherExternalRef": "GV0002" }
```

Cùng một `id`, nhưng `teacherExternalRef` đã đổi. 📸 *(che token)*

> ✅ **Đạt INT-07.** Ba tiêu chí mục 17.6, trong đó tiêu chí thứ hai được chứng minh bằng
> chính ID nội bộ chứ không phải bằng suy luận.

---

## Bước 5 — INT-03: đăng ký học (2 phút)

**Tab B → Thành viên** → tìm `SEC-TEAM07-T01` → **chưa có hàng nào**. 📸

**Tab A → 05 Đăng ký học** → **+ Đăng ký**:

| Ô nhập | Chọn |
|---|---|
| Sinh viên | `SV07T01` |
| Lớp học phần | `SEC-TEAM07-T01` |

→ **Lưu thay đổi**. Ghi `enrollmentId` → `ENR-____________` 📸

**Chờ 10 giây.** Tab B → **Thành viên** → ↻:

| Cột | Phải thấy |
|---|---|
| Course | Course của `SEC-TEAM07-T01` |
| User | User của `SV07T01` |
| Role | `STUDENT` |
| Status | `ACTIVE` |
📸

> ✅ **Đạt INT-03.**

---

## Bước 6 — INT-06: đồng bộ điểm ngược (4 phút)

Đây là bước có mẹo: **phải chứng minh điểm nháp KHÔNG đồng bộ** trước đã.

**Tab A → 06 Điểm chính thức** → xác nhận chưa có điểm cho `SV07T01`. 📸

**Tab B → Sổ điểm** → **+ Ghi điểm**. Hộp thoại *"Ghi điểm"*:

| Ô nhập | Chọn / điền |
|---|---|
| Khóa học | chọn dòng có `SEC-TEAM07-T01` |
| Học viên | chọn `#<User ID> · Nguyen Thi Mai` |
| **Điểm cuối kỳ /100** | `87.5` |

→ Lưu. Bảng Sổ điểm hiện trạng thái **`BẢN NHÁP`**. 📸

### ⏸ Chờ đủ 15 giây rồi kiểm tra

**Tab A → 06 Điểm chính thức** → ↻ → **vẫn phải trống**. 📸

> Đây chính là tiêu chí nghiệm thu số 2 mục 16.6: *"Điểm chỉ đồng bộ sau thao tác publish"*.
> Rất nhiều bài bỏ qua bước này.

### Giờ mới công bố

**Tab B → Sổ điểm** → bấm **Công bố** ở dòng vừa tạo. Trạng thái đổi thành **`ĐÃ CÔNG BỐ`**. 📸

**Tab B → Tín hiệu** → tìm `grade.published`, ghi `eventId` → `evt_____________` 📸

**Chờ 10 giây.** **Tab A → 06 Điểm chính thức** → ↻:

| Cột | Phải thấy |
|---|---|
| Sinh viên | `SV07T01` |
| Lớp | `SEC-TEAM07-T01` |
| Điểm /10 | **`8.75`** ← quy đổi từ 87.5/100 |
| Điểm chữ | **`A`** ← UniSIS tự tính, mình không gửi |
| Nguồn | `LMS` |
📸

### Xác minh bằng Swagger

Swagger UniSIS <http://127.0.0.1:8001/docs> → `GET /api/v1/grades` → **Try it out** →
**Execute** → tìm bản ghi của `SV07T01`:

```json
{ "studentId": "SV07T01", "sectionId": "SEC-TEAM07-T01",
  "finalScore": 8.75, "letterGrade": "A", "source": "LMS" }
```
📸 *(che token)*

> ✅ **Đạt INT-06** và kịch bản chấm **T06**.

---

## Bước 7 — INT-08: cảnh báo học tập (3 phút)

**Tab A → 07 Cảnh báo cố vấn** → xác nhận chưa có cảnh báo cho `SV07T01`. 📸

**Tab B → Rủi ro** → **+ Đánh giá học viên**. Hộp thoại *"Đánh giá mức độ tham gia"*:

| Ô nhập | Điền |
|---|---|
| Khóa học | chọn `SEC-TEAM07-T01` |
| Học viên | chọn `#<User ID> · Nguyen Thi Mai` |
| **Hoàn thành %** | `20` |
| **Số ngày không hoạt động** | `16` |

→ Lưu. Bảng Radar phải hiện `AT_RISK`. 📸

**Tab B → Tín hiệu** → tìm `learner.at_risk`, ghi `eventId` → `evt_____________` 📸

**Chờ 10 giây.** **Tab A → 07 Cảnh báo cố vấn** → ↻:

| Cột | Phải thấy |
|---|---|
| Sinh viên | `SV07T01` |
| Lớp | `SEC-TEAM07-T01` |
| Mức | `AT_RISK` |
| Chi tiết | có **cả** `20` và `16` |
📸

### Thử thêm trường hợp NORMAL — đề yêu cầu riêng

**Tab B → Rủi ro** → **+ Đánh giá học viên** → cùng Khóa học và Học viên, nhưng:

| Ô nhập | Điền |
|---|---|
| Hoàn thành % | `50` |
| Số ngày không hoạt động | `16` |

→ Lưu. Radar hiện `NORMAL`, và **Tín hiệu KHÔNG có sự kiện mới**.

Chờ 15 giây → **Tab A → 07 Cảnh báo cố vấn** → **vẫn chỉ có 1 cảnh báo**. 📸

> ✅ **Đạt INT-08**, gồm cả tiêu chí "NORMAL không tạo alert" ở mục 18.6.

---

## Bước 8 — INT-04: huỷ đăng ký (2 phút)

**Tab A → 05 Đăng ký học** → tìm đăng ký của `SV07T01` → **Hủy đăng ký** → xác nhận.
Trạng thái chuyển `DROPPED`. 📸

**Chờ 10 giây.** Kiểm tra **ba chỗ** ở Tab B — đây là phần dễ sai nhất:

| # | Menu | Phải thấy |
|---|---|---|
| 1 | **Thành viên** | **không còn** hàng ACTIVE cho cặp này |
| 2 | **Người dùng** | User `SV07T01` **vẫn còn** |
| 3 | **Khóa học** | Course `SEC-TEAM07-T01` **vẫn còn** |
📸📸📸

### Xác minh bằng Swagger — gọn hơn ba tấm ảnh

Swagger UniLearn, gọi ba lần:

| Endpoint | Nhập | Kết quả phải có |
|---|---|---|
| `GET /api/v1/memberships` | — | **không** có cặp `courseId`+`userId` của mình |
| `GET /api/v1/users/{user_id}` | User ID bước 1 | `200` — User còn nguyên |
| `GET /api/v1/courses/{course_id}` | Course ID bước 3 | `200` — Course còn nguyên |

Hai mã `200` cuối chính là bằng chứng trực tiếp cho *"không xoá User, không xoá Course"* —
mạnh hơn ảnh danh sách, vì ảnh danh sách chỉ cho thấy "có nhìn thấy", còn `200` cho thấy
"truy vấn trực tiếp vẫn tồn tại". 📸 *(che token)*

> ✅ **Đạt INT-04.** Mục 14.6 tách riêng ba tiêu chí này, phải có đủ bằng chứng cả ba.

---

## Bước 9 — T09: chống trùng (2 phút)

Tab C (<http://localhost:8080/admin/inbox>) → tìm dòng `student.created` của `SV07T01`,
ghi trường **`id`** của nó (số nhỏ, không phải `eventId`) → `______`

**Tab D** — Swagger của Integration Service (<http://localhost:8080/swagger-ui.html>),
không cần đăng nhập:

1. Tìm mục `admin-controller` → `POST /admin/inbox/{id}/requeue`
2. Bấm vào để mở rộng → **Try it out**
3. Ô `id` → nhập số vừa ghi
4. **Execute**

Response phải là:

```json
{ "requeued": true, "id": <so vua nhap>, "eventId": "evt_..." }
```

Chờ 3 giây → Tab C làm mới:

| Kiểm tra | Phải thấy |
|---|---|
| `action` | **`NOOP`** |
| Dữ liệu bên UniLearn | **không đổi gì** |
📸

> ✅ **Đạt T09.** `NOOP` là bằng chứng trực quan nhất của tính chống trùng: hàm hội tụ đọc
> trạng thái thực tế, thấy đã đúng nên không gọi API ghi nào.

---

## Bước 10 — T10: phục hồi sau lỗi (3 phút)

Bật chế độ lỗi mô phỏng ở UniLearn:

```bash
curl -X PUT http://127.0.0.1:8002/admin/tenants/TEAM07/failure/HIGH -H "X-Admin-Key: admin-secret"
```

**Tab A → 02 Hồ sơ sinh viên** → **+ Hồ sơ mới** → tạo `SV07T02` (các ô khác điền tuỳ ý,
Trạng thái `ACTIVE`).

Tab C → làm mới liên tục → sẽ thấy `status: "RETRYING"` và `attempt` tăng dần. 📸

Tắt chế độ lỗi:

```bash
curl -X PUT http://127.0.0.1:8002/admin/tenants/TEAM07/failure/OFF -H "X-Admin-Key: admin-secret"
```

Chờ backoff (1, 2, 4, 8… giây) → dòng đó tự chuyển **`DONE`**, `retryCount > 0`. 📸

Tab B → **Người dùng** → `SV07T02` xuất hiện đầy đủ — **không mất sự kiện nào**.

> ✅ **Đạt T10.**

---

## Bước 11 — T11: đối soát sau khi reset (4 phút)

Đây là bước ấn tượng nhất khi bảo vệ.

### 11.1. Mô phỏng giảng viên khởi tạo lại dữ liệu

```bash
curl -X POST http://127.0.0.1:8001/admin/tenants/TEAM07/reset -H "X-Admin-Key: admin-secret"
```

```bash
curl -X POST http://127.0.0.1:8002/admin/tenants/TEAM07/reset -H "X-Admin-Key: admin-secret"
```

> ⚠️ Reset **xoá sạch** dữ liệu tenant, gồm cả `SV07T01` và toàn bộ bảng sự kiện. Chụp xong
> ảnh các bước trên rồi mới làm bước này.

Kết quả trả về: SIS `100 SV / 15 lớp`, LMS `80 User / 12 Course` — lệch **20 User và 3 Course**,
và **không còn sự kiện nào** để mà nghe.

### 11.2. Ba bước đối soát

**Tab D** — Swagger của Integration Service (<http://localhost:8080/swagger-ui.html>)
→ mục `admin-controller` → `POST /admin/reconcile` → **Try it out**.
Endpoint này có đúng một tham số: `dryRun`.

**Lần 1 — đặt `dryRun` = `true`** → **Execute**. Chỉ báo cáo, không ghi gì:

```json
{ "runId": "r...", "dryRun": true, "requestsUsed": 6,
  "entities": {
    "STUDENT_USER":          { "sourceCount": 100, "targetCount": 80, "missing": 20 },
    "SECTION_COURSE":        { "sourceCount": 15,  "targetCount": 12, "missing": 3, "drifted": 12 },
    "ENROLLMENT_MEMBERSHIP": { "missing": 0 }
  } }
```

Chú ý `"requestsUsed": 6` — phát hiện toàn bộ chỗ lệch chỉ tốn 6 request. 📸

**Lần 2 — đặt `dryRun` = `false`** → **Execute**. Lần này `enqueued` sẽ khác 0, và `runId`
**khác** lần trước. Chờ ~15 giây cho worker xử lý xong.

> `runId` khác nhau giữa hai lượt là có chủ đích. Nếu mã sự kiện tổng hợp đặt theo thực thể
> thay vì theo lượt chạy, ràng buộc chống trùng sẽ coi lượt thứ hai là trùng và bỏ qua sạch —
> tức là bấm đối soát trước mặt giảng viên mà không làm gì cả.

Trong lúc chờ, Tab C sẽ thấy hàng loạt dòng `src: "RECON"` chuyển dần sang `DONE`. 📸

**Lần 3 — đặt lại `dryRun` = `true`** → **Execute**:

```json
"missing": 0, "drifted": 0, "extra": 0
```
📸

Tab B → **Người dùng** và **Khóa học** → ↻ → giờ là `100 User` và `15 Course`, khớp UniSIS. 📸

> ✅ **Đạt T11 và NFR-06.** Đây là thứ mà chỉ nghe webhook thì **không bao giờ** làm được —
> phần lệch đó không có sự kiện nào tương ứng.

---

## Bảng kết quả — điền khi chạy

| Bước | Yêu cầu | Mã đã dùng | eventId | Đạt? |
|---|---|---|---|---|
| 1 | INT-01 | `SV07T01` | | ☐ |
| 2 | INT-05 | `SV07T01` | | ☐ |
| 3 | INT-02 | `SEC-TEAM07-T01` | | ☐ |
| 4 | INT-07 | `SEC-TEAM07-T01` | | ☐ |
| 5 | INT-03 | `ENR-________` | | ☐ |
| 6 | INT-06 | `87.5 → 8.75` | | ☐ |
| 7 | INT-08 | `20% / 16 ngày` | | ☐ |
| 8 | INT-04 | cùng cặp bước 5 | | ☐ |
| 9 | T09 | — | | ☐ |
| 10 | T10 | `SV07T02` | | ☐ |
| 11 | T11 | — | | ☐ |

Ghi chú thêm: `______________________________________________`

---

## Phụ lục — Swagger dùng ở những bước nào

| Bước | Swagger nào | Endpoint | Chứng minh điều gì |
|---|---|---|---|
| 0.4 | SIS + LMS | `POST /api/v1/auth/token` | Lấy token để Authorize |
| 1 | LMS | `GET /api/v1/users?externalRef=` | Đúng 1 User, có ID nội bộ |
| 4 | LMS | `GET /api/v1/courses/{course_id}` | **Course ID không đổi** sau khi đổi giảng viên |
| 6 | SIS | `GET /api/v1/grades` | `8.75`, `letterGrade=A`, `source=LMS` |
| 8 | LMS | `GET /api/v1/memberships` + `users/{id}` + `courses/{id}` | Membership mất, User và Course còn |
| 9 | Tích hợp | `POST /admin/inbox/{id}/requeue` | Chống trùng → `NOOP` |
| 11 | Tích hợp | `POST /admin/reconcile` | Đối soát ba lượt |

Ngoài ra, Swagger UniSIS còn có `GET /api/v1/events?eventType=...` để lọc nhanh sự kiện theo
loại — tiện hơn cuộn tay ở menu **08 Dòng sự kiện** khi tenant đã có nhiều dữ liệu.

Bước **4** là chỗ Swagger có giá trị nhất: tiêu chí *"ID LMS Course giữ nguyên"* gần như không
chứng minh được bằng ảnh danh sách, nhưng một lần gọi `GET /api/v1/courses/{id}` trả `200`
với đúng ID cũ và giảng viên mới thì không còn gì để tranh cãi.

---

## Nếu có bước nào không như mong đợi

Truy theo **đúng bốn tầng**, đừng đoán:

| Tầng | Câu hỏi | Xem ở đâu |
|---|---|---|
| 1 | Thao tác đã lưu chưa? | Tab A, đúng menu vừa thao tác |
| 2 | Sự kiện đã phát chưa? | Tab A → 08 Dòng sự kiện *(hoặc Tab B → Tín hiệu)* |
| 3 | Xử lý tới đâu rồi? | Tab C, hoặc `/admin/events/{eventId}` |
| 4 | Dữ liệu đích đúng chưa? | Tab B, đúng menu tương ứng |

Ba nguyên nhân hay gặp nhất:

| Triệu chứng | Nguyên nhân |
|---|---|
| Tầng 4 không đổi dù tầng 3 báo `DONE` | Trộn tenant — kiểm tra góc trên bên phải **cả hai** tab hiện `TEAM07` |
| Tầng 3 trống trơn | Integration Service chưa chạy, hoặc chờ chưa đủ 10 giây |
| Tầng 3 báo `RETRYING` mãi | Chế độ lỗi mô phỏng còn bật — tắt bằng lệnh ở bước 10 |

Bảng gỡ rối đầy đủ nằm ở `HUONG-DAN-KIEM-THU.md` mục 7.
