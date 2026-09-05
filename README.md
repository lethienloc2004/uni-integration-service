# UniSIS ↔ UniLearn Integration Service

Integration Service cho học phần **Kiến trúc và Tích hợp hệ thống** — kết nối UniSIS (SIS)
và UniLearn (LMS) qua REST API và Webhook.

Đây **không phải** một API nghiệp vụ. Dịch vụ này chủ yếu là HTTP *client* của hai hệ thống
nguồn; phần phục vụ vào chỉ gồm bộ nhận webhook, `/health` và vài endpoint quản trị.

Thiết kế chi tiết: xem `ĐỀ XUẤT GIẢI PHÁP v2.0`. Các tham chiếu "mục 4.3", "BV-4", "I01"…
trong mã nguồn đều trỏ về tài liệu đó.

| Tài liệu | Nội dung |
|---|---|
| [AGENTS.md](AGENTS.md) | Ngữ cảnh cho AI agent: ràng buộc, cạm bẫy, lệnh thường dùng |
| [HUONG-DAN-CHAY.md](HUONG-DAN-CHAY.md) | Chạy dự án trên IntelliJ, cấu hình, gỡ rối |
| [KICH-BAN-TEST.md](KICH-BAN-TEST.md) | **Kịch bản chạy thử một mạch** — 1 sinh viên, 1 lớp, đi hết 8 INT trong ~25 phút |
| [HUONG-DAN-KIEM-THU.md](HUONG-DAN-KIEM-THU.md) | Tra cứu kiểm thử từng INT riêng lẻ: nhập gì, ở đâu, thứ tự nào |
| [docs/BaoCao-BaiTapLon-Integration-Service.docx](docs/BaoCao-BaiTapLon-Integration-Service.docx) | **Báo cáo bài tập lớn bản Word** — sản phẩm nộp bài số 10, 105 trang, 5 chương + phụ lục A–F |
| [docs/TEST-REPORT.md](docs/TEST-REPORT.md) | **Test Report** — đối chiếu 12 kịch bản chấm T01–T12, nộp cho giảng viên |
| [docs/diagrams/](docs/diagrams/) | Sơ đồ kiến trúc và ba sơ đồ tuần tự (sản phẩm nộp bài số 03 và 06) |
| [docs/screenshots/](docs/screenshots/) | 41 ảnh bằng chứng bốn tầng cho 8 INT — đã chèn vào Phụ lục C |
| [docs/evidence/](docs/evidence/) | Bằng chứng đã thu thập cho từng INT |

---

## Tiến độ

| Yêu cầu | Luồng | Trạng thái |
|---|---|---|
| Hạ tầng | inbox, worker, retry, audit, mapping, rate limit | ✅ xong |
| INT-01 | Student → LMS User | ✅ xong |
| INT-05 | status → enabled | ✅ xong (cùng `ensureUser`) |
| INT-02 | Section → LMS Course | ✅ xong |
| INT-07 | đổi giảng viên | ✅ xong (cùng `ensureCourse`) |
| INT-03 / INT-04 | Enrollment → Membership | ✅ xong |
| INT-06 | grade.published → điểm SIS | ✅ xong |
| INT-08 | learner.at_risk → Advising Alert | ✅ xong |
| Reconciler | đối soát theo tập hợp | ✅ xong |

---

## Yêu cầu môi trường

- JDK 21 trở lên (đã kiểm chứng trên Temurin 25)
- Maven 3.9+
- Docker (tuỳ chọn — chỉ cần khi đóng gói)

Không cần cài cơ sở dữ liệu: H2 là thư viện nhúng, dữ liệu nằm trong `./data/integration.mv.db`.

---

## Chạy lần đầu

```bash
cp .env.example .env
```

Mở `.env` và điền `TENANT_ID`, `SIS_URL`, `LMS_URL`, `CLIENT_SECRET` theo phiếu tài khoản.
**Không commit file `.env`** — nó đã nằm trong `.gitignore`.

### Chạy bằng Maven

```bash
mvn spring-boot:run
```

### Chạy bằng Docker

Bản tự chứa (build nhiều tầng, Maven chạy bên trong container):

```bash
docker build -t uni-integration .
```

Bản nhẹ — dùng khi máy ít RAM hoặc muốn xác minh nhanh. Cần `mvn package` trước:

```bash
docker build -f Dockerfile.jar -t uni-integration .
```

```bash
docker run --env-file .env -p 8080:8080 -v "$(pwd)/data:/app/data" uni-integration
```

Gắn volume `data` để bảng ánh xạ và các sự kiện đang chờ không mất khi chạy lại container.

---

## Kiểm tra nhanh

```bash
curl http://localhost:8080/health
```

Mặc định `/health` là phép kiểm tra rẻ, không gọi ra ngoài. Muốn thử thật việc lấy token
ở cả hai hệ thống thì thêm `?deep=true` — lúc đó mới tốn hai lượt gọi.

---

## Chạy kiểm thử

```bash
mvn test
```

**84 ca kiểm thử**, không cần mạng và không cần hai hệ thống nguồn — WireMock giả lập cả hai.

| Lớp kiểm thử | Phạm vi |
|---|---|
| `SisStudentMappingTest` | 21 ca thuần: quy đổi thang điểm, ánh xạ trạng thái, ghép tên, mốc thời gian, che bí mật |
| `TenantStartupCheckTest` | 3 ca: NFR-08 — cấu hình tenant sai thì chặn khởi động |
| `EnsureUserIntegrationTest` | 16 ca: INT-01, INT-05, chống trùng, retry 503, **429 + Retry-After**, 409, mất ánh xạ, poller |
| `EnsureCourseIntegrationTest` | 9 ca: INT-02, INT-07, danh mục lỗi, `CLOSED → ARCHIVED` |
| `EnsureMembershipIntegrationTest` | 11 ca: INT-03, INT-04, tự khôi phục phụ thuộc, 409/404 idempotent |
| `SyncGradeIntegrationTest` | 8 ca: INT-06, quy đổi điểm, DRAFT không đồng bộ, 422 dead-letter |
| `RaiseAdvisingAlertIntegrationTest` | 9 ca: INT-08, chống dội cảnh báo, NORMAL không tạo alert |
| `ReconcilerIntegrationTest` | 7 ca: NFR-06, kịch bản T11, quy tắc `runId`, 6 request |

Vài ca đáng chú ý:

| Mã | Nội dung |
|---|---|
| I03 | Cùng `eventId` giao ba lần chỉ gây một tác động |
| I04 | LMS trả 503 hai lần rồi 201 — qua RETRYING rồi DONE |
| I10 | Xoá bản ghi ánh xạ mà User còn → tự phục hồi, không tạo trùng |
| I15 | Bộ che bí mật không để lọt token ra log |
| I21 | Nguồn không kết nối được là lỗi TẠM THỜI, phải RETRYING chứ không dead-letter |
| I22 | Điểm mốc polling tiến, `since` mã hoá đúng một lần |
| I27 | INT-07 đổi giảng viên → PATCH, Course `id` giữ nguyên |
| I33, I34 | Sự kiện đăng ký đến khi chưa có User/Course → tự khôi phục phụ thuộc |
| I37 | INT-04 gỡ Membership nhưng **không** xoá User/Course |
| I44 | INT-06 dùng external reference, không dùng id nội bộ LMS |
| I45 | 422 ENROLLMENT_NOT_FOUND → dead-letter, **không** tự tạo enrollment |
| I57 | Hai lượt đối soát liên tiếp đều sinh được việc (quy tắc `runId`) |
| I59 | Sự kiện tổng hợp mang dữ liệu → đối soát không đọc lại nguồn |
| I60 | Membership không dịch ngược được thì **không** gỡ |
| I63, I64 | 429 dùng `Retry-After` và không đốt ngân sách thử lại (T12) |

---

## Endpoint

| Endpoint | Công dụng |
|---|---|
| `GET /swagger-ui.html` | **Swagger UI** — gọi thử mọi endpoint, khỏi gõ curl |
| `GET /v3/api-docs` | Đặc tả OpenAPI 3.1 |
| `GET /health` | Trạng thái, tenant, ngân sách tần suất còn lại, tồn đọng inbox |
| `POST /webhooks/sis` | Nhận callback từ UniSIS — chỉ ghi inbox rồi trả 200 |
| `POST /webhooks/lms` | Nhận callback từ UniLearn |
| `GET /admin/inbox` | Danh sách inbox (lọc `?status=RETRYING`) |
| `GET /admin/inbox/summary` | Đếm theo từng trạng thái |
| `GET /admin/events/{eventId}` | Toàn bộ vòng đời một sự kiện |
| `GET /admin/dead-letter` | Sự kiện lỗi dữ liệu cần rà soát |
| `POST /admin/inbox/{id}/requeue` | Đưa lại vào hàng đợi |
| `POST /admin/reconcile?dryRun=true` | **Đối soát chế độ chỉ báo cáo** — cho thấy phạm vi lệch |
| `POST /admin/reconcile` | Chạy đối soát thật |
| `GET /admin/reconcile/last` | Báo cáo lượt đối soát gần nhất |
| `POST /admin/drain` | Chạy ngay một lượt xử lý, khỏi chờ lịch |

`GET /admin/inbox` chính là **tầng 3** của quy trình gỡ lỗi bốn tầng — khi giảng viên bấm
tạo một sinh viên mới, làm mới trang này và chiếu dòng tương ứng lên màn hình.

---

## Bản đồ mã nguồn

| Thành phần | Vị trí |
|---|---|
| Hàm hội tụ `ensureUser` (INT-01, INT-05) | `sync/EnsureUserHandler.java` |
| Hàm hội tụ `ensureCourse` (INT-02, INT-07) | `sync/EnsureCourseHandler.java` |
| Hàm hội tụ `ensureMembership` (INT-03, INT-04) | `sync/EnsureMembershipHandler.java` |
| Luồng ngược `syncGrade` (INT-06) | `sync/SyncGradeHandler.java` |
| Luồng ngược `raiseAdvisingAlert` (INT-08) | `sync/RaiseAdvisingAlertHandler.java` |
| Đối soát theo tập hợp (NFR-06) | `reconcile/Reconciler.java` |
| Bảng ánh xạ INT-01 | `client/dto/SisStudent.java`, `client/LmsApi.java` |
| Transactional Inbox, chống trùng | `inbox/InboxService.java` |
| Giành việc, retry, dead-letter | `inbox/Worker.java` |
| Hợp đồng lỗi (bảng 4.4) | `client/ErrorClass.java` |
| Token, rate limit, che bí mật | `client/ApiClient.java` |
| Ánh xạ ID (bộ nhớ đệm, BV-4) | `mapping/IdMappingService.java` |
| Poller có điểm mốc | `poll/EventPoller.java` |
| Nhật ký kiểm toán | `audit/AuditService.java` |
| Năm bảng CSDL | `resources/db/migration/` |

---

## Ghi chú kỹ thuật

**Ép HTTP/1.1.** JDK HttpClient mặc định thử nâng cấp lên HTTP/2 trên kết nối `http://`
không mã hoá; nhiều máy chủ đóng kết nối thay vì từ chối tử tế, gây `EOFException`.
`ApiClient` ép `HTTP_1_1`.

**Vỏ bọc JSON.** Đã đối chiếu với mã nguồn thật của hai hệ thống: mọi endpoint danh sách đều
trả **mảng thuần**. `ApiClient.unwrapList` vẫn chấp nhận thêm dạng bọc trong
`content` / `items` / `data` / `results` để không vỡ nếu môi trường lab của giảng viên dùng
phân trang.

**Ghim phiên bản ảnh Maven.** Tag nổi `maven:3.9-eclipse-temurin-21` từng kéo về một bản mà
`java` bên trong không chạy được, khiến `mvn package` không tạo `target/` và build chết ở
`COPY --from=build` với thông báo rất khó hiểu. Đã ghim `3.9.9`.

**Hai bảng đã bị loại bỏ có chủ đích.** `sync_state` bị bỏ ngay từ bản thiết kế v2.0 vì hàm
hội tụ luôn quy chiếu về nguồn sự thật nên không có bài toán ghi lùi trạng thái. `alert_dedup`
bị bỏ ở migration V2 vì việc chống trùng cảnh báo hỏi thẳng UniSIS — nguồn sự thật là hệ thống
đích chứ không phải một bảng cục bộ. Giữ một bảng không còn tác dụng sẽ làm yếu chính lập luận
kiến trúc.
