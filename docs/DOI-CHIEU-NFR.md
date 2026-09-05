# Đối chiếu 12 yêu cầu phi chức năng — mục 19 của đề

> Đề bài nói rõ: *"Chỉ gọi được API nhưng không xử lý các tình huống này chưa được xem là
> một hệ thống tích hợp hoàn chỉnh."* Đây là 1.0 điểm rubric "Resilience" cộng phần lớn
> 1.0 điểm "Testing" và 0.5 điểm "Deployment, logging, vận hành".

Trạng thái: **12/12 đã kiểm chứng**.

---

| Mã | Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|---|
| NFR-01 | Chống trùng theo `eventId` | ✅ | `I03`, `I24`, `I47`, `I52` |
| NFR-02 | Thử lại lỗi tạm thời | ✅ | `I04`, `I26`, `I46`, `I54`, **`I63`, `I64`** |
| NFR-03 | Không thử lại vô hạn lỗi dữ liệu | ✅ | `I09`, `I45`, `I53` |
| NFR-04 | Nhật ký kiểm toán | ✅ | `integration_log`, `/admin/events/{eventId}` |
| NFR-05 | Ánh xạ ID | ✅ | `I10`, `I25`, `I39` |
| NFR-06 | Đối soát | ✅ | `I55`–`I61`, T11 chạy thật |
| NFR-07 | An toàn thông tin | ✅ | `I15`, `.gitignore`, quét log |
| NFR-08 | Cách ly tenant | ✅ | **`U10`–`U12`**, header trong `I01` |
| NFR-09 | Health và khả năng quan sát | ✅ | `/health`, `/health?deep=true` |
| NFR-10 | Kiểm thử tự động | ✅ | **84 ca**, 27 ca tình huống lỗi |
| NFR-11 | Triển khai độc lập | ✅ | Phòng sạch + **2 ảnh Docker chạy thật INT-01** |
| NFR-12 | Ý thức về giới hạn tần suất | ✅ | `U06`, `I58`, **`I63`, `I64`** |

Chữ **in đậm** là phần vừa bổ sung sau đợt rà soát này.

---

## Hai lỗ hổng phát hiện khi rà soát

### NFR-02 và NFR-12 — thiếu ca kiểm thử cho 429

Mã nguồn xử lý `429` đúng ngay từ đầu: `ApiClient` đọc header `Retry-After`, `Worker` dùng nó
thay cho dãy backoff, và cố ý **hoàn lại lượt thử** vì áp lực tần suất không phải thất bại.

Nhưng **không có ca kiểm thử nào chứng minh điều đó**. Kịch bản chấm **T12** nhắm thẳng vào
đây. Đã bổ sung:

- **`I63`** — trả `429` kèm `Retry-After: 7`, khẳng định mốc thử lại rơi vào khoảng
  `+5s … +10s`. Dãy backoff trong cấu hình kiểm thử là 0 giây, nên nếu hệ thống dùng backoff
  thay vì đọc header thì mốc sẽ là *ngay bây giờ* và ca kiểm thử đỏ.
- **`I64`** — chạy 9 lượt xử lý với `429` liên tục, nhiều hơn giới hạn 6 lần thử lại. Sự kiện
  phải vẫn ở `RETRYING`, dead-letter phải bằng 0. Nếu `429` bị tính là thất bại thì từ lượt
  thứ bảy nó đã rơi vào dead-letter.

### NFR-08 — thiếu kiểm tra tenant lúc khởi động

Bảng rủi ro trong bản thiết kế v2.0 có ghi *"ứng dụng cũng tự dừng khi khởi động nếu cấu hình
tenant không nhất quán"* — nhưng phần đó **chưa hề được cài đặt**. Tài liệu hứa nhiều hơn mã
nguồn làm.

Đã bổ sung `config/TenantStartupCheck.java`: kiểm tra cấu hình ngay lúc khởi động và **dừng
hẳn** nếu thiếu `TENANT_ID`, thiếu client secret, hoặc `SIS_URL` trùng `LMS_URL`.

Dừng hẳn thay vì cảnh báo là có lý do: trộn tenant khiến dữ liệu hai bên không bao giờ khớp
**mà nhật ký vẫn báo thành công** — người dùng mất hàng giờ đi tìm nguyên nhân. Thông báo lỗi
chỉ thẳng sang `HUONG-DAN-CHAY.md` mục 2.

Đây chỉ là kiểm tra **cấu hình**, không gọi ra ngoài, nên dịch vụ vẫn khởi động được khi
UniSIS/UniLearn chưa sẵn sàng. Kiểm chứng kết nối thật thì dùng `/health?deep=true`.

Ca kiểm thử `U10`–`U12`. Khi khởi động, dịch vụ in một dòng đậm:

```
=== TENANT DANG LAM VIEC: TEAM07 === SIS=http://127.0.0.1:8001 LMS=http://127.0.0.1:8002
```

---

## NFR-11 — đã kiểm chứng đầy đủ

Yêu cầu có hai vế: *"Có Dockerfile"* và *"chạy được từ README mà không phụ thuộc IDE của
thành viên nhóm"*.

### Vế 2 — chạy độc lập: ✅ đã kiểm chứng bằng phòng sạch

Chép **duy nhất** file jar sang một thư mục trống hoàn toàn, không có mã nguồn, không có
IntelliJ, không có biến môi trường nào.

**Lần 1 — không có gì ngoài file jar:** dịch vụ từ chối khởi động với thông báo dùng được ngay

```
Cau hinh khong hop le, dich vu khong the khoi dong:
  - Client secret cua UniSIS dang trong. Dat CLIENT_SECRET (hoac SIS_CLIENT_SECRET)
    qua bien moi truong hoac file .env.
  - Client secret cua UniLearn dang trong. ...
```

**Lần 2 — thêm một file `.env` bên cạnh:** khởi động bình thường

```
=== TENANT DANG LAM VIEC: TEAM07 === SIS=http://127.0.0.1:8001 LMS=http://127.0.0.1:8002
Tomcat started on port 8099
Started IntegrationApplication in 10.689 seconds
```

`/health` trả `{"status":"UP","tenantId":"TEAM07",...}`, và cơ sở dữ liệu H2 tự được tạo
ngay trong thư mục đó (`data/integration.mv.db`).

Không phụ thuộc IDE, không phụ thuộc mã nguồn, không phụ thuộc máy của ai.

> ⚠️ **Lưu ý về thư mục làm việc.** Cả `.env` lẫn `data/` đều tính theo **thư mục đang đứng**,
> không phải theo vị trí file jar. Chạy `java -jar duong/dan/app.jar` từ chỗ khác sẽ không nạp
> được `.env` và sẽ tạo `data/` ở chỗ khác. Luôn `cd` vào thư mục chứa `.env` trước khi chạy —
> hoặc truyền cấu hình qua biến môi trường, vốn không phụ thuộc thư mục.

### Vế 1 — Docker: ✅ đã dựng và chạy thật cả hai bản

| Ảnh | Cách dựng | Kết quả |
|---|---|---|
| `uni-integration:full` | `Dockerfile` — Maven chạy trong container, tự chứa | ✅ |
| `uni-integration:jar` | `Dockerfile.jar` — chép jar dựng sẵn, nhẹ và nhanh | ✅ |

Cả hai đều được kiểm chứng bằng cùng một bộ phép thử:

```
uid=1001(appuser) gid=1001(appuser)          <- khong chay bang root
/health?deep=true  -> canAuthenticate: true o CA HAI he thong
Swagger UI         -> HTTP 200
```

Và quan trọng nhất — **chạy thật một luồng INT-01 qua container**:

```
TRUOC: chua co User cho SV07DOCK2
       (tao Student tren UniSIS, cho poller cua container)
SAU  : id=102  displayName='Vo Thi Hai'  userType=LEARNER
inbox: student.created  status=DONE  action=CREATE
```

Container không chỉ khởi động được mà thực sự làm công việc tích hợp.

Container nói chuyện với hai hệ thống trên máy thật qua `host.docker.internal`:

```bash
docker run --rm --env-file .env -p 8080:8080 -e SIS_URL=http://host.docker.internal:8001 -e LMS_URL=http://host.docker.internal:8002 --add-host=host.docker.internal:host-gateway -v "${PWD}/data:/app/data" uni-integration:full
```

`127.0.0.1` bên trong container trỏ về chính container, không phải máy thật — đây là lỗi rất
hay gặp khi lần đầu chạy bằng Docker.

Đọc kỹ thì thấy hai điểm rủi ro, đã sửa:

**1. `HEALTHCHECK` gọi `wget` — đã bỏ.** Ảnh `eclipse-temurin:21-jre` không bảo đảm có sẵn
`wget` hay `curl`. Một healthcheck gọi công cụ không tồn tại sẽ làm container **luôn** bị đánh
dấu `unhealthy` — tệ hơn hẳn là không khai báo gì. Endpoint thăm dò vẫn là `GET /health`; khai
báo healthcheck ở tầng điều phối, nơi biết chắc có công cụ nào.

**2. `useradd` không tạo nhóm tường minh — đã sửa.** `chown appuser:appuser` chỉ chạy được nếu
nhóm `appuser` tồn tại, mà hành vi tự tạo nhóm khác nhau giữa các bản phân phối. Giờ tạo nhóm
bằng `groupadd` rồi mới `useradd -g`.

### Ba lỗi thật đã sửa nhờ dựng thử

Không dựng thử thì cả ba đều lọt tới buổi bảo vệ.

**3. Tag ảnh nổi `maven:3.9-eclipse-temurin-21` kéo về một bản hỏng — đã ghim `3.9.9`.**
Trong bản đó `uname` chạy bình thường nhưng `java` im lặng không chạy, nên `mvn package`
không tạo ra `target/`, và build chết ở bước `COPY --from=build` với thông báo cực kỳ khó
hiểu:

```
ERROR: lstat /build/target: no such file or directory
```

Nhìn thông báo đó gần như không thể đoán được nguyên nhân nằm ở ảnh cơ sở. Ghim phiên bản
cụ thể vừa loại hẳn lớp lỗi này vừa làm build tái lập được.

---

## Ghi chú về NFR-12 — thứ tự ưu tiên

Bản thiết kế v2.0 mục 6.2 nêu thứ tự ưu tiên *worker > poller > reconciler*. Trên thực tế
**chưa cài bộ lập lịch theo mức ưu tiên**, và cũng không cần:

- Bộ đối soát chỉ tốn **6 request** cho khâu phát hiện (`I58`)
- Khâu sửa lệch đi qua chính hàng đợi nghiệp vụ, tức là đã nằm trên đường ưu tiên cao
- Poller tốn 6 request/phút, khoảng 6% ngân sách

Nên phần lớn ngân sách vốn đã thuộc về luồng nghiệp vụ. Xây một bộ lập lịch ưu tiên vào lúc
này là giải quyết một vấn đề chưa xảy ra. Ghi lại ở đây để trả lời thẳng nếu bị hỏi, thay vì
để tài liệu hứa nhiều hơn mã nguồn làm — đúng lỗi vừa mắc ở NFR-08.

---

## Thống kê kiểm thử

**84 ca, 100% xanh**, chạy dưới 30 giây, không cần mạng và không cần hai hệ thống nguồn.

| Lớp | Số ca | Phạm vi |
|---|---|---|
| `SisStudentMappingTest` | 21 | Quy đổi điểm, ánh xạ trạng thái, ghép tên, mốc thời gian, che bí mật |
| `TenantStartupCheckTest` | 3 | NFR-08 |
| `EnsureUserIntegrationTest` | 16 | INT-01, INT-05, 429, chống trùng, retry, poller |
| `EnsureCourseIntegrationTest` | 9 | INT-02, INT-07 |
| `EnsureMembershipIntegrationTest` | 11 | INT-03, INT-04, tự khôi phục phụ thuộc |
| `SyncGradeIntegrationTest` | 8 | INT-06 |
| `RaiseAdvisingAlertIntegrationTest` | 9 | INT-08 |
| `ReconcilerIntegrationTest` | 7 | NFR-06, T11 |

Đề yêu cầu **tối thiểu 3** ca tình huống lỗi ngoài đường thuận. Hiện có **27**.
