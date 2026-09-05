package vn.thanhdo.integration.inbox;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Transactional Inbox — moi su kien tu ca BA nguon kich hoat deu di qua bang nay.
 *
 * <p>Rang buoc {@code UNIQUE(source_system, event_id)} la nen tang cua chong trung
 * (NFR-01). Quy tac sinh {@code eventId} khac nhau theo nguon — xem muc 5.4:
 * webhook va poller lay nguyen eventId cua he thong nguon, con reconciler dung
 * {@code recon:{runId}:{entityType}:{businessKey}} voi runId sinh MOI moi luot chay.
 */
@Entity
@Table(name = "inbox_event")
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", nullable = false, length = 16)
    private String sourceSystem;      // SIS | LMS | RECON

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "business_key", length = 128)
    private String businessKey;

    @Lob
    @Column(name = "payload")
    private String payload;           // dung lam desiredHint (muc 3.3)

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InboxStatus status = InboxStatus.RECEIVED;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "worker_id", length = 64)
    private String workerId;

    @Column(name = "picked_at")
    private Instant pickedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_action", length = 16)
    private String lastAction;        // CREATE | UPDATE | DELETE | NOOP

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected InboxEvent() { }

    public InboxEvent(String sourceSystem, String eventId, String eventType,
                      String businessKey, String payload, Instant occurredAt) {
        this.sourceSystem = sourceSystem;
        this.eventId = eventId;
        this.eventType = eventType;
        this.businessKey = businessKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.receivedAt = Instant.now();
        this.status = InboxStatus.RECEIVED;
    }

    /**
     * Su kien co con du moi de dung payload lam desired hay khong (muc 3.3).
     * Neu da nam trong hang cho qua lau, hoac dang o lan thu lai thu hai tro di,
     * thi phai doc lai tu nguon su that de khong ghi de bang du lieu cu.
     */
    public boolean hintIsFresh(long maxAgeSeconds) {
        if (attempt > 1) {
            return false;
        }
        Instant basis = occurredAt != null ? occurredAt : receivedAt;
        return basis != null
                && basis.isAfter(Instant.now().minusSeconds(maxAgeSeconds));
    }

    public Long getId() { return id; }
    public String getSourceSystem() { return sourceSystem; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String v) { this.businessKey = v; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public InboxStatus getStatus() { return status; }
    public void setStatus(InboxStatus v) { this.status = v; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int v) { this.attempt = v; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant v) { this.nextAttemptAt = v; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String v) { this.workerId = v; }
    public Instant getPickedAt() { return pickedAt; }
    public void setPickedAt(Instant v) { this.pickedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public String getLastAction() { return lastAction; }
    public void setLastAction(String v) { this.lastAction = v; }
    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }

    /** So lan da THU LAI = so lan xu ly tru lan dau. */
    public int getRetryCount() { return Math.max(0, attempt - 1); }
}
