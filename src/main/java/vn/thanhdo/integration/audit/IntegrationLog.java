package vn.thanhdo.integration.audit;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Nhat ky kiem toan (NFR-04).
 *
 * <p>{@code eventId} dong vai tro MA TUONG QUAN xuyen suot: tu luc nhan, qua moi
 * lan thu lai, den trang thai cuoi. Tim theo eventId la ra toan bo vong doi.
 *
 * <p>TUYET DOI khong luu token hay client secret o bang nay (NFR-07).
 */
@Entity
@Table(name = "integration_log")
public class IntegrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "source_system", length = 16)
    private String sourceSystem;

    @Column(name = "handler", length = 64)
    private String handler;

    @Column(name = "business_key", length = 128)
    private String businessKey;

    /** CREATE | UPDATE | DELETE | NOOP — gia tri NOOP la bang chung truc quan cua chong trung. */
    @Column(name = "action", length = 16)
    private String action;

    @Column(name = "direction", length = 16)
    private String direction;      // SIS_TO_LMS | LMS_TO_SIS

    @Column(name = "endpoint", length = 256)
    private String endpoint;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "attempt")
    private Integer attempt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    protected IntegrationLog() { }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final IntegrationLog l = new IntegrationLog();
        public Builder eventId(String v) { l.eventId = v; return this; }
        public Builder eventType(String v) { l.eventType = v; return this; }
        public Builder sourceSystem(String v) { l.sourceSystem = v; return this; }
        public Builder handler(String v) { l.handler = v; return this; }
        public Builder businessKey(String v) { l.businessKey = v; return this; }
        public Builder action(String v) { l.action = v; return this; }
        public Builder direction(String v) { l.direction = v; return this; }
        public Builder endpoint(String v) { l.endpoint = v; return this; }
        public Builder httpStatus(Integer v) { l.httpStatus = v; return this; }
        public Builder attempt(Integer v) { l.attempt = v; return this; }
        public Builder durationMs(Long v) { l.durationMs = v; return this; }
        public Builder status(String v) { l.status = v; return this; }
        public Builder message(String v) { l.message = truncate(v); return this; }
        public IntegrationLog build() { l.loggedAt = Instant.now(); return l; }

        private static String truncate(String s) {
            if (s == null) return null;
            return s.length() <= 1000 ? s : s.substring(0, 1000) + "...";
        }
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSourceSystem() { return sourceSystem; }
    public String getHandler() { return handler; }
    public String getBusinessKey() { return businessKey; }
    public String getAction() { return action; }
    public String getDirection() { return direction; }
    public String getEndpoint() { return endpoint; }
    public Integer getHttpStatus() { return httpStatus; }
    public Integer getAttempt() { return attempt; }
    public Long getDurationMs() { return durationMs; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getLoggedAt() { return loggedAt; }
}
