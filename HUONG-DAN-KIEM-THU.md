# Hướng dẫn kiểm thử thủ công — nhập gì, ở đâu, theo thứ tự nào

Tài liệu này dành cho việc **kiểm chứng trên giao diện** và **chụp ảnh bằng chứng** theo
mục 9.8 của đề. Kiểm thử tự động thì chỉ cần `mvn test` (36 ca, không cần mạng).

Phạm vi hiện có: **INT-01, INT-05, INT-02, INT-07, INT-03, INT-04** cùng hai kịch bản chấm
**T09** và **T10**.

---

## Phần 0 — Chuẩn bị (làm một lần mỗi buổi)

### 0.1. Khởi động ba tiến trình, đúng thứ tự này

| # | Chạy gì | Ở đâu | Chờ đến khi |
|---|---|---|---|
| 1 | `run_both.bat` | `C:\Users\ADMIN\Downloads\Integration_workshop` | Hai cửa sổ cmd hiện `Application startup complete` |
| 2 | Kiểm tra | Trình duyệt | <http://127.0.0.1:8001/health> và <http://127.0.0.1:8002/health> đều trả `{"status":"ok"}` |
| 3 | Run configuration **IntegrationService** | IntelliJ | Log hiện `Started IntegrationApplication` |

> Sai thứ tự thì mọi sự kiện sẽ nằm ở `RETRYING`. Không phải lỗi — bật hai hệ thống lên
> là nó tự thử lại. Nhưng bật đúng thứ tự thì đỡ rối khi đọc log.

### 0.2. Kiểm tra Integration Service nối được cả hai bên

Mở <http://localhost:8080/health?deep=true>. Phải thấy `"canAuthenticate": true` ở **cả**
`sis` lẫn `lms`. Nếu `false` → sai `CLIENT_SECRET` hoặc hai hệ thống chưa chạy.

### 0.3. Đăng nhập hai giao diện

Mở **hai tab riêng**, để cạnh nhau suốt buổi:

| | UniSIS | UniLearn |
|---|---|---|
| Địa chỉ | <http://127.0.0.1:8001/> | <http://127.0.0.1:8002/> |
| Ô thứ nhất | **Tenant ID** = `TEAM07` | **Tenant** = `TEAM07` |
| Ô thứ hai | **Client ID** = `WEB-CONSOLE` | **Client ID** = `WEB-CONSOLE` |
| Ô thứ ba | **Client Secret** = `student-secret` | **Client Secret** = `student-secret` |
| Nút | **Mở UniSIS** | **Kết nối** |

> **Kiểm tra bắt buộc:** góc trên bên phải cả hai màn hình phải hiển thị `TEAM07`.
> Trộn tenant là nguyên nhân số một khiến dữ liệu không bao giờ khớp mà log vẫn báo thành công.

### 0.4. Bản đồ menu

**UniSIS** — thanh bên trái:

| Menu | Dùng cho |
|---|---|
| 01 Tổng quan học vụ | Xem số lượng tổng thể |
| **02 Hồ sơ sinh viên** | INT-01, INT-05 |
| 03 Danh mục đào tạo | Tra `courseCode` và `lecturerId` trước khi mở lớp |
| **04 Lớp học phần** | INT-02, INT-07 |
| **05 Đăng ký học** | INT-03, INT-04 |
| 06 Điểm chính thức | INT-06 *(chưa làm)* |
| 07 Cảnh báo cố vấn | INT-08 *(chưa làm)* |
| **08 Dòng sự kiện** | Lấy `eventId` — cần cho mọi ảnh bằng chứng |
| 09 Kết nối Webhook | Chỉ khi bật chế độ webhook |

**UniLearn** — thanh bên trái:

| Menu | Dùng cho |
|---|---|
| Tín hiệu *(đầu tiên)* | Tổng quan |
| **Người dùng** | Kiểm tra kết quả INT-01, INT-05 |
| **Khóa học** | Kiểm tra kết quả INT-02, INT-07 |
| **Thành viên** | Kiểm tra kết quả INT-03, INT-04 |
| Sổ điểm | INT-06 *(chưa làm)* |
| Rủi ro | INT-08 *(chưa làm)* |

### 0.5. Ba giao diện Swagger

Đề mục 9.8 cho phép dùng **response GET từ Swagger** làm bằng chứng, bên cạnh ảnh giao diện.
Có ba Swagger khác nhau, đừng nhầm:

| Swagger | Địa chỉ | Cần đăng nhập | Dùng để |
|---|---|---|---|
| **Integration Service** | <http://localhost:8080/swagger-ui.html> | ❌ không | Bấm `requeue`, `reconcile`, `drain`, xem inbox và dead-letter |
| UniSIS | <http://127.0.0.1:8001/docs> | ✅ có | Xác minh dữ liệu **nguồn**, lọc `/events` theo loại |
| UniLearn | <http://127.0.0.1:8002/docs> | ✅ có | Xác minh dữ liệu **đích**, nhất là **ID nội bộ** |

**Swagger của Integration Service không cần đăng nhập** — mở là dùng ngay. Nó công bố 11
endpoint: `/health`, hai bộ nhận webhook, và tám endpoint `/admin/**`.

Hai Swagger của giảng viên cần Authorize một lần (token sống 12 giờ):

1. `POST /api/v1/auth/token` → **Try it out** → body:
   `{"clientId":"WEB-CONSOLE","clientSecret":"student-secret","tenantId":"TEAM07"}`
2. **Execute** → chép giá trị `accessToken` (chuỗi dài `eyJ...`)
3. Cuộn lên đầu → nút **Authorize** 🔓 → dán token → **Authorize** → **Close**

> ⚠️ Che ô token và Client Secret trong mọi ảnh chụp Swagger.

Hướng dẫn từng bước chi tiết hơn nằm ở [KICH-BAN-TEST.md](KICH-BAN-TEST.md) mục 0.4.

### 0.6. Quy ước đặt mã — bắt buộc theo mục 9.6

Mỗi lần thử dùng **mã mới**, không trùng mã đã có:

```
Sinh vien    : SV07LABnn      vi du SV07LAB03, SV07LAB04
Lop hoc phan : SEC-TEAM07-LABnn
```

**Tránh dải `SV070001` → `SV070100` và `SEC-TEAM07-01` → `SEC-TEAM07-15`** — đó là dữ liệu
seed, đụng vào sẽ nhận `409 STUDENT_EXISTS` / `409 SECTION_EXISTS`.

Ghi lại mã đã dùng vào bảng ở [Phần 6](#phần-6--bảng-ghi-test-report).

---

## Phần 1 — INT-01: Student → LMS User

### Bước 1. Chụp trạng thái TRƯỚC

1. Sang tab **UniLearn** → menu **Người dùng**
2. Gõ `SV07LAB03` vào ô tìm kiếm
3. Kết quả phải **rỗng**
4. 📸 **Ảnh 1** — màn hình Người dùng không có kết quả

### Bước 2. Tạo sinh viên ở UniSIS

Sang tab **UniSIS** → menu **02 Hồ sơ sinh viên** → bấm nút **+ Hồ sơ mới**.

Hộp thoại *"Tạo hồ sơ sinh viên"* hiện ra. Nhập chính xác:

| Ô nhập | Giá trị |
|---|---|
| **Mã sinh viên** | `SV07LAB03` |
| **Chương trình** | `CNTT` |
| **Họ & tên đệm** | `Le Van` |
| **Tên** | `Cuong` |
| **Email** | `cuong.lab03@sv.edu.vn` |
| **Khóa** | `2026` |
| **Trạng thái** | `ACTIVE` |

Bấm **Lưu thay đổi**. Danh sách phải xuất hiện hồ sơ vừa tạo.

📸 **Ảnh 2** — dòng `SV07LAB03` trong danh sách sinh viên

### Bước 3. Lấy mã sự kiện

Menu **08 Dòng sự kiện** → tìm dòng `student.created` của `SV07LAB03`.

**Ghi lại `eventId`** — dạng `evt_xxxxxxxxxxxxxxxx`. Mã này là sợi dây xuyên suốt mọi
bằng chứng còn lại.

📸 **Ảnh 3** — dòng sự kiện kèm `eventId`

### Bước 4. Xem tầng xử lý ở giữa

Hai cách, chọn một:

- **Nhanh:** mở thẳng <http://localhost:8080/admin/inbox> — trả JSON thô
- **Đẹp hơn khi trình diễn:** Swagger của Integration Service
  <http://localhost:8080/swagger-ui.html> → mục `admin-controller` → `GET /admin/inbox`
  → **Try it out** → **Execute**. Không cần đăng nhập gì.

  Muốn lọc thì điền ô `status`, ví dụ `RETRYING` để xem những sự kiện đang chờ thử lại.

Tìm dòng có `eventId` vừa ghi. Phải thấy:

```json
{ "eventType": "student.created", "businessKey": "SV07LAB03",
  "status": "DONE", "action": "CREATE", "attempt": 1, "retryCount": 0 }
```

📸 **Ảnh 4** — dòng inbox

Muốn xem toàn bộ vòng đời: <http://localhost:8080/admin/events/{eventId}>

### Bước 5. Kiểm tra kết quả ở UniLearn

Sang tab **UniLearn** → **Người dùng** → nhấn nút ↻ → tìm `SV07LAB03`.

Đối chiếu đúng 6 dòng bảng ánh xạ mục 11.2:

| Cột trên màn hình | Giá trị phải thấy |
|---|---|
| Username | `SV07LAB03` |
| External ref | `SV07LAB03` |
| Display name | `Le Van Cuong` *(họ trước, tên sau)* |
| Email | `cuong.lab03@sv.edu.vn` |
| Type | `LEARNER` |
| Access | `ENABLED` |
| User ID | một số do LMS sinh, **khác** `SV07LAB03` |

📸 **Ảnh 5** — dòng User trên UniLearn

> Dòng cuối là bằng chứng cho bất biến BV-4: ID nội bộ của LMS không bao giờ bằng mã nghiệp vụ.

### ⏱ Chờ bao lâu?

Chế độ polling: tối đa **10 giây**. Nếu sau 15 giây chưa thấy, xem
`/admin/inbox` — nếu `status=RETRYING` thì UniLearn đang lỗi; nếu không có dòng nào
thì poller chưa chạy.

Không muốn chờ: gọi `POST /admin/drain` để ép xử lý ngay.

---

## Phần 2 — INT-05: trạng thái Student → Access của LMS User

Làm tiếp trên chính `SV07LAB03`.

### Bước 1. Đổi trạng thái ở UniSIS

Menu **02 Hồ sơ sinh viên** → tìm `SV07LAB03` → bấm **Sửa** ở cuối dòng.

Hộp thoại *"Điều chỉnh sinh viên"*: đổi ô **Trạng thái** từ `ACTIVE` sang `SUSPENDED`.
Bấm **Lưu thay đổi**.

Thông báo *"Đã cập nhật và phát student.updated"* sẽ hiện ra.

📸 **Ảnh 6** — hồ sơ với trạng thái `SUSPENDED`

### Bước 2. Kiểm tra ở UniLearn

**Người dùng** → ↻ → tìm `SV07LAB03`:

| Cột | Trước | Sau |
|---|---|---|
| Access | `ENABLED` | **`DISABLED`** |
| User ID | *(số X)* | **vẫn là số X** — không sinh User thứ hai |

📸 **Ảnh 7** — Access đã chuyển `DISABLED`

### Bước 3. Đổi ngược lại

Sửa trạng thái về `ACTIVE` → Access phải quay lại `ENABLED`. Đây là tiêu chí T05 của đề:
*"đổi lại ACTIVE → true"*.

### Bước 4. Thử đổi email

Sửa **Email** thành `cuong.moi@sv.edu.vn` → UniLearn phải cập nhật email trên **cùng User**,
tuyệt đối không tạo User mới. Đây là tiêu chí nghiệm thu số 2 của mục 11.6.

---

## Phần 3 — INT-02: Section → LMS Course

### Bước 1. Tra thông tin cần dùng

Menu **03 Danh mục đào tạo**. Ghi lại:

- Một **Mã học phần**, ví dụ `INT402` (Kien truc va tich hop he thong)
- Một **Mã giảng viên**, ví dụ `GV0001`

### Bước 2. Chụp trạng thái TRƯỚC

UniLearn → **Khóa học** → tìm `SEC-TEAM07-LAB03` → phải **rỗng**.

📸 **Ảnh 8**

### Bước 3. Mở lớp ở UniSIS

Menu **04 Lớp học phần** → bấm **+ Mở lớp**.

Hộp thoại *"Mở lớp học phần"*:

| Ô nhập | Giá trị |
|---|---|
| **Mã lớp** | `SEC-TEAM07-LAB03` |
| **Học phần** | chọn `INT402` từ danh sách thả xuống |
| **Giảng viên** | chọn `GV0001` từ danh sách thả xuống |
| **Học kỳ** | `2026-1` |
| **Sĩ số tối đa** | `60` |
| **Ngày bắt đầu** | `2026-08-15` |
| **Ngày kết thúc** | `2026-12-15` |
| **Trạng thái** | `OPEN` |

Bấm **Lưu thay đổi**.

📸 **Ảnh 9** — lớp vừa mở trong danh sách

### Bước 4. Lấy mã sự kiện

**08 Dòng sự kiện** → tìm `section.created` của `SEC-TEAM07-LAB03` → ghi `eventId`.

📸 **Ảnh 10**

### Bước 5. Kiểm tra ở UniLearn

**Khóa học** → ↻ → tìm `SEC-TEAM07-LAB03`. Đối chiếu bảng ánh xạ mục 12.2:

| Cột | Giá trị phải thấy |
|---|---|
| External code | `SEC-TEAM07-LAB03` |
| Title | `INT402 - Kien truc va tich hop he thong` *(phải chứa mã học phần)* |
| Term | `2026-1` |
| Teacher | `GV0001` |
| State | `PUBLISHED` |

📸 **Ảnh 11**

> **Ghi lại số Course ID** hiển thị trên màn hình — Phần 4 cần đối chiếu nó.

> ⚠️ Theo mục 12.8 của đề: **không bấm nút "Tạo khóa học"** bên UniLearn khi đang chứng
> minh INT-02. Tự tạo dữ liệu ở hệ thống đích thì không được tính là bằng chứng tích hợp.

---

## Phần 4 — INT-07: đổi giảng viên phụ trách

### Bước 1. Đổi ở UniSIS

Menu **04 Lớp học phần** → tìm `SEC-TEAM07-LAB03` → bấm **Điều chỉnh**.

Hộp thoại *"Điều chỉnh lớp học phần"*: đổi ô **Giảng viên** từ `GV0001` sang `GV0002`.
Bấm **Lưu thay đổi**.

Thông báo *"Lớp đã cập nhật, section.updated được phát"*.

📸 **Ảnh 12**

### Bước 2. Kiểm tra ở UniLearn

**Khóa học** → ↻ → `SEC-TEAM07-LAB03`:

| Kiểm tra | Kết quả phải có |
|---|---|
| Teacher | đổi thành `GV0002` |
| **Course ID** | **vẫn là số cũ** ở Phần 3 bước 5 |
| Tổng số khóa học | **không tăng** — không sinh Course trùng |

📸 **Ảnh 13**

Đây là ba tiêu chí nghiệm thu mục 17.6.

> **Tiêu chí "ID LMS Course giữ nguyên" gần như không chứng minh được bằng ảnh danh sách** —
> ảnh chỉ cho thấy có một dòng, không cho thấy đó vẫn là bản ghi cũ. Đây là chỗ Swagger đáng
> giá nhất trong cả bài.

Swagger UniLearn <http://127.0.0.1:8002/docs> → `GET /api/v1/courses/{course_id}` →
**Try it out** → nhập **đúng số Course ID đã ghi trước khi đổi giảng viên** → **Execute**.

Phải trả `200` với cùng `id` cũ nhưng `teacherExternalRef` đã là `GV0002`:

```json
{ "id": <so cu>, "externalCode": "SEC-TEAM07-LAB03", "teacherExternalRef": "GV0002" }
```

📸 **Ảnh 14** — response từ Swagger *(nhớ che token)*

---

## Phần 4b — INT-03 và INT-04: đăng ký học và huỷ học

Cần chuẩn bị trước: một lớp đã đồng bộ sang LMS (làm xong Phần 3).

### Bước 1. Chụp trạng thái TRƯỚC

UniLearn → **Thành viên** → tìm theo mã lớp `SEC-TEAM07-LAB03` → chưa có hàng nào cho
sinh viên sắp đăng ký.

📸 **Ảnh 18**

### Bước 2. Đăng ký học ở UniSIS

Menu **05 Đăng ký học** → bấm **+ Đăng ký**.

| Ô nhập | Giá trị |
|---|---|
| **Sinh viên** | chọn `SV07LAB03` từ danh sách thả xuống |
| **Lớp học phần** | chọn `SEC-TEAM07-LAB03` từ danh sách thả xuống |

Bấm **Lưu thay đổi**. Thông báo *"Đăng ký thành công"*.

**Ghi lại `enrollmentId`** — dạng `ENR-XXXXXXXXXX`.

📸 **Ảnh 19** — dòng đăng ký vừa tạo

### Bước 3. Lấy mã sự kiện

**08 Dòng sự kiện** → tìm `enrollment.created` chứa đúng `studentId` và `sectionId`.

📸 **Ảnh 20**

### Bước 4. Kiểm tra ở UniLearn

**Thành viên** → ↻ → tìm cặp vừa đăng ký:

| Cột | Giá trị phải thấy |
|---|---|
| Course | Course của `SEC-TEAM07-LAB03` |
| User | User của `SV07LAB03` |
| Role | `STUDENT` |
| Status | `ACTIVE` |

📸 **Ảnh 21**

> ⚠️ Mục 13.8 của đề: **không bấm nút "Thêm thành viên"** bên UniLearn khi đang chứng minh
> INT-03. Tự tạo membership bằng tay thì không được tính là bằng chứng.

### Bước 5 *(tuỳ chọn nhưng rất đáng làm)* — thử tự khôi phục phụ thuộc

Chọn một sinh viên trong dải `SV070081` → `SV070100`. Đây là phần lệch **cố ý** của dữ liệu
seed: SIS có 100 Student nhưng LMS chỉ có 80 User, nên những em này **chưa có LMS User**.

Đăng ký một em trong số đó vào lớp. Kết quả phải là: Integration Service **tự tạo LMS User
trước** rồi mới thêm thành viên — cả hai việc trong cùng một lượt xử lý.

Xem log sẽ thấy dòng:

```
key=SV070081 chua co LMS User, goi ensureUser truoc khi them thanh vien
```

Đây là bằng chứng cho tiêu chí nghiệm thu số 2 của mục 13.6.

📸 **Ảnh 22** — dòng log tự khôi phục phụ thuộc

### Bước 6. Huỷ đăng ký (INT-04)

Menu **05 Đăng ký học** → tìm đăng ký vừa tạo → bấm **Hủy đăng ký** → xác nhận.

Trạng thái ở UniSIS chuyển `DROPPED`.

📸 **Ảnh 23**

### Bước 7. Kiểm tra ba điều ở UniLearn — đây là phần dễ sai nhất

| # | Kiểm tra ở đâu | Kết quả phải có |
|---|---|---|
| 1 | **Thành viên** → ↻ | **không còn** hàng ACTIVE cho cặp đó |
| 2 | **Người dùng** | User của sinh viên **vẫn còn** |
| 3 | **Khóa học** | Course tương ứng **vẫn còn** |

📸 **Ảnh 24, 25, 26** — lần lượt ba màn hình trên

> Đề mục 14.6 nêu rõ: chỉ gỡ quan hệ Membership, **không xoá** User và **không xoá** Course.
> Đây là ba tiêu chí riêng biệt, phải chụp đủ cả ba.

**Xác minh bằng Swagger — mạnh hơn ba tấm ảnh.** Swagger UniLearn, gọi ba lần:

| Endpoint | Nhập | Kết quả phải có |
|---|---|---|
| `GET /api/v1/memberships` | — | **không** còn cặp `courseId`+`userId` của mình |
| `GET /api/v1/users/{user_id}` | User ID | `200` — User còn nguyên |
| `GET /api/v1/courses/{course_id}` | Course ID | `200` — Course còn nguyên |

Ảnh danh sách chỉ nói "có nhìn thấy"; hai mã `200` nói "truy vấn trực tiếp vẫn tồn tại".

### Bước 8. Huỷ lặp lần hai

Giao lại sự kiện drop bằng `POST /admin/inbox/{id}/requeue` → kết quả phải là `action=NOOP`,
và User/Course vẫn nguyên. Đây là tiêu chí nghiệm thu số 4 của mục 14.6.

---

## Phần 5 — Hai kịch bản chấm

### T09 — Chống trùng

Cách dễ nhất, làm hoàn toàn trên Swagger của Integration Service — không cần đăng nhập,
không cần công cụ ngoài:

1. Mở <http://localhost:8080/swagger-ui.html> → `GET /admin/inbox` → **Try it out** →
   **Execute**. Tìm một sự kiện `"status": "DONE"` và ghi trường **`id`** của nó
   (số nhỏ, **không phải** `eventId`)
2. `POST /admin/inbox/{id}/requeue` → **Try it out** → nhập số đó → **Execute**.
   Response: `{ "requeued": true, "id": ..., "eventId": "evt_..." }`
3. Chờ 2 giây → gọi lại `GET /admin/inbox`

Nếu thích dòng lệnh thì tương đương:

```bash
curl -X POST http://localhost:8080/admin/inbox/1/requeue
```

**Kết quả phải thấy:** `action` chuyển từ `CREATE` (hoặc `UPDATE`) thành **`NOOP`**, và
UniLearn **không thay đổi gì**.

📸 **Ảnh 15** — dòng inbox có `action=NOOP`

Đây là bằng chứng trực quan nhất của tính chống trùng: hàm hội tụ đọc trạng thái thực tế,
thấy đã đúng nên không gọi API ghi nào.

### T10 — Phục hồi sau lỗi tạm thời

Bật chế độ lỗi mô phỏng của hệ thống nguồn:

```bash
curl -X PUT http://127.0.0.1:8002/admin/tenants/TEAM07/failure/HIGH -H "X-Admin-Key: admin-secret"
```

Mức `HIGH` = 30% request ghi trả về 503.

1. Tạo một sinh viên mới `SV07LAB04` ở UniSIS
2. Xem `/admin/inbox` — sẽ thấy `status=RETRYING`, `attempt` tăng dần
3. Tắt chế độ lỗi:

```bash
curl -X PUT http://127.0.0.1:8002/admin/tenants/TEAM07/failure/OFF -H "X-Admin-Key: admin-secret"
```

4. Chờ backoff (1, 2, 4, 8… giây) → sự kiện tự chuyển `DONE`
5. Kiểm tra UniLearn: User xuất hiện đầy đủ, **không mất sự kiện nào**

📸 **Ảnh 16** — dòng inbox `RETRYING` với `retryCount > 0`
📸 **Ảnh 17** — cùng dòng đó sau khi thành `DONE`

> Endpoint `/admin/...` dùng khoá `X-Admin-Key` của giảng viên, không nằm trong Phụ lục A
> của đề. Trên bản chạy cục bộ thì dùng được; ở môi trường lab dùng chung nên hỏi giảng
> viên trước.

---

## Phần 6 — Bảng ghi Test
Report

Đề mục 9.6 yêu cầu ghi lại mã đã dùng. Chép bảng này vào Test Report và điền dần:

| Ngày | INT | Mã đã dùng | eventId | Kết quả | Ảnh |
|---|---|---|---|---|---|
| | INT-01 | `SV07LAB03` | | | 1–5 |
| | INT-05 | `SV07LAB03` | | | 6–7 |
| | INT-02 | `SEC-TEAM07-LAB03` | | | 8–11 |
| | INT-07 | `SEC-TEAM07-LAB03` | | | 12–14 |
| | INT-03 | `SV07LAB03` + `SEC-TEAM07-LAB03` | | | 18–22 |
| | INT-04 | cùng cặp trên | | | 23–26 |
| | T09 | | | | 15 |
| | T10 | `SV07LAB04` | | | 16–17 |

---

## Phần 7 — Khi kết quả không như mong đợi

Truy theo **đúng bốn tầng** của mục 9.8, đừng đoán:

| Tầng | Câu hỏi | Xem ở đâu |
|---|---|---|
| 1. Trạng thái nguồn | Thao tác đã lưu thành công chưa? | UniSIS → 02 Hồ sơ sinh viên / 04 Lớp học phần |
| 2. Sự kiện nguồn | Sự kiện đã phát chưa? `eventId` là gì? | UniSIS → 08 Dòng sự kiện |
| 3. Xử lý tích hợp | Nhận chưa? Trạng thái nào? Thử lại mấy lần? | `/admin/inbox` và `/admin/events/{eventId}` |
| 4. Trạng thái đích | Dữ liệu đích đúng khoá nghiệp vụ chưa? | UniLearn → Người dùng / Khóa học |

Các triệu chứng hay gặp:

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Tầng 2 không có sự kiện | Thao tác chưa lưu thành công, hoặc đang xem nhầm tenant |
| Tầng 3 không có dòng nào | Poller chưa chạy, hoặc Integration Service chưa khởi động |
| Tầng 3 `RETRYING` | Hệ thống đích đang lỗi hoặc chế độ lỗi mô phỏng đang bật |
| Tầng 3 `DEAD_LETTER` | Lỗi dữ liệu — đọc `lastError`, sửa xong thì `requeue` |
| Tầng 3 `action=NOOP` mà tầng 4 trống | Đang chạy bản jar cũ; trong IntelliJ bấm Run là tự biên dịch lại |
| Tầng 4 vẫn không đổi | Kiểm tra tenant hiển thị ở góc trên bên phải **cả hai** giao diện |

Khi `/admin/inbox` chưa đủ để kết luận, xuống thẳng cơ sở dữ liệu — xem Phần 7b.

---

## Phần 7b — Xem thẳng cơ sở dữ liệu H2

Dùng khi cần nhìn sâu hơn `/admin/inbox`: đối chiếu mốc thời gian, xem bảng ánh xạ ID,
hoặc chứng minh trước giảng viên rằng dữ liệu thật sự nằm trong hàng đợi bền chứ không
phải xử lý trong bộ nhớ.

Cơ sở dữ liệu là một file duy nhất: `data/integration.mv.db`.

### 7b.1. Nối vào bằng IntelliJ — **không cần khởi động lại**

Tab **Database** (bên phải) → **+** → **Data Source** → **H2**:

| Ô | Điền |
|---|---|
| URL | `jdbc:h2:file:C:/Users/ADMIN/IdeaProjects/uni-integration-service/data/integration;AUTO_SERVER=TRUE` |
| User | `sa` |
| Password | *(để trống)* |

Bấm **Test Connection** → **OK**.

> **`AUTO_SERVER=TRUE` là phần bắt buộc.** Thiếu tham số này sẽ báo
> *"Database may be already in use"* vì Integration Service đang giữ file. Có nó thì hai
> bên dùng chung được, khỏi phải tắt dịch vụ đang chạy.
>
> Đường dẫn dùng dấu `/`, **không** dùng `\`, và **không** kèm đuôi `.mv.db`.

### 7b.2. Nối bằng H2 Console trên trình duyệt — phải bật profile `dev`

Console cố ý **tắt** ở cấu hình mặc định (`application.yml`) — không nên phơi màn hình
chạy SQL tự do ở bản chạy thật. Bật bằng cách khởi động lại với profile `dev`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Trong IntelliJ: **Run** → **Edit Configurations…** → ô **Active profiles** điền `dev`.

Rồi mở `http://localhost:8080/h2-console` và điền đúng ba ô như bảng 7b.1.
Profile `dev` đồng thời bật log mức `DEBUG`.

### 7b.3. Năm bảng nghiệp vụ

| Bảng | Nội dung | Xem khi nào |
|---|---|---|
| `inbox_event` | Mọi sự kiện đã nhận, trạng thái, số lần thử | Tầng 3 của quy trình gỡ lỗi |
| `id_mapping` | Bộ nhớ đệm ánh xạ ID nguồn → đích | Nghi ngờ ánh xạ sai hoặc mất |
| `integration_log` | Nhật ký kiểm toán từng bước | Cần dựng lại toàn bộ vòng đời |
| `dead_letter` | Sự kiện lỗi dữ liệu, kèm lý do | Có dòng `DEAD_LETTER` ở tầng 3 |
| `poll_checkpoint` | Mốc thời gian polling của SIS và LMS | Poller có vẻ đứng yên |

Bảng thứ sáu — `flyway_schema_history` — là của Flyway, không phải dữ liệu nghiệp vụ.
Riêng bảng này Flyway tạo với **tên viết thường**, nên trong H2 phải đặt nháy kép cho
cả tên bảng lẫn tên cột, nếu không sẽ báo *"Table not found"*:

```sql
SELECT "version", "description", "success" FROM "flyway_schema_history"
ORDER BY "installed_rank";
```

Kết quả đúng phải là ba dòng, cột `success` đều `TRUE`: bảng lịch sử, `1 init`,
`2 drop alert dedup`.

### 7b.4. Câu truy vấn dùng được ngay

Tổng quan sức khoẻ — chạy trước khi bắt đầu buổi kiểm thử:

```sql
SELECT status, COUNT(*) FROM inbox_event GROUP BY status;
```

Kết quả mong đợi: chỉ có `DONE`. Còn `RETRYING` nghĩa là còn việc đang chạy dở; có
`DEAD_LETTER` thì đọc tiếp truy vấn bên dưới.

Toàn bộ vòng đời một sự kiện — thay `eventId` bằng mã lấy ở tầng 2:

```sql
SELECT event_id, event_type, business_key, status, last_action, attempt,
       occurred_at, received_at, completed_at
FROM inbox_event WHERE event_id = 'evt_...';
```

Lý do vào dead-letter:

```sql
SELECT e.event_id, e.event_type, d.reason, d.created_at
FROM dead_letter d JOIN inbox_event e ON e.id = d.inbox_event_id;
```

Bảng ánh xạ của một sinh viên hoặc một lớp:

```sql
SELECT * FROM id_mapping WHERE source_key LIKE '%SV07LAB03%';
```

Poller còn tiến hay đã đứng:

```sql
SELECT * FROM poll_checkpoint;
```

Cột `updated_at` phải nhích lên sau mỗi 10 giây. Đứng yên nghĩa là poller đã tắt hoặc
không gọi được nguồn.

### 7b.5. Mốc thời gian trong H2 là **giờ Việt Nam**

Đây là điểm hay gây rối khi đối chiếu ảnh chụp. Cùng một sự kiện:

| Nơi xem | Hiển thị |
|---|---|
| UniSIS → 08 Dòng sự kiện | `2026-08-23T02:11:28` |
| H2 `inbox_event.occurred_at` | `2026-08-23 09:11:28` |

**Cả hai đều đúng, lệch nhau 7 tiếng.** Nguồn lưu giờ UTC nhưng SQLite làm rơi nhãn múi
giờ, nên giao diện của giảng viên hiển thị số UTC như thể là giờ địa phương. H2 mới là
bên hiện đúng giờ đồng hồ treo tường.

Nhờ vậy H2 đọc được độ trễ đầu-cuối một cách trực tiếp:

```
occurred_at  09:11:28   sự kiện xảy ra ở UniSIS
received_at  09:11:37   Integration Service nhận, 9 giây sau
completed_at 09:11:37   xử lý xong, dưới 1 giây
```

Con số 9 giây khớp với chu kỳ polling 10 giây — **dùng được làm bằng chứng NFR-01**.

### 7b.6. Hai điều không được làm

**Không sửa dữ liệu bằng tay để "chữa" một ca kiểm thử.** Sự kiện kẹt thì dùng
`POST /admin/inbox/{id}/requeue`. Sửa thẳng bảng là làm hỏng chính thứ đang cần chứng minh.

**Không chụp ảnh cột `payload`** khi chưa đọc lại — cột này chứa nguyên văn JSON của
nguồn. Quy tắc che bí mật ở Phần 8 áp dụng cho cả ảnh chụp cơ sở dữ liệu.

### 7b.7. Xoá sạch để chạy lại từ đầu

Tắt Integration Service rồi xoá thư mục `data/`. Flyway sẽ dựng lại lược đồ ở lần khởi
động kế tiếp.

Mất luôn bảng `id_mapping`, nhưng **không sao** — mục 5.4: ánh xạ chỉ là bộ nhớ đệm, hàm
hội tụ tra lại được từ khoá nghiệp vụ. Đây chính là điều ca kiểm thử `I10` chứng minh.

---

## Phần 8 — Quy tắc bằng chứng

Theo mục 9.8, mỗi INT trong báo cáo cần tối thiểu:

1. Ảnh trạng thái **trước** ở hệ thống đích
2. Ảnh thao tác nghiệp vụ ở hệ thống **nguồn**
3. Ảnh sự kiện nguồn kèm `eventId`
4. Đoạn log Integration Service **chứa đúng `eventId` đó** và ánh xạ ID nguồn → đích
5. Ảnh trạng thái **sau** ở hệ thống đích

**Che toàn bộ access token và client secret** trong mọi ảnh chụp và đoạn log.

Nhắc lại quy tắc mục 9.7: với INT-01, INT-02, INT-05, INT-07 thì thao tác **phải phát sinh
ở UniSIS**, dữ liệu bên UniLearn **phải do Integration Service tạo**. Tự bấm tạo tay bên
UniLearn thì thao tác đó không được xem là bằng chứng tích hợp.
