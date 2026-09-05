# Test Report — Integration Service UniSIS ↔ UniLearn

Báo cáo kiểm thử cho dự án kết thúc môn **Kiến trúc và Tích hợp hệ thống**.
Lập theo mục 21 (kịch bản kiểm thử và bảo vệ) và mục 21.1 (evidence cần trình bày) của đề.

| Hạng mục | Giá trị |
|---|---|
| Tenant | `TEAM07` |
| UniSIS | `http://127.0.0.1:8001` |
| UniLearn | `http://127.0.0.1:8002` |
| Integration Service | `http://localhost:8080` |
| Kỳ thu thập bằng chứng | 15/08/2026 – 23/08/2026 |
| Cơ chế kích hoạt | polling 10 giây + đối soát; webhook tắt |
| Kiểm thử tự động | **84/84 xanh**, `BUILD SUCCESS` |
| Hình thức | Làm cá nhân |

---

## 1. Kết luận tóm tắt

**8/8 yêu cầu tích hợp đã hoàn thành và đã chạy thật**, có dấu vết trong hàng đợi bền.
Bộ đối soát hoạt động đúng: phát hiện 35 chỗ lệch, chữa hết, dry-run lại còn 0.

Bảng dưới đây trích từ chính cơ sở dữ liệu của Integration Service — mỗi dòng là bằng
chứng một luồng đã thực sự chạy, không phải chỉ có mã nguồn:

| Nguồn | Loại sự kiện | Số lần | Phục vụ |
|---|---|---|---|
| SIS | `student.created` | 8 | INT-01 |
| SIS | `student.updated` | 1 | INT-05 |
| SIS | `section.created` | 2 | INT-02 |
| SIS | `section.updated` | 2 | INT-07 |
| SIS | `enrollment.created` | 4 | INT-03 |
| SIS | `enrollment.dropped` | 2 | INT-04 |
| **LMS** | `grade.published` | 1 | INT-06 *(luồng ngược)* |
| **LMS** | `learner.at_risk` | 1 | INT-08 *(luồng ngược)* |
| RECON | `student.updated` | 20 | Đối soát |
| RECON | `section.updated` | 15 | Đối soát |

Có mặt **cả hai chiều** — 8 loại sự kiện nghiệp vụ từ SIS và LMS, cộng sự kiện tổng hợp
do đối soát sinh ra.

---

## 2. Đối chiếu 12 kịch bản chấm — mục 21

| Mã | Thao tác của GV | Kết quả phải chứng minh | Trạng thái | Bằng chứng |
|---|---|---|---|---|
| **T01** | Tạo Student mới trên SIS | LMS có đúng 01 User, `externalRef` = `studentId` | ✅ chạy thật | `evt_0d017f8fa6da41cd` |
| **T02** | Tạo Section mới trên SIS | LMS có Course đúng `externalCode` và `term` | ✅ chạy thật | `evt_a556d20383f04d11` |
| **T03** | Enroll Student vào Section | Membership STUDENT ACTIVE đúng User/Course | ✅ chạy thật | `evt_4283eb14af7f40de` |
| **T04** | Drop enrollment | Membership bị gỡ; User/Course còn nguyên | ✅ chạy thật | `evt_0024acb3ac9c45d5` |
| **T05** | ACTIVE → SUSPENDED | `enabled=false`; đổi lại thì `true` | ✅ chạy thật | `evt_c815b884d41e4204` |
| **T06** | Nhập 87.5 trên LMS rồi Publish | SIS có `finalScore=8.75` đúng Student/Section | ✅ chạy thật | `evt_96fbc70dd94b441e` |
| **T07** | Đổi `lecturerId` của Section | Course giữ nguyên `id`, `teacherExternalRef` đổi | ✅ chạy thật | `evt_4a29f2c9ca474aff` |
| **T08** | Tạo risk 20% / 16 ngày | SIS có 01 Advising Alert `AT_RISK` | ✅ chạy thật | `evt_54fbd2970fee424f` |
| **T09** | Giao lại cùng `eventId` | Không nhân đôi User/Course/Membership/Alert | ✅ chạy thật | 4 lần `action=NOOP` |
| **T10** | LMS/SIS tạm trả 503 rồi hoạt động lại | Event pending được retry và cuối cùng đồng bộ đúng | ⚠️ **chỉ kiểm thử tự động** | `I04`, `I26`, `I46`, `I54` |
| **T11** | Reset dữ liệu khi không có event lịch sử | Service phát hiện và chữa Student/Section thiếu | ✅ chạy thật | 35 → 0 |
| **T12** | Tạo tải gọi API hoặc **bật test tương ứng** | Tôn trọng 429/`Retry-After`, không crash/retry loop | ✅ theo đúng cách đề cho phép | `I63`, `I64` |

### Ghi chú trung thực về T10 và T12

**T10 chưa có bản ghi chạy thật trong cơ sở dữ liệu hiện tại.** Truy vấn
`SELECT attempt, COUNT(*) FROM inbox_event GROUP BY attempt` cho kết quả **56 sự kiện đều
`attempt=1`**, và `integration_log` không có dòng nào `status='RETRYING'`. Nghĩa là trong
kỳ thu thập này chưa lần nào hai hệ thống nguồn thật sự trả 503.

Cơ chế thử lại **đã được kiểm chứng đầy đủ bằng bốn ca tự động** với WireMock giả lập 503.
Muốn có thêm bản ghi chạy thật thì bật chế độ lỗi mô phỏng của giảng viên rồi làm theo
`HUONG-DAN-KIEM-THU.md` Phần 5 — T10.

**T12 dùng đúng cách đề cho phép.** Nguyên văn mục 21: *"Tạo tải gọi API **hoặc bật test
tương ứng**"*. Hai ca `I63`, `I64` kiểm chứng dịch vụ đọc `Retry-After` và không đốt ngân
sách thử lại khi gặp 429.

---

## 3. Mã nghiệp vụ đã dùng — mục 9.6

Đề yêu cầu dùng mã mới, dễ nhận diện theo tenant, không trùng dữ liệu mẫu.

| Loại | Mã đã dùng |
|---|---|
| Sinh viên | `SV07LAB02`, `SV07LAB03`…`SV07LAB06`, `SV07V8` |
| Sinh viên lấy từ phần lệch cố ý của seed | `SV070081` |
| Lớp học phần | `SEC-TEAM07-LAB02`, `SEC-TEAM07-LAB03`, `SEC-TEAM07-V8` |
| Học phần | `INT402` — Kien truc va tich hop he thong |
| Giảng viên | `GV0001` → `GV0002` |
| Enrollment | `ENR-A3CBDA1221` |

Việc chọn `SV070081` là có chủ đích: seed tạo 100 Student bên SIS nhưng chỉ 80 User bên
LMS. Dùng một mã thuộc phần lệch để kiểm chứng tình huống khó nhất của mục 13.5 — **sự kiện
đăng ký đến khi phụ thuộc chưa tồn tại**.

---

## 4. Bằng chứng bốn tầng — mục 9.8

Mỗi INT được truy theo đúng bốn tầng: trạng thái nguồn → sự kiện nguồn → xử lý tích hợp →
trạng thái đích. Hồ sơ chi tiết nằm ở `docs/evidence/`.

### 4.1. INT-01 — Student → LMS User

```
Tầng 1  UniLearn GET /users?externalRef=SV07LAB02   -> []
Tầng 2  UniSIS   POST /api/v1/students               -> evt_0d017f8fa6da41cd
Tầng 3  evt=evt_0d017f8fa6da41cd fn=ensureUser key=SV07LAB02
        action=CREATE target=1 attempt=1 dur=76ms status=DONE
Tầng 4  UniLearn id=1, username=SV07LAB02, displayName="Tran Thi Binh",
                 userType=LEARNER, enabled=true, externalRef=SV07LAB02
```

Sáu trường ánh xạ theo mục 11.2 đều đúng. `id=1` là ID nội bộ do UniLearn sinh, **khác**
`studentId` — đúng bất biến BV-4.

### 4.2. INT-05 — Trạng thái Student → Access

```
UniSIS  PATCH /students/SV07LAB02 {"status":"SUSPENDED"}
UniLearn id=1 KHÔNG đổi, enabled=false
```

Không sinh User thứ hai — bất biến BV-1.

### 4.3. INT-02 và INT-07 — Section → Course, đổi giảng viên

```
INT-02  evt_a556d20383f04d11  action=CREATE  target=13
        externalCode=SEC-TEAM07-LAB02, term=2026-1, state=PUBLISHED,
        title="INT402 - Kien truc va tich hop he thong", teacherExternalRef=GV0001

INT-07  evt_4a29f2c9ca474aff  action=UPDATE  target=13
        teacherExternalRef GV0001 -> GV0002, id giữ nguyên 13
```

Tổng Course của TEAM07 sau thao tác: **13** = 12 seed + 1 mới. Không có Course trùng.

INT-07 **không cần một dòng mã riêng nào** — đổi giảng viên chỉ là một trường hợp chênh
lệch mà `ensureCourse` vốn đã xử lý.

### 4.4. INT-03 và INT-04 — Enrollment → Membership

```
INT-03  evt_4283eb14af7f40de  action=CREATE  target=13/81  dur=204ms
        "SV070081 chua co LMS User, goi ensureUser truoc khi them thanh vien"

INT-04  evt_0024acb3ac9c45d5  action=DELETE  target=13/81  dur=41ms
        "go SV070081 khoi SEC-TEAM07-LAB02 — User va Course GIU NGUYEN"
```

Đây là bằng chứng cho tiêu chí khó nhất của mục 13.6: sự kiện đến trước khi phụ thuộc tồn
tại vẫn xử lý được. Phụ thuộc được khôi phục **ngay trong lượt xử lý**, không cần hàng chờ
riêng — đó là lý do `dur=204ms` cao hơn các luồng khác.

Điểm dễ sai nhất của INT-04 là gỡ quan hệ thành viên **chứ không xoá** User/Course. Mã nguồn
chỉ gọi `DELETE /courses/{id}/members/{userId}`; ca `I37` khẳng định hai lời gọi
`DELETE /users/{id}` và `DELETE /courses/{id}` **không hề xảy ra**.

### 4.5. INT-06 — Điểm đã Publish → UniSIS *(luồng ngược)*

```
evt=evt_96fbc70dd94b441e src=LMS fn=syncGrade key=SV07V8|SEC-TEAM07-V8
action=UPDATE dur=28ms status=DONE
msg=ghi diem chinh thuc 87.5/100 -> 8.75/10 cho SV07V8 tai SEC-TEAM07-V8
    (source=LMS, upsert, letterGrade de UniSIS tu tinh)
```

Hai điểm đáng bảo vệ:

**Điểm DRAFT không đồng bộ.** Đã chờ 12 giây — dài hơn một chu kỳ polling — UniSIS vẫn
chưa có điểm. `SyncGradeHandler` chỉ đăng ký loại `grade.published`, nên thao tác lưu nháp
không thể gây ra bất kỳ lời gọi nào. Đây là tiêu chí nghiệm thu số 2 của mục 16.6.

**Cố ý không gửi `letterGrade`.** Để UniSIS tự tính `A` từ `8.75`. Quy đổi thang điểm dùng
`BigDecimal` với `HALF_UP`, không dùng số thực — vì đây là điểm số chính thức của sinh viên,
và làm tròn nhị phân cho kết quả không ổn định ở chữ số thứ hai.

### 4.6. INT-08 — Learning Risk → Advising Alert *(luồng ngược)*

```
evt=evt_54fbd2970fee424f src=LMS fn=raiseAdvisingAlert key=SV07V8|SEC-TEAM07-V8
action=CREATE dur=33ms status=DONE
msg=tao canh bao AT_RISK cho SV07V8 tai SEC-TEAM07-V8
    (Tien do hoc tap 20%, khong hoat dong 16 ngay (nguon: UniLearn, riskId=...))
```

`details` chứa **cả tiến độ lẫn số ngày không hoạt động** — đủ để cố vấn hiểu nguyên nhân
mà không phải mở UniLearn.

**Chống trùng cảnh báo bằng cách hỏi chính hệ thống đích.** Ràng buộc `UNIQUE` trên inbox
chặn được cùng một `eventId` giao nhiều lần, nhưng không chặn được tình huống thực tế hơn:
một sinh viên bị đánh giá lại nhiều lần, mỗi lần một `eventId` khác, và cố vấn nhận về mười
cảnh báo giống hệt. Vì vậy trước khi ghi, dịch vụ hỏi UniSIS xem đã có cảnh báo nào đang mở
cho cùng `(studentId, sectionId, riskType)` chưa.

Cách này tính được cả cảnh báo do người khác tạo, và cả cảnh báo còn sót từ lần chạy trước
khi cơ sở dữ liệu tích hợp bị xoá — một bảng cục bộ sẽ bỏ sót cả hai. Đó cũng là lý do
**bảng `alert_dedup` đã bị loại bỏ** ở migration V2.

---

## 5. T11 — Đối soát, kiểm chứng ba bước

Việc khởi tạo lại dữ liệu **xoá sạch bảng events** ở cả hai hệ thống. Phần lệch không có sự
kiện tương ứng, nên một dịch vụ chỉ nghe webhook hoặc polling sẽ **không bao giờ** chữa
được — dù chạy bao lâu.

### Bước 1 — Dry run

```
runId: r1a0271d6b87   requestsUsed: 6

STUDENT_USER            nguon=100  dich= 80   thieu=20  lech= 0  thua=0
SECTION_COURSE          nguon= 15  dich= 12   thieu= 3  lech=12  thua=0
ENROLLMENT_MEMBERSHIP   nguon=180  dich=180   thieu= 0  lech= 0  thua=0

TONG CHO LECH: 35     da dua vao hang doi: 0   (dung, vi dryRun)
```

Khớp đúng khoảng lệch cố ý của seed. 12 Course lệch tiêu đề là do seed đặt
`"Imported course N"` trong khi UniSIS nói tiêu đề phải là `"INT402 - …"` — đối soát bắt
được cả phần lệch **trường dữ liệu**, không chỉ phần thiếu.

**Toàn bộ phát hiện tốn đúng 6 request** cho cả ba đối tượng. Ca `I58` kiểm chứng từng
endpoint.

### Bước 2 — Chạy thật

```
runId: r1a0271dfe33          <- KHÁC runId lượt dry-run
đã đưa vào hàng đợi: 35
```

```
[ 5s] {"RECEIVED":0,"PROCESSING":1,"DONE":34,"RETRYING":0,"DEAD_LETTER":0}
[10s] {"RECEIVED":0,"PROCESSING":0,"DONE":35,"RETRYING":0,"DEAD_LETTER":0}
```

Xong sau ~10 giây, không một lần thử lại, không một dead-letter. Sự kiện tổng hợp đi qua
**đúng các hàm hội tụ**, không có nhánh xử lý riêng:

```
evt=recon:r1a0271dfe33:student:SV070081 type=student.updated src=RECON
fn=ensureUser action=CREATE target=81 dur=44ms status=DONE
```

### Bước 3 — Dry run lại

```
TONG CHO LECH CON LAI: 0

UniSIS Student: 100  |  UniLearn User  : 100
UniSIS Section:  15  |  UniLearn Course:  15
```

### Ba quyết định thiết kế đáng bảo vệ

**1. Mã sự kiện tổng hợp phải chứa `runId`** — `recon:{runId}:{entityType}:{businessKey}`.
Nếu đặt mã theo thực thể, ràng buộc `UNIQUE(source_system, event_id)` sẽ coi lượt đối soát
thứ hai là trùng và bỏ qua sạch. Hậu quả rơi đúng vào lúc giảng viên bấm đối soát trước mặt:
nhật ký báo *"duplicate, skipped"*, trông y hệt một lỗi. Ca `I57` chạy hai lượt liên tiếp và
khẳng định lượt thứ hai vẫn sinh được việc.

**2. Sửa lệch không đọc lại nguồn.** Sự kiện tổng hợp **mang theo** dữ liệu đã phân giải,
dùng làm `desiredHint`. Ca `I59` khẳng định sau khi đối soát xong, không có một lời gọi
`GET /api/v1/students/{id}` nào. Nếu không mang dữ liệu theo, 20 sửa lỗi sẽ phát sinh thêm
20 lượt đọc — ở quy mô lớn hơn thì đủ chạm trần 100 request/phút.

**3. Không dịch ngược được thì không gỡ.** Membership trỏ tới `courseId`/`userId` không có
trong danh sách đã tải thì **bỏ qua và ghi cảnh báo** chứ không gỡ. Xoá nhầm không khôi phục
được, và dữ liệu đó có thể nằm ngoài phạm vi tích hợp. Ca `I60` kiểm chứng.

---

## 6. Kiểm thử tự động — NFR-10

```
Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Chạy bằng `mvn -B test`. **Không cần mạng và không cần hai hệ thống nguồn** — WireMock giả
lập cả UniSIS lẫn UniLearn, nên giảng viên chạy lại được trên máy bất kỳ.

| Lớp kiểm thử | Số ca | Phạm vi |
|---|---|---|
| `SisStudentMappingTest` | 21 | Quy đổi thang điểm, ánh xạ trạng thái, ghép tên, mốc thời gian, che bí mật |
| `TenantStartupCheckTest` | 3 | NFR-08 — cấu hình tenant sai thì chặn khởi động |
| `EnsureUserIntegrationTest` | 16 | INT-01, INT-05, chống trùng, 503, 429 + `Retry-After`, 409, mất ánh xạ, poller |
| `EnsureCourseIntegrationTest` | 9 | INT-02, INT-07, danh mục lỗi, `CLOSED → ARCHIVED` |
| `EnsureMembershipIntegrationTest` | 11 | INT-03, INT-04, tự khôi phục phụ thuộc, 409/404 idempotent |
| `SyncGradeIntegrationTest` | 8 | INT-06, quy đổi điểm, DRAFT không đồng bộ, 422 dead-letter |
| `RaiseAdvisingAlertIntegrationTest` | 9 | INT-08, chống dội cảnh báo, `NORMAL` không tạo alert |
| `ReconcilerIntegrationTest` | 7 | NFR-06, kịch bản T11, quy tắc `runId`, 6 request |
| **Tổng** | **84** | trong đó **27 ca tình huống lỗi** |

Mười ca đáng chú ý nhất:

| Mã | Nội dung |
|---|---|
| `I03` | Cùng `eventId` giao ba lần chỉ gây một tác động |
| `I10` | Xoá bản ghi ánh xạ mà User còn → tự phục hồi, không tạo trùng |
| `I15` | Bộ che bí mật không để lọt token ra log |
| `I21` | Nguồn không kết nối được là lỗi **tạm thời** → RETRYING, không dead-letter |
| `I22` | Điểm mốc polling tiến, `since` mã hoá đúng một lần |
| `I37` | INT-04 gỡ Membership nhưng **không** xoá User/Course |
| `I44` | INT-06 dùng external reference, không dùng id nội bộ LMS |
| `I45` | 422 `ENROLLMENT_NOT_FOUND` → dead-letter, **không** tự tạo enrollment |
| `I57` | Hai lượt đối soát liên tiếp đều sinh được việc |
| `I63`, `I64` | 429 dùng `Retry-After` và không đốt ngân sách thử lại (T12) |

---

## 7. Trạng thái hàng đợi bền — bằng chứng NFR-04

Trích trực tiếp từ H2 tại thời điểm lập báo cáo. Cách nối vào xem
`HUONG-DAN-KIEM-THU.md` Phần 7b.

| Chỉ số | Giá trị |
|---|---|
| Tổng sự kiện đã nhận | 56 |
| `DONE` | 54 |
| `DEAD_LETTER` | 2 |
| `RETRYING` tồn đọng | **0** |
| Bản ghi ánh xạ ID | 46 — 29 `STUDENT_USER`, 17 `SECTION_COURSE` |
| Dòng nhật ký kiểm toán | 59 |

Phân bố hành động:

| Hành động | Số lần | Ý nghĩa |
|---|---|---|
| `CREATE` | 34 | Tạo mới ở hệ thống đích |
| `UPDATE` | 16 | Hội tụ phần chênh lệch |
| `NOOP` | **4** | **Bằng chứng chống trùng** — trạng thái đã đúng nên không gọi API ghi nào |
| `DELETE` | 2 | Gỡ membership (INT-04) |

`action=NOOP` là bằng chứng trực quan nhất của tính chống trùng: hàm hội tụ đọc trạng thái
thực tế, thấy đã đúng nên **không phát sinh lời gọi ghi**. Không cần logic chống trùng riêng
cho từng luồng — đó là hệ quả tự nhiên của kiến trúc hội tụ trạng thái.

### Hai dòng dead-letter

```
evt_f564607318084ee2  student.created  SIS /api/v1/students/SV07DOCK1 -> HTTP 422
evt_aee6e9675daf4701  student.created  SIS /api/v1/students/SV07DOCK2 -> HTTP 422
```

Hai sinh viên tạo trong lượt kiểm chứng Docker, sau đó bị xoá khỏi UniSIS. Sự kiện còn trong
dòng sự kiện nhưng bản ghi gốc không còn → 422.

**Đây là hành vi đúng, không phải lỗi tồn đọng.** Lỗi dữ liệu vào dead-letter ngay ở lần thử
đầu tiên thay vì đốt hết 6 lượt thử lại — đúng NFR-03. Giữ lại làm bằng chứng.

---

## 8. Đối chiếu 12 yêu cầu phi chức năng

**12/12 đạt.** Chi tiết và bằng chứng từng mục ở `docs/DOI-CHIEU-NFR.md`.

| Mã | Yêu cầu | Bằng chứng |
|---|---|---|
| NFR-01 | Chống trùng theo `eventId` | `I03`, `I24`, `I47`, `I52` + 4 lần `NOOP` chạy thật |
| NFR-02 | Thử lại lỗi tạm thời | `I04`, `I26`, `I46`, `I54`, `I63`, `I64` |
| NFR-03 | Không thử lại vô hạn lỗi dữ liệu | `I09`, `I45`, `I53` + 2 dead-letter thật |
| NFR-04 | Nhật ký kiểm toán | 59 dòng `integration_log`, `/admin/events/{eventId}` |
| NFR-05 | Ánh xạ ID | `I10`, `I25`, `I39` + 46 bản ghi ánh xạ |
| NFR-06 | Đối soát | `I55`–`I61`, T11 chạy thật 35 → 0 |
| NFR-07 | An toàn thông tin | `I15`, `.gitignore`, quét log không lọt token |
| NFR-08 | Cách ly tenant | `U10`–`U12`, chặn khởi động khi cấu hình sai |
| NFR-09 | Health và khả năng quan sát | `/health`, `/health?deep=true` |
| NFR-10 | Kiểm thử tự động | 84 ca, 27 ca tình huống lỗi |
| NFR-11 | Triển khai độc lập | Phòng sạch + 2 ảnh Docker chạy thật INT-01 |
| NFR-12 | Ý thức về giới hạn tần suất | `U06`, `I58`, `I63`, `I64`; token bucket 80/100 req/phút |

Hai lỗ hổng đã tự phát hiện khi rà soát và **đã vá**, ghi lại đầy đủ trong
`docs/DOI-CHIEU-NFR.md`: thiếu ca kiểm thử 429 (NFR-02/NFR-12) và thiếu kiểm tra tenant lúc
khởi động (NFR-08). Bảng rủi ro trong bản thiết kế từng hứa có kiểm tra tenant trong khi mã
nguồn chưa hề cài đặt.

---

## 9. Ba giới hạn của hệ thống đích cần ghi nhận

Không phải thiếu sót thiết kế — là ràng buộc của API đích, cần trả lời được khi bảo vệ.

**Học kỳ chỉ đặt được một lần.** `CoursePatch` bên UniLearn chỉ nhận `title`, `state`,
`teacherExternalRef` — **không có `term`**. Nếu `semesterCode` bên UniSIS đổi về sau,
Integration Service không thể hội tụ trường đó. Đã ghi chú tại `LmsCourse.differsFrom`.

**Mã lỗi khi thêm thành viên bất đối xứng.** Thiếu Course trả **404**, thiếu User trả
**422**, đã là thành viên trả **409**. Theo bảng phân loại lỗi chung thì 404 và 422 đều dẫn
tới dead-letter, nhưng ở đây cả hai thực chất là **thiếu phụ thuộc có thể tự khôi phục** —
nên `ensureMembership` phân loại lại thành `DEPENDENCY_MISSING`, khôi phục rồi thử lại đúng
một lần (ca `I41`).

**Mốc thời gian không có nhãn múi giờ.** Hai hệ thống lưu giờ UTC nhưng SQLite làm rơi
offset, nên API trả `2026-08-23T02:11:28` không kèm `Z`. `Instant.parse()` sẽ ném ngoại lệ
và điểm mốc polling đứng yên — dịch vụ đọc lại toàn bộ bảng sự kiện mỗi 10 giây.
`util/Timestamps.parse()` hiểu giá trị không có múi giờ là UTC; ca `U09` canh chỗ này.

---

## 10. Ranh giới trách nhiệm đã tuân thủ

**Không tự tạo enrollment để "chữa" lỗi 422.** Mục 16.5 nêu tình huống UniSIS từ chối ghi
điểm vì cặp Student/Section chưa có đăng ký học. Đăng ký học thuộc thẩm quyền của UniSIS và
Phòng Đào tạo — đây là ranh giới trách nhiệm, không phải giới hạn kỹ thuật. Thử lại cũng vô
ích vì dữ liệu sẽ không tự xuất hiện. Hành động đúng: chuyển thẳng dead-letter kèm log nêu
rõ nguyên nhân, để con người quyết định. Ca `I45` kiểm chứng cả hai vế.

**Không tạo dữ liệu tay ở hệ thống đích.** Theo mục 9.7, với INT-01, INT-02, INT-05, INT-07
thì thao tác phải phát sinh ở UniSIS và dữ liệu bên UniLearn phải do Integration Service
tạo. Mọi bản ghi UniLearn trong báo cáo này đều do dịch vụ sinh ra.

**Không sửa mã nguồn của giảng viên.** Thư mục `Integration_workshop` giữ nguyên hiện trạng
ban đầu, đã đối chiếu bằng SHA-256 với bản trong file `.rar`.

---

## 11. Còn thiếu

Báo cáo này là bằng chứng ở **tầng API và tầng cơ sở dữ liệu**. Mục 9.8 còn yêu cầu ảnh chụp
giao diện. Danh sách ảnh cần chụp cho từng INT nằm ở cuối mỗi file trong `docs/evidence/`.

| Việc | Trạng thái |
|---|---|
| Ảnh chụp giao diện UniSIS/UniLearn — trước và sau, cho từng INT | ⬜ chưa có |
| Ba sơ đồ tuần tự | ⬜ mã PlantUML đã có ở Phụ lục B bản thiết kế v2.0, chưa dựng thành ảnh |
| T10 chạy thật với chế độ lỗi mô phỏng | ⬜ hiện chỉ có ca tự động |

**Che toàn bộ access token và client secret trong mọi ảnh chụp và đoạn log** — kể cả ảnh
chụp cơ sở dữ liệu, vì cột `payload` chứa nguyên văn JSON của nguồn.

---

## 12. Cách chạy lại toàn bộ

```bash
mvn -B test
```

84 ca, dưới 30 giây, không cần mạng và không cần hai hệ thống nguồn.

Muốn chạy thử một mạch trên hệ thống thật — 1 sinh viên, 1 lớp, đi hết 8 INT trong khoảng
25 phút — xem `KICH-BAN-TEST.md`. Tra cứu kiểm thử từng INT riêng lẻ xem
`HUONG-DAN-KIEM-THU.md`.
