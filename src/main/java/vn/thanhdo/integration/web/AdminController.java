package vn.thanhdo.integration.web;

import org.springframework.web.bind.annotation.*;
import vn.thanhdo.integration.audit.AuditService;
import vn.thanhdo.integration.inbox.*;
import vn.thanhdo.integration.reconcile.ReconcileReport;
import vn.thanhdo.integration.reconcile.Reconciler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint quan tri — vua phuc vu van hanh vua la CONG CU TRINH DIEN (muc 10.2).
 *
 * <p>Trang liet ke inbox chinh la TANG 3 cua quy trinh go loi bon tang. Khi giang vien
 * bam tao mot sinh vien moi, chi can lam moi trang nay va chieu dong tuong ung —
 * ma su kien, trang thai, anh xa nguon sang dich, so lan thu lai. Thuyet phuc hon
 * nhieu so voi cuon nhat ky trong cua so dong lenh.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final InboxRepository inbox;
    private final DeadLetterRepository deadLetters;
    private final AuditService audit;
    private final Worker worker;
    private final Reconciler reconciler;

    public AdminController(InboxRepository inbox, DeadLetterRepository deadLetters,
                           AuditService audit, Worker worker, Reconciler reconciler) {
        this.inbox = inbox;
        this.deadLetters = deadLetters;
        this.audit = audit;
        this.worker = worker;
        this.reconciler = reconciler;
    }

    /** Danh sach inbox, loc theo trang thai neu can. */
    @GetMapping("/inbox")
    public List<Map<String, Object>> inbox(@RequestParam(required = false) InboxStatus status) {
        List<InboxEvent> rows = (status == null)
                ? inbox.findTop200ByOrderByIdDesc()
                : inbox.findTop200ByStatusOrderByIdDesc(status);
        return rows.stream().map(AdminController::toRow).toList();
    }

    /** Dem theo tung trang thai — cau tra loi nhanh cho "he thong dang the nao". */
    @GetMapping("/inbox/summary")
    public Map<String, Long> summary() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (InboxStatus s : InboxStatus.values()) {
            out.put(s.name(), inbox.countByStatus(s));
        }
        return out;
    }

    /** Toan bo vong doi cua mot su kien — tim theo MA TUONG QUAN. */
    @GetMapping("/events/{eventId}")
    public Map<String, Object> trace(@PathVariable String eventId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", eventId);
        out.put("log", audit.findByEventId(eventId).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", l.getLoggedAt());
            m.put("handler", l.getHandler());
            m.put("action", l.getAction());
            m.put("endpoint", l.getEndpoint());
            m.put("http", l.getHttpStatus());
            m.put("attempt", l.getAttempt());
            m.put("status", l.getStatus());
            m.put("message", l.getMessage());
            return m;
        }).toList());
        return out;
    }

    @GetMapping("/dead-letter")
    public List<Map<String, Object>> deadLetter() {
        return deadLetters.findTop200ByOrderByIdDesc().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("inboxEventId", d.getInboxEventId());
            m.put("reason", d.getReason());
            m.put("createdAt", d.getCreatedAt());
            return m;
        }).toList();
    }

    /** Dua mot su kien dead-letter tro lai hang doi sau khi da xu ly nguyen nhan. */
    @PostMapping("/inbox/{id}/requeue")
    public Map<String, Object> requeue(@PathVariable Long id) {
        return inbox.findById(id).map(e -> {
            e.setStatus(InboxStatus.RECEIVED);
            e.setAttempt(0);
            e.setNextAttemptAt(null);
            e.setLastError(null);
            inbox.save(e);
            return Map.<String, Object>of("requeued", true, "id", id, "eventId", e.getEventId());
        }).orElse(Map.of("requeued", false, "reason", "khong tim thay id " + id));
    }

    /**
     * Chay doi soat — NFR-06 va kich ban cham T11.
     *
     * <p>Cach trinh dien thuyet phuc nhat: bam {@code ?dryRun=true} de cho thay N cho lech,
     * bam lai khong co tham so de sua that, roi bam dryRun mot lan nua thay con 0.
     */
    @PostMapping("/reconcile")
    public ReconcileReport reconcile(@RequestParam(defaultValue = "false") boolean dryRun) {
        return reconciler.reconcile(dryRun);
    }

    /** Bao cao cua luot doi soat gan nhat, khong chay lai. */
    @GetMapping("/reconcile/last")
    public ReconcileReport lastReconcile() {
        return reconciler.getLastReport();
    }

    /** Chay ngay mot luot xu ly — tien khi trinh dien, khoi cho lich. */
    @PostMapping("/drain")
    public Map<String, Object> drain() {
        int n = worker.drainOnce();
        return Map.of("processed", n, "workerId", worker.getWorkerId());
    }

    private static Map<String, Object> toRow(InboxEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("source", e.getSourceSystem());
        m.put("eventId", e.getEventId());
        m.put("eventType", e.getEventType());
        m.put("businessKey", e.getBusinessKey());
        m.put("status", e.getStatus());
        m.put("action", e.getLastAction());
        m.put("attempt", e.getAttempt());
        m.put("retryCount", e.getRetryCount());
        m.put("receivedAt", e.getReceivedAt());
        m.put("completedAt", e.getCompletedAt());
        m.put("nextAttemptAt", e.getNextAttemptAt());
        m.put("lastError", e.getLastError());
        return m;
    }
}
