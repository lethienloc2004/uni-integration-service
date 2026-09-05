# Bằng chứng INT-08 và Đối soát (NFR-06 / kịch bản T11)

---

# Phần A — INT-08: Learning Risk → Advising Alert

| Hạng mục | Giá trị |
|---|---|
| Hướng | UniLearn → Integration Service → UniSIS |
| Điều kiện phát sự kiện | `completionPercent < 30` **và** `inactiveDays >= 14` |

## Bảng ánh xạ mục 18.2

| Nguồn (UniLearn) | Đích (UniSIS) | Quy tắc |
|---|---|---|
| `userExternalRef` | `studentId` | mã nghiệp vụ của SIS |
| `courseExternalCode` | `sectionId` | mã nghiệp vụ của SIS |
| `riskType` | `riskType` | thường là `AT_RISK` |
| `completionPercent` + `inactiveDays` | `details` | đủ để cố vấn hiểu nguyên nhân |

`details` sinh ra có dạng:

```
Tien do hoc tap 20%, khong hoat dong 16 ngay (nguon: UniLearn, riskId=RSK-XXXX)
```

## Bài toán riêng: chống trùng cảnh báo

Mục 18.4 nêu rõ: *"endpoint alert không tự dedup theo riskId, Integration Service phải chống
tạo alert trùng"*.

Ràng buộc `UNIQUE` trên inbox đã chặn được **cùng một `eventId` giao nhiều lần**. Nhưng nó
không chặn được tình huống thực tế hơn: một sinh viên bị **đánh giá lại nhiều lần**, mỗi lần
sinh một `eventId` khác nhau, và cố vấn nhận về mười cảnh báo giống hệt.

Cách xử lý: trước khi ghi, hỏi **chính UniSIS** xem đã có cảnh báo nào đang mở cho cùng
`(studentId, sectionId, riskType)` chưa.

```
GET /api/v1/advising/alerts?studentId=..  ->  loc theo sectionId + riskType + status=OPEN
```

**Nguồn sự thật là hệ thống đích, không phải một bảng cục bộ.** Cách này tính được cả cảnh
báo do người khác tạo, và cả cảnh báo còn sót lại từ lần chạy trước khi cơ sở dữ liệu tích
hợp bị xoá — một bảng cục bộ sẽ bỏ sót cả hai.

> **Hệ quả: đã bỏ bảng `alert_dedup`.** Migration `V2__drop_alert_dedup.sql` xoá nó, cùng lý do
> đã bỏ `sync_state` từ bản thiết kế v2.0: giữ một bảng không còn tác dụng sẽ làm yếu chính
> lập luận kiến trúc.

## Tiêu chí nghiệm thu mục 18.6

| # | Tiêu chí | Ca kiểm thử |
|---|---|---|
| 1 | Risk 20% / 16 ngày → đúng 01 alert AT_RISK cho đúng student/section | `I49` |
| 2 | Risk `NORMAL` **không** tạo alert | ca riêng, khẳng định 0 lời gọi POST |
| 3 | Duplicate `learner.at_risk` không tạo alert trùng | `I50`, `I52` |

Thêm các ca ngoài yêu cầu tối thiểu: `I50b` (cảnh báo cũ đã đóng thì vẫn tạo mới),
`I51` (cảnh báo của lớp khác không chặn), `I53` (422 `STUDENT_NOT_FOUND` → dead-letter ngay),
`I54` (503 → RETRYING rồi DONE).

---

# Phần B — Đối soát: kịch bản chấm T11

> *"Reset dữ liệu khi integration không có event lịch sử → Service phát hiện và chữa dữ liệu
> Student/Section thiếu."*

## Vì sao bắt buộc phải có

Việc khởi tạo lại dữ liệu **xoá sạch bảng events** ở cả hai hệ thống. Sau reset:

```
UniSIS   : 100 Student, 15 Section, 180 Enrollment
UniLearn :  80 User,    12 Course,  180 Membership
```

Phần lệch **không có sự kiện tương ứng**. Một dịch vụ chỉ nghe webhook hoặc polling sẽ
**không bao giờ** chữa được — dù chạy bao lâu.

## Kiểm chứng thật, ba bước

### Bước 1 — Dry run: phạm vi lệch là bao nhiêu?

```
POST /admin/reconcile?dryRun=true
```

```
runId: r1a0271d6b87   requestsUsed: 6

STUDENT_USER            nguon=100  dich= 80   thieu=20  lech= 0  thua=0
SECTION_COURSE          nguon= 15  dich= 12   thieu= 3  lech=12  thua=0
ENROLLMENT_MEMBERSHIP   nguon=180  dich=180   thieu= 0  lech= 0  thua=0

TONG CHO LECH: 35        da dua vao hang doi: 0   (dung, vi dryRun)
```

Đúng khoảng lệch cố ý của seed: **20 User thiếu, 3 Course thiếu**. Cộng thêm 12 Course lệch
tiêu đề — seed đặt `"Imported course N"` trong khi UniSIS nói tiêu đề phải là
`"INT402 - Kien truc va tich hop he thong"`. Đối soát bắt được cả phần lệch trường này.

**Toàn bộ phát hiện tốn đúng 6 request** cho cả ba đối tượng — ca kiểm thử `I58` kiểm chứng
từng endpoint.

### Bước 2 — Chạy thật

```
POST /admin/reconcile
```

```
runId: r1a0271dfe33     <- KHAC runId cua luot dry-run

STUDENT_USER            da dua vao hang doi: 20
SECTION_COURSE          da dua vao hang doi: 15
TONG: 35
```

Xử lý xong sau **~10 giây**, không một lần thử lại, không một dead-letter:

```
[ 5s] {"RECEIVED":0,"PROCESSING":1,"DONE":34,"RETRYING":0,"DEAD_LETTER":0}
[10s] {"RECEIVED":0,"PROCESSING":0,"DONE":35,"RETRYING":0,"DEAD_LETTER":0}
```

Nhật ký cho thấy sự kiện tổng hợp đi qua **đúng các hàm hội tụ**, không có nhánh riêng:

```
evt=recon:r1a0271dfe33:student:SV070081 type=student.updated src=RECON
fn=ensureUser key=SV070081 action=CREATE target=81 attempt=1 dur=44ms status=DONE
msg=tao LMS User moi cho SV070081
```

### Bước 3 — Dry run lại: đã sạch chưa?

```
STUDENT_USER            nguon=100  dich=100   thieu=0  lech=0  thua=0
SECTION_COURSE          nguon= 15  dich= 15   thieu=0  lech=0  thua=0
ENROLLMENT_MEMBERSHIP   nguon=180  dich=180   thieu=0  lech=0  thua=0

TONG CHO LECH CON LAI: 0
```

Đối chiếu trực tiếp hai hệ thống:

```
UniSIS Student: 100  |  UniLearn User  : 100
UniSIS Section:  15  |  UniLearn Course:  15
```

---

## Ba quyết định thiết kế đáng bảo vệ

### 1. Mã sự kiện tổng hợp phải chứa `runId`

```
recon:{runId}:{entityType}:{businessKey}
```

`runId` sinh **mới mỗi lượt chạy**. Nếu đặt mã theo thực thể — chẳng hạn
`recon:student:SV070081` — thì ràng buộc `UNIQUE(source_system, event_id)` sẽ coi lượt đối
soát thứ hai là trùng và **bỏ qua sạch**. Hậu quả rơi đúng vào lúc giảng viên bấm đối soát
trước mặt: nhật ký báo *"duplicate, skipped"*, trông y hệt một lỗi.

Ca kiểm thử `I57` chạy hai lượt liên tiếp và khẳng định lượt thứ hai **vẫn sinh được việc**.

### 2. Sửa lệch không đọc lại nguồn

Bộ đối soát vừa tải toàn bộ dữ liệu nên đã có sẵn mọi thứ trong bộ nhớ. Sự kiện tổng hợp
**mang theo** phần dữ liệu đã phân giải, và bộ xử lý dùng nó làm `desiredHint` (mục 3.3).

Ca kiểm thử `I59` khẳng định điều này chặt chẽ: sau khi đối soát và xử lý xong, **không có
một lời gọi `GET /api/v1/students/{id}` nào**. Nếu không mang dữ liệu theo, 20 sửa lỗi sẽ
phát sinh thêm 20 lượt đọc — và ở quy mô lớn hơn thì đủ để chạm trần 100 request/phút.

Bằng chứng gián tiếp trên hệ thống thật: 35 việc xong trong ~10 giây, hoàn toàn không chạm
giới hạn tần suất.

### 3. Không dịch ngược được thì không gỡ

Khi một membership trỏ tới `courseId`/`userId` không có trong danh sách đã tải, bộ đối soát
**bỏ qua và ghi cảnh báo** chứ không gỡ. Xoá nhầm thì không khôi phục được, và dữ liệu đó có
thể nằm ngoài phạm vi tích hợp. Ca kiểm thử `I60` kiểm chứng.

---

## Ba nguồn kích hoạt đối soát

| Khi nào | Mặc định | Vai trò |
|---|---|---|
| Lúc khởi động | **bật** | Bắt buộc — lần duy nhất bù được dữ liệu có sẵn không có sự kiện |
| Theo lệnh thủ công | luôn có | Bấm ngay tại buổi bảo vệ sau khi giảng viên reset |
| Định kỳ 10 phút | bật | Lớp phòng thủ dự phòng — được phép tắt đầu tiên khi cần nhường băng thông |

Tắt bằng `ENABLE_RECONCILE_ON_STARTUP=false` và `ENABLE_RECONCILE_SCHEDULE=false`.

---

## Ca kiểm thử tự động

| Mã | Nội dung |
|---|---|
| `I55` | Phát hiện Student thiếu, sinh sự kiện tổng hợp vào cùng bảng inbox |
| `I56` | `dryRun` chỉ báo cáo, tuyệt đối không ghi gì |
| `I57` | **Hai lượt liên tiếp đều sinh được việc** — kiểm chứng quy tắc `runId` |
| `I58` | Phát hiện tốn đúng 6 request cho cả ba đối tượng |
| `I59` | Sự kiện tổng hợp mang dữ liệu → không đọc lại nguồn |
| `I60` | Membership không dịch ngược được thì **không** gỡ |
| `I61` | Đăng ký đã huỷ ở SIS nhưng membership còn ACTIVE → sinh việc gỡ |

Tổng bộ kiểm thử: **79/79 xanh**.

---

## Còn thiếu

- [ ] Ảnh UniLearn → Rủi ro — nhập Completion 20%, Inactive 16, tín hiệu `AT_RISK`
- [ ] Ảnh UniLearn → Tín hiệu — `learner.at_risk` kèm `eventId`
- [ ] Ảnh UniSIS → Cảnh báo cố vấn — alert `AT_RISK` với `details` có số liệu
- [ ] Ảnh chứng minh Completion 50% / Inactive 16 → `NORMAL`, **không** tạo alert
- [ ] Ảnh ba bước đối soát: dry-run báo 35 → chạy thật → dry-run báo 0
