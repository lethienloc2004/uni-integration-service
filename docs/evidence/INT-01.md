# Bằng chứng INT-01 — Đồng bộ Student thành LMS User

> Kèm INT-05 (đồng bộ trạng thái) và kịch bản T09 (chống trùng), vì cả ba dùng chung
> một hàm hội tụ `ensureUser`.

| Hạng mục | Giá trị |
|---|---|
| Tenant | `TEAM07` |
| Ngày thực hiện | 15/08/2026 |
| Mã sinh viên dùng thử | `SV07LAB02` |
| UniSIS | `http://127.0.0.1:8001` |
| UniLearn | `http://127.0.0.1:8002` |
| Integration Service | `http://localhost:8080` |
| Chế độ | polling 10 giây (webhook tắt) |

Quy ước đặt mã theo mục 9.6 của đề: mã mới, dễ nhận diện theo tenant, không trùng
mã ví dụ có sẵn.

---

## Tầng 1 — Trạng thái trước khi tích hợp

```
UniSIS   GET /api/v1/students/SV07LAB02          -> HTTP 404   (chưa tồn tại)
UniLearn GET /api/v1/users?externalRef=SV07LAB02 -> []         (chưa có User)
```

Hai hệ thống đều chưa có dữ liệu của mã này.

---

## Tầng 2 — Thao tác nghiệp vụ và sự kiện nguồn

Thao tác phát sinh **tại UniSIS** — hệ thống đích không bị tác động thủ công,
đúng quy tắc mục 9.7 của đề.

```json
POST /api/v1/students
{
  "studentId": "SV07LAB02", "firstName": "Binh", "lastName": "Tran Thi",
  "email": "binh@sv.edu.vn", "programCode": "CNTT", "cohort": 2026, "status": "ACTIVE"
}
```

Sự kiện UniSIS phát ra:

```
eventId    = evt_0d017f8fa6da41cd
eventType  = student.created
occurredAt = 2026-08-15T02:52:22.185493
```

---

## Tầng 3 — Xử lý tại Integration Service

`GET /admin/inbox`

```
eventId=evt_0d017f8fa6da41cd  type=student.created  key=SV07LAB02
status=DONE  action=CREATE  attempt=1  retryCount=0
```

Dòng nhật ký tương ứng, mã sự kiện đóng vai trò mã tương quan:

```
evt=evt_0d017f8fa6da41cd type=student.created src=SIS fn=ensureUser key=SV07LAB02
action=CREATE target=1 attempt=1 dur=76ms status=DONE
msg=tao LMS User moi cho SV07LAB02
```

---

## Tầng 4 — Trạng thái sau tại hệ thống đích

```json
GET /api/v1/users?externalRef=SV07LAB02
[{
  "id": 1,
  "username": "SV07LAB02",
  "displayName": "Tran Thi Binh",
  "emailAddress": "binh@sv.edu.vn",
  "userType": "LEARNER",
  "enabled": true,
  "externalRef": "SV07LAB02"
}]
```

Đối chiếu với bảng ánh xạ mục 11.2 của đề:

| Nguồn | Đích | Quy tắc | Kết quả |
|---|---|---|---|
| `studentId` | `username` | giữ nguyên | `SV07LAB02` ✅ |
| `studentId` | `externalRef` | khoá nghiệp vụ | `SV07LAB02` ✅ |
| `lastName + " " + firstName` | `displayName` | ghép, trim | `Tran Thi Binh` ✅ |
| `email` | `emailAddress` | SIS ghi đè | `binh@sv.edu.vn` ✅ |
| hằng | `userType` | `LEARNER` | `LEARNER` ✅ |
| `status == ACTIVE` | `enabled` | ACTIVE → true | `true` ✅ |

`id = 1` là ID nội bộ do UniLearn tự sinh, **khác** `studentId` — đúng bất biến BV-4:
ID nội bộ của LMS không bao giờ được dùng làm mã nghiệp vụ.

---

## INT-05 — Đồng bộ trạng thái

```
UniSIS  PATCH /api/v1/students/SV07LAB02  {"status": "SUSPENDED"}
```

Kết quả tại UniLearn sau đồng bộ:

```
id      = 1        <- KHÔNG đổi, không sinh User thứ hai (bất biến BV-1)
enabled = false    <- trạng thái khác ACTIVE nên tài khoản bị vô hiệu hoá
```

```
evt=evt_c815b884d41e4204 type=student.updated fn=ensureUser key=SV07LAB02
action=UPDATE target=1 attempt=1 dur=46ms status=DONE
msg=cap nhat displayName/email/enabled cho SV07LAB02 (enabled=false)
```

---

## T09 — Chống trùng

Xử lý lại đúng sự kiện `evt_c815b884d41e4204` một lần nữa:

```
evt=evt_c815b884d41e4204 type=student.updated fn=ensureUser key=SV07LAB02
action=NOOP target=1 attempt=1 dur=17ms status=DONE
msg=LMS User da dung trang thai, khong lam gi
```

`action=NOOP` là bằng chứng trực quan của tính chống trùng: hàm hội tụ đọc trạng thái
thực tế, thấy đã đúng nên **không gọi API ghi nào**. Không cần logic chống trùng riêng
cho từng luồng.

---

## Đối chiếu tiêu chí nghiệm thu — mục 11.6

| # | Tiêu chí | Kết quả |
|---|---|---|
| 1 | Tạo Student mới → xuất hiện đúng 01 LMS User | ✅ đúng 1 User, `id=1` |
| 2 | Sửa email/họ tên SIS → LMS User được cập nhật | ✅ ca kiểm thử I02 + `action=UPDATE` |
| 3 | Đổi ACTIVE → SUSPENDED → `enabled=false` | ✅ đã kiểm chứng thật |
| 4 | Giao cùng eventId nhiều lần → không tạo User trùng | ✅ `action=NOOP`, vẫn `id=1` |

## Đối chiếu test tối thiểu — mục 11.7

| Test | Ca kiểm thử tự động |
|---|---|
| Happy path tạo Student mới | `I01` |
| Update email sau khi User đã có | `I02` |
| Duplicate event | `I03` |
| LMS trả 503 lần đầu rồi hoạt động lại | `I04` |

Tổng: **27/27 ca kiểm thử xanh** (`mvn test`).

---

## NFR-07 — Không lộ bí mật

Quét toàn bộ nhật ký sinh ra trong phiên: không có chuỗi nào khớp `student-secret`
hay tiền tố JWT `eyJhbGciOi`. Bộ lọc che bí mật hoạt động đúng.

---

## Còn thiếu

Hồ sơ này là bằng chứng ở **tầng API**. Mục 9.8 của đề còn yêu cầu **ảnh chụp giao diện**
trạng thái trước và sau ở UniSIS/UniLearn. Cần bổ sung:

- [ ] Ảnh UniLearn → People, tìm `SV07LAB02` — trước khi tích hợp (không có kết quả)
- [ ] Ảnh UniSIS → Sinh viên — hồ sơ vừa tạo
- [ ] Ảnh UniSIS → Dòng sự kiện — dòng `student.created` kèm `eventId`
- [ ] Ảnh UniLearn → People — User đã xuất hiện, Access `ENABLED`
- [ ] Ảnh UniLearn → People — sau khi đổi sang SUSPENDED, Access `DISABLED`

Nhớ che token và client secret trong mọi ảnh chụp.
