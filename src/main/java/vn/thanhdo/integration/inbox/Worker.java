package vn.thanhdo.integration.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.audit.AuditService;
import vn.thanhdo.integration.audit.IntegrationLog;
import vn.thanhdo.integration.client.ApiException;
import vn.thanhdo.integration.client.ErrorClass;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.sync.EventHandler;
import vn.thanhdo.integration.sync.HandlerResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bo dieu phoi: lay ban ghi inbox, GIANH QUYEN xu ly, goi dung ham nghiep vu,
 * roi cap nhat trang thai va ghi nhat ky.
 *
 * <p>Toan bo phan ung voi loi tuan theo HOP DONG LOI o muc 4.4. Diem quan trong:
 * trang thai thu lai duoc luu BEN trong bang inbox chu khong nam trong bo nho,
 * nen khoi dong lai giua chung khong lam mat su kien nao (ca kiem thu I12).
 */
@Component
public class Worker {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private static final List<InboxStatus> CLAIMABLE = List.of(
            InboxStatus.RECEIVED, InboxStatus.RETRYING, InboxStatus.PENDING_DEPENDENCY);

    private final InboxRepository repo;
    private final DeadLetterRepository deadLetters;
    private final AuditService audit;
    private final IntegrationProperties props;
    private final List<EventHandler> handlers;
    private final String workerId;

    public Worker(InboxRepository repo, DeadLetterRepository deadLetters, AuditService audit,
                  IntegrationProperties props, List<EventHandler> handlers) {
        this.repo = repo;
        this.deadLetters = deadLetters;
        this.audit = audit;
        this.props = props;
        this.handlers = handlers;
        this.workerId = "w-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${integration.worker.poll-interval-ms:1000}")
    public void tick() {
        if (!props.getWorker().isEnabled()) {
            return;
        }
        drainOnce();
    }

    /** Xu ly mot luot. Tach rieng de ca kiem thu goi truc tiep, khong phai cho lich. */
    public int drainOnce() {
        List<InboxEvent> due = repo.findDue(Instant.now(),
                PageRequest.of(0, props.getWorker().getBatchSize()));

        int processed = 0;
        for (InboxEvent candidate : due) {
            // GIANH VIEC bang cap nhat co dieu kien (muc 7.2).
            // Tien trinh thua nhan ve 0 dong va bo qua ban ghi nay — khong co khe ho.
            int claimed = repo.claim(candidate.getId(), workerId, Instant.now(), CLAIMABLE);
            if (claimed == 0) {
                continue;
            }
            repo.findById(candidate.getId()).ifPresent(this::process);
            processed++;
        }
        return processed;
    }

    private void process(InboxEvent e) {
        Optional<EventHandler> handler = handlers.stream()
                .filter(h -> h.supports(e.getEventType()))
                .findFirst();

        if (handler.isEmpty()) {
            log.warn("evt={} type={} CHUA CO HANDLER — bo qua. Se co khi hoan thanh INT tuong ung.",
                    e.getEventId(), e.getEventType());
            finish(e, HandlerResult.noop(null, "chua co handler cho type=" + e.getEventType()),
                    null, 0);
            return;
        }

        EventHandler h = handler.get();
        long t0 = System.currentTimeMillis();
        try {
            HandlerResult result = h.handle(e);
            finish(e, result, h, System.currentTimeMillis() - t0);

        } catch (ApiException ex) {
            onApiFailure(e, h, ex, System.currentTimeMillis() - t0);

        } catch (Exception ex) {
            // Loi lap trinh — thu lai cung khong khac gi. Ghi vet va dung.
            log.error("evt={} loi khong mong doi trong {}", e.getEventId(), h.name(), ex);
            toDeadLetter(e, h, "loi khong mong doi: " + ex, null,
                    System.currentTimeMillis() - t0);
        }
    }

    private void finish(InboxEvent e, HandlerResult r, EventHandler h, long durationMs) {
        e.setStatus(InboxStatus.DONE);
        e.setLastAction(r.actionName());
        e.setCompletedAt(Instant.now());
        e.setNextAttemptAt(null);
        e.setLastError(null);
        repo.save(e);

        audit.write(IntegrationLog.builder()
                .eventId(e.getEventId())
                .eventType(e.getEventType())
                .sourceSystem(e.getSourceSystem())
                .handler(h != null ? h.name() : "(none)")
                .businessKey(e.getBusinessKey())
                .action(r.actionName())
                .direction(h != null ? h.direction() : null)
                .endpoint(r.targetId())
                .attempt(e.getAttempt())
                .durationMs(durationMs)
                .status("DONE")
                .message(r.detail())
                .build());
    }

    /** Phan ung theo hop dong loi 4.4. */
    private void onApiFailure(InboxEvent e, EventHandler h, ApiException ex, long durationMs) {
        ErrorClass ec = ex.getErrorClass();

        switch (ec) {
            case RATE_LIMITED -> {
                // 429 la ap luc, KHONG phai that bai: hoan lai luot thu de khong
                // tieu ngan sach thu lai. Doc Retry-After thay vi dung backoff.
                Duration wait = ex.getRetryAfter() != null
                        ? ex.getRetryAfter()
                        : Duration.ofSeconds(backoffSeconds(e.getAttempt()));
                e.setAttempt(Math.max(0, e.getAttempt() - 1));
                scheduleRetry(e, h, ex, InboxStatus.RETRYING, wait, durationMs);
            }
            case TRANSIENT -> {
                if (e.getAttempt() >= props.getRetry().getMaxAttempts()) {
                    toDeadLetter(e, h, "can luot thu lai sau " + e.getAttempt()
                            + " lan: " + ex.getMessage(), ex, durationMs);
                } else {
                    scheduleRetry(e, h, ex, InboxStatus.RETRYING,
                            Duration.ofSeconds(backoffSeconds(e.getAttempt())), durationMs);
                }
            }
            case DEPENDENCY_MISSING -> {
                if (e.getAttempt() >= props.getRetry().getMaxAttempts()) {
                    toDeadLetter(e, h, "phu thuoc van thieu sau " + e.getAttempt()
                            + " lan: " + ex.getMessage(), ex, durationMs);
                } else {
                    scheduleRetry(e, h, ex, InboxStatus.PENDING_DEPENDENCY,
                            Duration.ofSeconds(backoffSeconds(e.getAttempt())), durationMs);
                }
            }
            // TENANT_MISMATCH: loi cau hinh — thu lai vo ich.
            // DATA_ERROR:      du lieu that su sai, vi du INT-06 gap ENROLLMENT_NOT_FOUND.
            // NOT_FOUND / CONFLICT: le ra da duoc handler xu ly; lot toi day la bat thuong.
            default -> toDeadLetter(e, h, ec + ": " + ex.getMessage(), ex, durationMs);
        }
    }

    private void scheduleRetry(InboxEvent e, EventHandler h, ApiException ex,
                               InboxStatus status, Duration wait, long durationMs) {
        e.setStatus(status);
        e.setNextAttemptAt(Instant.now().plus(wait));
        e.setLastError(ex.getMessage());
        repo.save(e);

        audit.write(IntegrationLog.builder()
                .eventId(e.getEventId())
                .eventType(e.getEventType())
                .sourceSystem(e.getSourceSystem())
                .handler(h != null ? h.name() : null)
                .businessKey(e.getBusinessKey())
                .direction(h != null ? h.direction() : null)
                .endpoint(ex.getEndpoint())
                .httpStatus(ex.getStatus())
                .attempt(e.getAttempt())
                .durationMs(durationMs)
                .status(status.name())
                .message("cho " + wait.toSeconds() + "s roi thu lai — " + ex.getMessage())
                .build());
    }

    private void toDeadLetter(InboxEvent e, EventHandler h, String reason,
                              ApiException ex, long durationMs) {
        e.setStatus(InboxStatus.DEAD_LETTER);
        e.setNextAttemptAt(null);
        e.setLastError(reason);
        e.setCompletedAt(Instant.now());
        repo.save(e);
        deadLetters.save(new DeadLetter(e.getId(), reason));

        audit.write(IntegrationLog.builder()
                .eventId(e.getEventId())
                .eventType(e.getEventType())
                .sourceSystem(e.getSourceSystem())
                .handler(h != null ? h.name() : null)
                .businessKey(e.getBusinessKey())
                .direction(h != null ? h.direction() : null)
                .endpoint(ex != null ? ex.getEndpoint() : null)
                .httpStatus(ex != null ? ex.getStatus() : null)
                .attempt(e.getAttempt())
                .durationMs(durationMs)
                .status("DEAD_LETTER")
                .message(reason)
                .build());
    }

    /** Day 1, 2, 4, 8, 16, 32 giay — muc 5.5. */
    private int backoffSeconds(int attempt) {
        List<Integer> ladder = props.getRetry().getBackoffSeconds();
        int idx = Math.max(0, Math.min(attempt - 1, ladder.size() - 1));
        return ladder.get(idx);
    }

    public String getWorkerId() { return workerId; }
}
