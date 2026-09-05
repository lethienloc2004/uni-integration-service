package vn.thanhdo.integration.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.thanhdo.integration.inbox.InboxService;
import vn.thanhdo.integration.util.Timestamps;

import java.time.Instant;
import java.util.Map;

/**
 * Bo nhan webhook.
 *
 * <p>CHI ghi su kien xuong inbox roi tra 200 ngay — TUYET DOI khong goi API he thong
 * dich tai day (muc 5.3).
 *
 * <p>Ly do khong phai la cho dep ma la dieu kien dung dan cua he thong: webhook cua
 * lab duoc gui trong tien trinh nen voi thoi gian cho khoang 4 giay va he thong nguon
 * KHONG THU LAI lan nao. Mot lan xu ly day du gom tra cuu, tao hoac cap nhat, co khi
 * gap 503 phai cho — rat de vuot qua 4 giay. Neu bo nhan xu ly truc tiep thi nguon se
 * het thoi gian cho va su kien mat vinh vien.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final InboxService inbox;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebhookController(InboxService inbox) {
        this.inbox = inbox;
    }

    @PostMapping("/sis")
    public ResponseEntity<Map<String, Object>> fromSis(@RequestBody String rawBody) {
        return receive("SIS", rawBody);
    }

    @PostMapping("/lms")
    public ResponseEntity<Map<String, Object>> fromLms(@RequestBody String rawBody) {
        return receive("LMS", rawBody);
    }

    private ResponseEntity<Map<String, Object>> receive(String sourceSystem, String rawBody) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            String eventId = text(root, "eventId");
            String eventType = text(root, "eventType");
            Instant occurredAt = Timestamps.parse(text(root, "occurredAt"));

            if (eventId == null || eventType == null) {
                // Tra 200 chu khong 400: nguon khong thu lai, va mot ban ghi hong
                // khong duoc lam nghen kenh. Ghi log de con nguoi xem lai.
                log.warn("[{}] webhook thieu eventId hoac eventType, bo qua", sourceSystem);
                return ResponseEntity.ok(Map.of("accepted", false, "reason", "thieu eventId/eventType"));
            }

            InboxService.Result r = inbox.record(InboxService.Channel.WEBHOOK,
                    sourceSystem, eventId, eventType, rawBody, occurredAt);
            return ResponseEntity.ok(Map.of(
                    "accepted", true,
                    "eventId", eventId,
                    "duplicate", r == InboxService.Result.DUPLICATE));

        } catch (Exception e) {
            log.error("[{}] khong doc duoc payload webhook", sourceSystem, e);
            return ResponseEntity.ok(Map.of("accepted", false, "reason", "payload khong hop le"));
        }
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }

}
