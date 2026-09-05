# CLAUDE.md

Toàn bộ hướng dẫn cho AI agent nằm ở **[AGENTS.md](AGENTS.md)** — đọc file đó trước khi
sửa bất cứ thứ gì.

File này tồn tại vì Claude Code đọc `CLAUDE.md`, còn `AGENTS.md` là quy ước dùng chung cho
nhiều công cụ agent khác. Giữ nội dung ở một chỗ duy nhất để không bị lệch nhau.

## Ba điều tối quan trọng, nhắc lại ở đây phòng khi không đọc tiếp

1. **`C:\Users\ADMIN\Downloads\Integration_workshop` là mã nguồn của giảng viên — CHỈ ĐỌC.**
   Không sửa, không thêm file, kể cả log tạm. Log để trong scratchpad của phiên.

2. **`PATH` ưu tiên Java 8** trong khi dự án cần JDK 21+. Luôn đặt lại trước khi gọi Maven:
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
   ```

3. **Kiểm chứng bằng cách chạy thật, đừng tuyên bố suông.** Sửa mã thì chạy `mvn test` rồi dán
   số liệu. Chưa dựng thử được thì nói rõ là chưa kiểm chứng.

Người dùng trao đổi bằng tiếng Việt; chú thích trong mã và tài liệu viết tiếng Việt **không dấu**.
