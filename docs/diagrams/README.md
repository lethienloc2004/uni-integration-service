# Sơ đồ — sản phẩm nộp bài số 03 và 06

| Tệp | Nội dung | Dùng ở |
|---|---|---|
| `hinh-1-kien-truc.png` | Kiến trúc tổng thể | Báo cáo mục 2.2 |
| `hinh-2-sd-enrollment.png` | Tuần tự — tạo đăng ký học khi phụ thuộc chưa có (INT-03) | Báo cáo mục 4.1 |
| `hinh-3-sd-retry.png` | Tuần tự — lỗi tạm thời và thử lại (T10) | Báo cáo mục 4.2 |
| `hinh-4-sd-grade.png` | Tuần tự — đồng bộ ngược điểm đã publish (INT-06) | Báo cáo mục 4.4 |

Ảnh PNG sinh từ các tệp `.puml` cùng thư mục, ở 300 DPI.

## Dựng lại

Cần Java 17+ và Graphviz. **Lưu ý:** `java` trên PATH của máy này là Java 8, PlantUML sẽ
báo `UnsupportedClassVersionError`. Phải đặt lại JDK trước:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
java -jar C:/K15/plantuml.jar -tpng -charset UTF-8 -Sdpi=300 -o ./out hinh-*.puml
```

Kiểm tra Graphviz có được nhận không: `java -jar C:/K15/plantuml.jar -testdot`

`_style.puml` giữ bảng màu dùng chung — sửa một chỗ là cả bốn sơ đồ đổi theo.

## Khác với bản thiết kế v2.0

Sơ đồ kiến trúc trong Phụ lục A của bản thiết kế ghi bảng `alert_dedup`. Migration `V2` đã
xoá bảng đó — việc chống trùng cảnh báo hỏi thẳng UniSIS chứ không dựa vào bảng cục bộ.
Sơ đồ ở đây ghi đúng bốn bảng còn lại.
