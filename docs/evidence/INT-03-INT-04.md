# Bằng chứng INT-03 và INT-04

> Hai yêu cầu dùng chung hàm hội tụ `ensureMembership`. Khác với INT-01/02, nhóm này
> **có phụ thuộc**: membership chỉ tồn tại được khi đã có cả LMS User lẫn LMS Course.

| Hạng mục | Giá trị |
|---|---|
| Tenant | `TEAM07` (đã seed) |
| Ngày thực hiện | 15/08/2026 |
| Sinh viên | `SV070081` — **cố ý chọn người chưa có LMS User** |
| Lớp | `SEC-TEAM07-LAB02` → LMS Course `id=13` |
| enrollmentId | `ENR-A3CBDA1221` |

Việc chọn `SV070081` là có chủ đích: seed tạo 100 Student bên SIS nhưng chỉ 80 User bên
LMS, nên `SV070081` → `SV070100` là phần lệch cố ý. Dùng một trong số đó để kiểm chứng
**tình huống khó nhất của mục 13.5**: sự kiện đăng ký đến khi phụ thuộc chưa tồn tại.

---

## INT-03 — Enrollment → Membership

### Tầng 1 — Trạng thái trước

```
UniLearn GET /users?externalRef=SV070081   ->  []      (CHUA CO User)
UniLearn GET /courses/13/members           ->  []      (chua co thanh vien)
```

### Tầng 2 — Thao tác tại UniSIS

```json
POST /api/v1/enrollments
{ "studentId": "SV070081", "sectionId": "SEC-TEAM07-LAB02" }

-> { "enrollmentId": "ENR-A3CBDA1221", "status": "ENROLLED" }
```

Sự kiện phát ra: `evt_4283eb14af7f40de` / `enrollment.created`

### Tầng 3 — Xử lý, gồm bước tự khôi phục phụ thuộc

```
key=SV070081 chua co LMS User, goi ensureUser truoc khi them thanh vien
```

```
evt=evt_4283eb14af7f40de type=enrollment.created src=SIS fn=ensureMembership
key=SV070081|SEC-TEAM07-LAB02 action=CREATE target=13/81 attempt=1 dur=204ms status=DONE
msg=them SV070081 vao SEC-TEAM07-LAB02 (courseId=13, userId=81)
```

Đây là bằng chứng cho tiêu chí nghiệm thu số 2 của mục 13.6: *"Event đến trước User/Course
vẫn được xử lý cuối cùng khi dependency sẵn sàng"*. Trong thiết kế này, phụ thuộc được
khôi phục **ngay trong lượt xử lý** — không cần hàng chờ phụ thuộc riêng.

### Tầng 4 — Trạng thái sau tại UniLearn

```
LMS User cho SV070081 : id=81, displayName="Nguyen Van An81"   <- TU DUOC TAO
Thanh vien Course 13  : userId=81, role=STUDENT, status=ACTIVE
```

Đối chiếu bảng ánh xạ mục 13.2:

| Nguồn | Đích | Quy tắc | Kết quả |
|---|---|---|---|
| `studentId` | LMS `userId` | tra qua mapping hoặc `externalRef` | `SV070081` → `81` ✅ |
| `sectionId` | LMS `courseId` | tra qua mapping hoặc `externalCode` | `SEC-TEAM07-LAB02` → `13` ✅ |
| `ENROLLED` | Membership `ACTIVE` | không tạo lại User/Course tuỳ tiện | ✅ |
| hằng | `role=STUDENT` | vai trò duy nhất trong bài | ✅ |

### Tiêu chí nghiệm thu mục 13.6

| # | Tiêu chí | Kết quả |
|---|---|---|
| 1 | Enrollment mới → đúng User là member của đúng Course | ✅ `userId=81` trong `courseId=13` |
| 2 | Event đến trước User/Course vẫn xử lý được | ✅ User `id=81` do chính lượt này tạo |
| 3 | Không có Membership trùng do duplicate event | ✅ ca kiểm thử I36 |

---

## INT-04 — Huỷ học và gỡ Membership

### Thao tác tại UniSIS

```
DELETE /api/v1/enrollments/ENR-A3CBDA1221   ->   status = DROPPED
```

### Xử lý

```
evt=evt_0024acb3ac9c45d5 type=enrollment.dropped src=SIS fn=ensureMembership
key=SV070081|SEC-TEAM07-LAB02 action=DELETE target=13/81 attempt=1 dur=41ms status=DONE
msg=go SV070081 khoi SEC-TEAM07-LAB02 — User va Course GIU NGUYEN
```

### Tiêu chí nghiệm thu mục 14.6 — kiểm chứng từng dòng

| # | Tiêu chí | Kết quả |
|---|---|---|
| 1 | Sau drop, GET membership không còn bản ghi ACTIVE | ✅ `GET /courses/13/members` → `[]` |
| 2 | **User vẫn tồn tại** | ✅ `id=81` còn nguyên |
| 3 | **Course vẫn tồn tại** | ✅ `id=13` còn nguyên |
| 4 | Gửi lặp cùng drop event không làm hỏng dữ liệu | ✅ ca kiểm thử I38, I40 |

Đây là điểm dễ sai nhất của INT-04: gỡ quan hệ thành viên **chứ không xoá** User hay Course.
Mã nguồn chỉ gọi `DELETE /courses/{id}/members/{userId}`, không có bất kỳ đường nào gọi
`DELETE /users/{id}` hay `DELETE /courses/{id}` — ca kiểm thử I37 kiểm tra đúng điều đó
bằng cách khẳng định hai lời gọi kia **không hề xảy ra**.

---

## Ràng buộc của hệ thống đích cần ghi nhận

Mã lỗi khi thêm thành viên **bất đối xứng**:

| Tình huống | Mã trả về |
|---|---|
| Thiếu Course | **404** `COURSE_NOT_FOUND` |
| Thiếu User | **422** `USER_NOT_FOUND` |
| Đã là thành viên ACTIVE | **409** `MEMBERSHIP_EXISTS` |

Theo bảng phân loại lỗi chung (mục 4.4), 404 và 422 đều dẫn tới dead-letter. Nhưng ở đây
cả hai thực chất là **thiếu phụ thuộc có thể tự khôi phục**. Vì vậy `ensureMembership`
phân loại lại chúng thành `DEPENDENCY_MISSING`, khôi phục rồi thử lại đúng một lần —
ca kiểm thử I41.

Tương tự, `DELETE` trả **404** `MEMBERSHIP_NOT_FOUND` không phải lỗi: mục 14.5 ghi rõ đó
có thể là trạng thái cuối đã đúng. Xử lý như idempotent success — ca kiểm thử I40.

---

## Ca kiểm thử tự động

| Mã | Nội dung |
|---|---|
| `I32` | `enrollment.created` thêm đúng 1 Membership STUDENT vào đúng Course |
| `I33` | **Chưa có LMS User** → tự gọi INT-01 rồi thêm thành viên |
| `I34` | **Chưa có LMS Course** → tự gọi INT-02 rồi thêm thành viên |
| `I35` | 409 `MEMBERSHIP_EXISTS` → idempotent success, không retry vô hạn |
| `I36` | Đã là thành viên → NOOP, không gọi POST |
| `I37` | **INT-04** gỡ Membership, khẳng định KHÔNG xoá User/Course |
| `I38` | Gửi lặp drop → NOOP, an toàn |
| `I39` | Mất ánh xạ cục bộ nhưng User/Course còn → tra lại được, vẫn gỡ đúng |
| `I40` | DELETE trả 404 → trạng thái cuối đã đúng |
| `I41` | 422 khi thêm → khôi phục phụ thuộc rồi thử lại |
| — | Sự kiện cũ → đọc lại trạng thái đăng ký hiện hành ở UniSIS (mục 3.3) |

Đối chiếu test tối thiểu mục 13.7 và 14.7: **đủ cả 7 mục**.

Tổng bộ kiểm thử: **47/47 xanh**.

---

## Còn thiếu

- [ ] Ảnh UniSIS → Đăng ký học — dòng đăng ký vừa tạo
- [ ] Ảnh UniSIS → Dòng sự kiện — `enrollment.created` kèm `eventId`
- [ ] Ảnh UniLearn → Thành viên — hàng role STUDENT, status ACTIVE
- [ ] Ảnh UniSIS → Đăng ký học — trạng thái `DROPPED`
- [ ] Ảnh UniLearn → Thành viên — không còn hàng ACTIVE
- [ ] Ảnh UniLearn → Người dùng và Khóa học — **cả hai vẫn còn**

Che token và client secret trong mọi ảnh chụp.
