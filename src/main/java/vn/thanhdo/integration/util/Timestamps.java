package vn.thanhdo.integration.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Doc moc thoi gian tu hai he thong nguon.
 *
 * <p>PHAI khoan dung, vi hai kenh tra ve HAI DINH DANG KHAC NHAU cho cung mot khai niem:
 * <ul>
 *   <li>Webhook: {@code datetime.now(timezone.utc).isoformat()} → {@code 2026-08-15T01:51:15.196920+00:00}</li>
 *   <li>{@code GET /events}: doc tu SQLite, ma SQLite KHONG luu mui gio →
 *       {@code 2026-08-15T01:51:15.196920} — khong co offset</li>
 *   <li>Ben trong payload su kien: {@code json.dumps(default=str)} → dung dau CACH
 *       thay cho chu T: {@code 2026-08-15 01:51:15.185171}</li>
 * </ul>
 *
 * <p>{@link Instant#parse} chi chap nhan dang thu nhat. Neu dung mot minh no, moc thoi gian
 * doc tu {@code /events} se luon la null, va diem moc polling KHONG BAO GIO TIEN — bo doc
 * se tai lai toan bo bang su kien sau moi 10 giay. Lop chong trung che mat trieu chung nay
 * nen loi khong lam sai du lieu, chi am tham dot het ngan sach tan suat (NFR-12).
 *
 * <p>Gia tri khong co mui gio duoc hieu la UTC, vi may chu ghi bang
 * {@code datetime.now(timezone.utc)} roi SQLite moi lam rot phan offset.
 */
public final class Timestamps {

    private Timestamps() { }

    /** Tra ve {@code null} neu that su khong doc duoc, thay vi nem ngoai le. */
    public static Instant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();

        // Dang co dau cach thay cho chu T
        if (s.length() > 10 && s.charAt(10) == ' ') {
            s = s.substring(0, 10) + 'T' + s.substring(11);
        }

        // 1) Da co chu Z
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
            // thu dang tiep theo
        }

        // 2) Co offset dang +07:00 hoac +0000
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception ignored) {
            // thu dang tiep theo
        }

        // 3) Khong co mui gio — hieu la UTC
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }
}
