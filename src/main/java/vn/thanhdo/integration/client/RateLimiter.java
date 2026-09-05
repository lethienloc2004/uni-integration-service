package vn.thanhdo.integration.client;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Token bucket theo cua so truot 60 giay — NFR-12.
 *
 * <p>Dat o muc 80 tren 100 request/phut de chua 20% bien an toan. Gioi han cua
 * lab duoc dem o tang xac thuc nen request LOI CUNG BI TINH; vi vay bo dieu tiet
 * phai dem moi luot goi di ra, khong chi dem luot thanh cong.
 *
 * <p>Khi het luot thi CHO chu khong huy: mat mot su kien vi bi tu choi tai cho
 * con te hon la cham vai giay.
 */
public class RateLimiter {

    private final int permitsPerMinute;
    private final Deque<Long> window = new ArrayDeque<>();
    private static final long WINDOW_MS = Duration.ofMinutes(1).toMillis();

    public RateLimiter(int permitsPerMinute) {
        this.permitsPerMinute = permitsPerMinute;
    }

    /** Chan lai cho toi khi con luot. */
    public void acquire() throws InterruptedException {
        while (true) {
            long waitMs;
            synchronized (this) {
                long now = System.currentTimeMillis();
                purgeExpired(now);
                if (window.size() < permitsPerMinute) {
                    window.addLast(now);
                    return;
                }
                waitMs = WINDOW_MS - (now - window.peekFirst()) + 1;
            }
            if (waitMs > 0) {
                Thread.sleep(Math.min(waitMs, 1000));
            }
        }
    }

    /** So luot con lai trong cua so hien tai — dung cho /health va ca kiem thu U06. */
    public synchronized int availablePermits() {
        purgeExpired(System.currentTimeMillis());
        return Math.max(0, permitsPerMinute - window.size());
    }

    private void purgeExpired(long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
            window.pollFirst();
        }
    }
}
