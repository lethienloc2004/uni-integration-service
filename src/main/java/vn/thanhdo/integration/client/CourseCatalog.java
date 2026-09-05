package vn.thanhdo.integration.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Bo nho dem danh muc hoc phan cua UniSIS.
 *
 * <p><b>Vi sao can:</b> {@code GET /api/v1/courses} khong co bo loc, luon tra ve toan bo
 * danh muc. Neu moi su kien section deu goi lai thi mot dot doi soat 15 lop se tieu 15
 * request chi de lay ten hoc phan — dung kieu lang phi ma NFR-12 cam.
 *
 * <p><b>Khong anh huong tinh dung dan:</b> xoa sach bo nho dem thi he thong van chay dung,
 * chi ton them request. Neu tra cuu that bai, ben goi rut gon title con moi courseCode —
 * muc 12.5 cua de bai cho phep dung title toi thieu chua courseCode.
 *
 * <p><b>Danh doi da chon:</b> khong lam moi khi gap ma la. Mot hoc phan vua duoc them vao
 * danh muc co the tam thoi cho ra title ngan trong toi da {@value #TTL_MINUTES} phut; lan
 * hoi tu ke tiep se tu sua lai vi ham hoi tu von so sanh va PATCH phan chenh lech. Doi lai,
 * khong bao gio phat sinh mot request tra cuu cho moi ma khong biet.
 */
@Component
public class CourseCatalog {

    private static final Logger log = LoggerFactory.getLogger(CourseCatalog.class);
    private static final long TTL_MINUTES = 5;
    private static final Duration TTL = Duration.ofMinutes(TTL_MINUTES);

    private final SisApi sis;

    private volatile Map<String, String> cache = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public CourseCatalog(SisApi sis) {
        this.sis = sis;
    }

    /** Ten hoc phan theo ma; {@code null} khi khong tra duoc. */
    public String courseName(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            return null;
        }
        return current().get(courseCode);
    }

    /** Buoc nap lai o lan goi ke tiep. Giu lai du lieu cu de con dung khi nguon tam loi. */
    public void invalidate() {
        this.loadedAt = Instant.EPOCH;
    }

    private Map<String, String> current() {
        if (Instant.now().isBefore(loadedAt.plus(TTL))) {
            return cache;
        }
        try {
            Map<String, String> loaded = sis.courseCatalog();
            this.cache = loaded;
            this.loadedAt = Instant.now();
            log.debug("da nap danh muc hoc phan: {} ma", loaded.size());
            return loaded;
        } catch (Exception e) {
            // Khong lam hong ca su kien chi vi tra cuu ten hoc phan that bai.
            log.warn("khong nap duoc danh muc hoc phan, dung du lieu cu / title rut gon: {}",
                    e.getMessage());
            return cache;
        }
    }
}
