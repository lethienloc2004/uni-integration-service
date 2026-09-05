package vn.thanhdo.integration.inbox;

/**
 * Vong doi mot ban ghi inbox — muc 5.5 cua De xuat giai phap v2.0.
 *
 * <pre>
 *   RECEIVED --> PROCESSING --> DONE
 *                    |
 *                    +--> RETRYING            (429 / 503 / loi mang)
 *                    +--> PENDING_DEPENDENCY  (thieu du lieu phu thuoc)
 *                    +--> DEAD_LETTER         (loi du lieu hoac can luot thu lai)
 * </pre>
 *
 * <p>Viec phan biet ro ba nhanh loi chinh la NFR-02 va NFR-03.
 */
public enum InboxStatus {
    RECEIVED,
    PROCESSING,
    DONE,
    RETRYING,
    PENDING_DEPENDENCY,
    DEAD_LETTER
}
