package vn.thanhdo.integration.inbox;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Su kien loi du lieu can ra soat thu cong (NFR-03).
 *
 * <p>Loi KHONG bi nuot: moi ban ghi o day deu hien tren trang quan tri va co the
 * dua lai vao hang doi. Day la khac biet giua "dung thu lai" va "bo qua".
 */
@Entity
@Table(name = "dead_letter")
public class DeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inbox_event_id", nullable = false)
    private Long inboxEventId;

    @Column(name = "reason", length = 1024)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DeadLetter() { }

    public DeadLetter(Long inboxEventId, String reason) {
        this.inboxEventId = inboxEventId;
        this.reason = reason != null && reason.length() > 1000
                ? reason.substring(0, 1000) + "..." : reason;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInboxEventId() { return inboxEventId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
