# Bằng chứng INT-02 và INT-07

> Hai yêu cầu dùng chung một hàm hội tụ `ensureCourse`. INT-07 không cần một dòng mã
> riêng nào — đổi `lecturerId` chỉ là một trường hợp chênh lệch mà hàm vốn đã xử lý.

| Hạng mục | Giá trị |
|---|---|
| Tenant | `TEAM07` (đã seed bằng endpoint admin của giảng viên) |
| Ngày thực hiện | 15/08/2026 |
| Mã lớp dùng thử | `SEC-TEAM07-LAB02` |
| Học phần | `INT402` — Kien truc va tich hop he thong |
| Giảng viên | `GV0001` → `GV0002` |
| Chế độ | polling 10 giây (webhook tắt) |

Dữ liệu nền sau seed, đúng như mục 6.5 và 7.5 của đề mô tả:

```
UniSIS   : 100 sinh vien, 10 giang vien, 20 hoc phan, 15 lop, 180 enrollment
UniLearn :  80 user, 12 course, 180 membership
```

Khoảng lệch 20 User và 3 Course là **cố ý** — dành cho bài toán đối soát sau này.

---

## INT-02 — Đồng bộ Section thành LMS Course

### Tầng 1 — Trạng thái trước

```
UniLearn GET /api/v1/courses?externalCode=SEC-TEAM07-LAB02  ->  []
```

### Tầng 2 — Thao tác nghiệp vụ tại UniSIS

```json
POST /api/v1/sections
{ "sectionId": "SEC-TEAM07-LAB02", "courseCode": "INT402",
  "semesterCode": "2026-1", "lecturerId": "GV0001",
  "capacity": 60, "status": "OPEN" }
```

Sự kiện phát ra: `evt_a556d20383f04d11` / `section.created`

### Tầng 3 — Xử lý tại Integration Service

```
evt=evt_a556d20383f04d11 type=section.created src=SIS fn=ensureCourse
key=SEC-TEAM07-LAB02 action=CREATE target=13 attempt=1 dur=102ms status=DONE
msg=tao LMS Course moi cho SEC-TEAM07-LAB02 (title=INT402 - Kien truc va tich hop he thong)
```

### Tầng 4 — Trạng thái sau tại UniLearn

```json
{ "id": 13,
  "externalCode": "SEC-TEAM07-LAB02",
  "title": "INT402 - Kien truc va tich hop he thong",
  "term": "2026-1",
  "state": "PUBLISHED",
  "teacherExternalRef": "GV0001" }
```

Đối chiếu bảng ánh xạ mục 12.2:

| Nguồn | Đích | Quy tắc | Kết quả |
|---|---|---|---|
| `sectionId` | `externalCode` | khoá nghiệp vụ | `SEC-TEAM07-LAB02` ✅ |
| `courseCode` + `courseName` | `title` | phải chứa `courseCode` | `INT402 - Kien truc va tich hop he thong` ✅ |
| `semesterCode` | `term` | giữ nguyên | `2026-1` ✅ |
| `lecturerId` | `teacherExternalRef` | tham chiếu giảng viên | `GV0001` ✅ |
| `OPEN` | `state` | khuyến nghị `PUBLISHED` | `PUBLISHED` ✅ |

### Tiêu chí nghiệm thu mục 12.6

| # | Tiêu chí | Kết quả |
|---|---|---|
| 1 | Tạo Section → đúng 01 LMS Course có `externalCode` = `sectionId` | ✅ `id=13` |
| 2 | Không tạo course trùng khi event bị lặp | ✅ ca kiểm thử I24 |
| 3 | Đối soát bổ sung Section đã tồn tại trước | ⬜ chờ Reconciler |

---

## INT-07 — Đồng bộ thay đổi giảng viên phụ trách

### Thao tác tại UniSIS

```
PATCH /api/v1/sections/SEC-TEAM07-LAB02   { "lecturerId": "GV0002" }
```

### Xử lý

```
evt=evt_4a29f2c9ca474aff type=section.updated src=SIS fn=ensureCourse
key=SEC-TEAM07-LAB02 action=UPDATE target=13 attempt=1 dur=45ms status=DONE
msg=doi giang vien phu trach GV0001 -> GV0002 (INT-07, Course id giu nguyen 13)
```

### Trạng thái sau tại UniLearn

```
id                 = 13          <- KHONG doi
teacherExternalRef = GV0002      <- da cap nhat
title              = INT402 - Kien truc va tich hop he thong
```

Tổng số Course của TEAM07: **13** = 12 seed + 1 mới. Không có Course trùng.

### Tiêu chí nghiệm thu mục 17.6

| # | Tiêu chí | Kết quả |
|---|---|---|
| 1 | Đổi `GV0001` → `GV0002` thì `teacherExternalRef` thành `GV0002` | ✅ |
| 2 | ID LMS Course giữ nguyên | ✅ vẫn là `13` |
| 3 | Không xuất hiện Course trùng | ✅ tổng 13, không tăng thêm |

---

## Ràng buộc của hệ thống đích cần ghi nhận

`CoursePatch` bên UniLearn chỉ nhận `title`, `state`, `teacherExternalRef` —
**không có `term`**. Nghĩa là học kỳ chỉ đặt được một lần lúc tạo Course. Nếu
`semesterCode` bên UniSIS đổi về sau, Integration Service **không thể** hội tụ trường đó.

Đây là giới hạn của API đích, không phải thiếu sót thiết kế. Đã ghi trong mã nguồn
tại `LmsCourse.differsFrom` để trả lời được khi bảo vệ.

---

## Ca kiểm thử tự động

| Mã | Nội dung |
|---|---|
| `I23` | `section.created` tạo đúng 1 Course, đủ 5 trường ánh xạ |
| `I24` | Sự kiện lặp không tạo Course thứ hai |
| `I25` | Course đã tồn tại nhưng ánh xạ cục bộ chưa có → không tạo trùng, tự khôi phục ánh xạ |
| `I26` | LMS trả 503 → RETRYING rồi DONE |
| `I27` | **INT-07** đổi giảng viên → PATCH, `id` giữ nguyên, không POST |
| `I28` | Gửi lặp `section.updated` → NOOP |
| `I29` | Không tra được tên học phần → title rút gọn còn `courseCode` (mục 12.5) |
| `I30` | Section `CLOSED` → Course `state=ARCHIVED` |
| `I31` | Danh mục học phần tạm lỗi → dùng dữ liệu đệm, sự kiện vẫn xong |

Đối chiếu test tối thiểu mục 12.7 và 17.7: **đủ cả 7 mục**.

Tổng bộ kiểm thử: **36/36 xanh**.

---

## Còn thiếu

- [ ] Ảnh UniSIS → Lớp học phần — lớp vừa mở
- [ ] Ảnh UniSIS → Dòng sự kiện — `section.created` kèm `eventId`
- [ ] Ảnh UniLearn → Courses — Course đã xuất hiện, `External code` đúng
- [ ] Ảnh UniLearn → Courses — sau khi đổi giảng viên, `teacherExternalRef` là `GV0002`
- [ ] Response `GET /api/v1/courses/13` từ Swagger để chứng minh `id` không đổi

Che token và client secret trong mọi ảnh chụp.
