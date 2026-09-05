# Bằng chứng INT-06 — Đồng bộ điểm đã Publish từ LMS về UniSIS

> **Luồng ngược đầu tiên: LMS → SIS.** Khác ba hàm hội tụ trước, đây không có trạng thái để
> hội tụ mà là ghi nhận một sự kiện đã xảy ra — việc công bố điểm.

| Hạng mục | Giá trị |
|---|---|
| Tenant | `TEAM07` |
| Ngày thực hiện | 15/08/2026 |
| Sinh viên | `SV070001` (LMS User `id=1`) |
| Lớp | `SEC-TEAM07-01` (LMS Course `id=1`) |
| Điểm nhập ở LMS | `87.5` / 100 |
| Điểm ghi vào SIS | `8.75` / 10 |

---

## Tầng 1 — Trạng thái trước

```
UniSIS  GET /api/v1/grades  -> khong co diem cho SV070001 / SEC-TEAM07-01
UniSIS  GET /api/v1/enrollments?studentId=SV070001&sectionId=SEC-TEAM07-01
        -> status = ENROLLED     (dieu kien bat buoc de UniSIS chap nhan ghi diem)
```

---

## Tầng 2a — Ghi điểm nháp và **chứng minh DRAFT không đồng bộ**

Đây là bước dễ bị bỏ qua nhưng chính là **tiêu chí nghiệm thu số 2 của mục 16.6**:
*"Điểm chỉ đồng bộ sau thao tác publish"*.

```json
PUT /api/v1/courses/1/grades   { "userId": 1, "finalGrade": 87.5 }

-> { "gradeId": 1, "finalGrade": 87.5, "published": false }
```

Chờ **12 giây** — dài hơn một chu kỳ polling:

```
UniSIS GET /api/v1/grades  ->  VAN CHUA CO
```

Đúng như thiết kế. UniLearn chỉ phát sự kiện khi bấm Publish, và `SyncGradeHandler` chỉ
đăng ký loại `grade.published` — nên thao tác lưu nháp không thể gây ra bất kỳ lời gọi nào
tới UniSIS.

---

## Tầng 2b — Bấm Publish

```json
POST /api/v1/grades/1/publish

-> { "userExternalRef":    "SV070001",
     "courseExternalCode": "SEC-TEAM07-01",
     "finalGrade":         87.5 }
```

Sự kiện phát ra: `evt_b5c9f5c85184434f` / `grade.published`

---

## Tầng 3 — Xử lý tại Integration Service

```
evt=evt_b5c9f5c85184434f type=grade.published src=LMS fn=syncGrade
key=SV070001|SEC-TEAM07-01 action=UPDATE target=SV070001/SEC-TEAM07-01
attempt=1 dur=43ms status=DONE
msg=ghi diem chinh thuc 87.5/100 -> 8.75/10 cho SV070001 tai SEC-TEAM07-01
    (source=LMS, upsert, letterGrade de UniSIS tu tinh)
```

Chú ý `src=LMS` — đây là sự kiện đầu tiên đến từ phía UniLearn.

---

## Tầng 4 — Trạng thái sau tại UniSIS

```
studentId   = SV070001
sectionId   = SEC-TEAM07-01
finalScore  = 8.75          <- 87.5/100 quy doi
letterGrade = A             <- UniSIS TU TINH, minh khong gui
source      = LMS
```

Đối chiếu bảng ánh xạ mục 16.2:

| Nguồn (UniLearn) | Đích (UniSIS) | Quy tắc | Kết quả |
|---|---|---|---|
| `userExternalRef` | `studentId` | bắt buộc external reference | `SV070001` ✅ |
| `courseExternalCode` | `sectionId` | bắt buộc external code | `SEC-TEAM07-01` ✅ |
| `finalGrade` 0..100 | `finalScore` 0..10 | `round(finalGrade / 10, 2)` | `87.5 → 8.75` ✅ |
| hằng | `source` | `"LMS"` | `LMS` ✅ |
| — | `letterGrade` | **cố ý không gửi** | UniSIS tự tính `A` ✅ |

---

## Tiêu chí nghiệm thu mục 16.6

| # | Tiêu chí | Kết quả |
|---|---|---|
| 1 | LMS `finalGrade=87.5` → SIS `finalScore=8.75` | ✅ |
| 2 | Điểm chỉ đồng bộ sau thao tác publish | ✅ DRAFT chờ 12 giây vẫn không đồng bộ |
| 3 | `studentId`/`sectionId` lấy từ external reference, không từ id nội bộ LMS | ✅ ca kiểm thử I44 |
| 4 | SIS `letterGrade` tính đúng nếu không gửi `letterGrade` | ✅ `8.75 → A` |

Kịch bản chấm **T06** của đề: *"Nhập 87.5 trên LMS và Publish → SIS có finalScore=8.75 cho
đúng Student/Section"* — đạt.

---

## Quyết định thiết kế quan trọng: 422 ENROLLMENT_NOT_FOUND

Mục 16.5 nêu tình huống UniSIS từ chối ghi điểm vì cặp Student/Section chưa có đăng ký học.

**Integration Service không được phép tự tạo enrollment để "chữa".** Đăng ký học thuộc thẩm
quyền của UniSIS và của Phòng Đào tạo — đây là **ranh giới trách nhiệm**, không phải giới
hạn kỹ thuật. Thử lại cũng vô ích vì dữ liệu sẽ không tự xuất hiện.

Hành động đúng: chuyển thẳng dead-letter kèm log nêu rõ nguyên nhân, để con người quyết định.
Đúng yêu cầu NFR-03 *"không thử lại vô hạn lỗi dữ liệu"*.

Ca kiểm thử `I45` kiểm chứng cả hai vế: sự kiện vào dead-letter ngay ở lần thử đầu tiên,
**và** không có lời gọi `POST /api/v1/enrollments` nào được phát ra.

---

## Về việc làm tròn

Dùng `BigDecimal` với `HALF_UP`, không dùng số thực:

```java
BigDecimal.valueOf(finalGrade).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP)
```

Làm tròn số thực nhị phân cho kết quả không ổn định ở chữ số thứ hai — mà đây là **điểm số
chính thức** của sinh viên. Ca kiểm thử `U01` kiểm tra 5 giá trị gồm cả `87.55 → 8.76`
và `87.54 → 8.75`.

---

## Ca kiểm thử tự động

| Mã | Nội dung |
|---|---|
| `U01` | Quy đổi thang điểm: `87.5→8.75`, `0→0.00`, `100→10.00`, làm tròn HALF_UP, chặn ngoài thang |
| `I42` | `87.5` → `8.75` tại đúng Student/Section, `source=LMS` |
| `I43` | **Không** gửi `letterGrade` |
| `I44` | Dùng external reference chứ không dùng `userId`/`courseId` nội bộ |
| `I45` | 422 `ENROLLMENT_NOT_FOUND` → dead-letter ngay, không tự tạo enrollment |
| `I46` | UniSIS trả 503 → RETRYING rồi DONE |
| `I47` | Gửi lặp `grade.published` chỉ ghi điểm một lần |
| `I48` | Điểm ngoài thang 0..100 bị chặn **trước** khi gọi API |
| — | Điểm DRAFT không bao giờ đến được bộ xử lý |

Đối chiếu test tối thiểu mục 16.7 — đủ cả 4 mục: `87.5→8.75`, `0→0.0` và `100→10.0`,
duplicate publish, SIS trả 503.

Tổng bộ kiểm thử: **63/63 xanh**.

---

## Còn thiếu

- [ ] Ảnh UniLearn → Sổ điểm — bản ghi DRAFT vừa tạo
- [ ] Ảnh UniSIS → Điểm chính thức — **chưa có gì** khi mới lưu nháp
- [ ] Ảnh UniLearn → Sổ điểm — sau khi bấm Publish
- [ ] Ảnh UniLearn → Tín hiệu — sự kiện `grade.published` kèm `eventId`
- [ ] Ảnh UniSIS → Điểm chính thức — `finalScore=8.75`, `letterGrade=A`, `source=LMS`

Che token và client secret trong mọi ảnh chụp.
