package vn.thanhdo.integration.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Ghi su kien vao inbox — hien thuc mau IDEMPOTENT RECEIVER (muc 6.1).
 *
 * <p>Ben gui khong bao dam moi thong diep chi den mot lan, va con co y gui lap
 * mot ty le su kien de kiem thu. Vi vay ben nhan phai tu nhan dien va loai bo
 * thong diep da xu ly. Chong trung dat o TANG CO SO DU LIEU bang rang buoc
 * UNIQUE(source_system, event_id) — dung cả khi chay nhieu tien trinh, manh hon
 * han viec kiem tra trong bo nho von hong ngay khi khoi dong lai.
 *
 * <p>Lop nay CO Y khong danh dau {@code @Transactional}: loi chen trung phai duoc
 * bat o ngoai giao dich cua repository, neu khong giao dich se bi danh dau
 * rollback-only va lan chen hop le tiep theo cung hong lay.
 */
@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private final InboxRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public InboxService(InboxRepository repo) {
        this.repo = repo;
    }

    public enum Result { INSERTED, DUPLICATE }

    /**
     * Kenh ma su kien di vao inbox. Chi anh huong toi MUC LOG cua truong hop trung,
     * khong anh huong nghiep vu.
     */
    public enum Channel {
        /**
         * Trung o day la tinh huong DANG CHU Y: he thong nguon giao at-least-once va
         * co y gui lap mot ty le su kien. Day chinh la bang chung cho kich ban T09.
         */
        WEBHOOK,

        /**
         * Trung o day la BINH THUONG va xay ra lien tuc: khoang chong lan 5 giay
         * (muc 6.3) co y doc lai vai giay cuoi de khong bo sot su kien trung moc
         * thoi gian. Ghi INFO se lam ngap nhat ky moi 10 giay.
         */
        POLLER,

        /** Trung o day la bat thuong — runId khien moi luot doi soat sinh ma moi. */
        RECON
    }

    /**
     * Ghi mot su kien. Tra ve DUPLICATE neu (sourceSystem, eventId) da ton tai.
     *
     * @param channel      kenh vao, chi dung de chon muc log
     * @param sourceSystem SIS | LMS | RECON
     * @param eventId      xem quy tac sinh ma o muc 5.4
     */
    public Result record(Channel channel, String sourceSystem, String eventId, String eventType,
                         String payloadJson, Instant occurredAt) {

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId khong duoc rong");
        }

        // Duong nhanh: da co roi thi khoi cham vao rang buoc
        Optional<InboxEvent> existing = repo.findBySourceSystemAndEventId(sourceSystem, eventId);
        if (existing.isPresent()) {
            if (channel == Channel.POLLER) {
                log.debug("evt={} type={} src={} action=DUPLICATE_IGNORED (khoang chong lan)",
                        eventId, eventType, sourceSystem);
            } else {
                log.info("evt={} type={} src={} action=DUPLICATE_IGNORED status={}",
                        eventId, eventType, sourceSystem, existing.get().getStatus());
            }
            return Result.DUPLICATE;
        }

        String businessKey = extractBusinessKey(eventType, payloadJson);
        InboxEvent e = new InboxEvent(sourceSystem, eventId, eventType, businessKey,
                payloadJson, occurredAt);

        try {
            repo.saveAndFlush(e);
            log.info("evt={} type={} src={} key={} action=RECEIVED",
                    eventId, eventType, sourceSystem, businessKey);
            return Result.INSERTED;
        } catch (DataIntegrityViolationException dup) {
            // Hai tien trinh cung chen mot luc — rang buoc UNIQUE lam dung viec cua no
            log.info("evt={} type={} src={} action=DUPLICATE_IGNORED (race)",
                    eventId, eventType, sourceSystem);
            return Result.DUPLICATE;
        }
    }

    /**
     * Khoa nghiep vu dung de PHAN LUONG theo thuc the (muc 5.6) va de tra cuu
     * khi go loi. Moi su kien cua cung mot thuc the luon roi vao cung mot tien trinh,
     * nho vay vua loai bo tranh chap vua giu dung thu tu.
     */
    private String extractBusinessKey(String eventType, String payloadJson) {
        if (payloadJson == null || eventType == null) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(payloadJson);
            JsonNode data = root.has("data") ? root.get("data") : root;

            return switch (eventType) {
                case "student.created", "student.updated" -> text(data, "studentId");
                case "section.created", "section.updated" -> text(data, "sectionId");
                case "enrollment.created", "enrollment.dropped" ->
                        join(text(data, "studentId"), text(data, "sectionId"));
                case "grade.published", "learner.at_risk" ->
                        join(text(data, "userExternalRef"), text(data, "courseExternalCode"));
                default -> null;
            };
        } catch (Exception ex) {
            log.warn("khong doc duoc businessKey tu payload cua type={}: {}", eventType, ex.getMessage());
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static String join(String a, String b) {
        return (a == null && b == null) ? null : a + "|" + b;
    }
}
