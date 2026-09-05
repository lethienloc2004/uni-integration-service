package vn.thanhdo.integration.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.ApiException;
import vn.thanhdo.integration.client.ErrorClass;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.client.dto.LmsLearnerAtRisk;
import vn.thanhdo.integration.inbox.InboxEvent;

/**
 * INT-08 — chuyen tin hieu rui ro hoc tap cua UniLearn thanh Advising Alert cua UniSIS.
 *
 * <p>Luong nguoc thu hai: LMS → SIS.
 *
 * <p>Bang anh xa — muc 18.2:
 * <pre>
 *   userExternalRef                   -> studentId
 *   courseExternalCode                -> sectionId
 *   riskType                          -> riskType   (thuong la AT_RISK)
 *   completionPercent + inactiveDays  -> details
 * </pre>
 *
 * <p><b>Bai toan rieng cua yeu cau nay:</b> AdvisingAlert khong co khoa tu nhien, va
 * {@code POST /api/v1/advising/alerts} khong tu chong trung — muc 18.4 noi ro Integration
 * Service phai tu lo. Rang buoc UNIQUE tren inbox da chan duoc truong hop cung mot eventId
 * giao nhieu lan, nhung chua chan duoc truong hop mot sinh vien bi danh gia lai nhieu lan
 * va moi lan sinh mot eventId khac nhau. Vi vay truoc khi ghi, bo xu ly hoi UniSIS xem da
 * co canh bao nao dang mo cho cung (studentId, sectionId, riskType) chua.
 */
@Component
public class RaiseAdvisingAlertHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(RaiseAdvisingAlertHandler.class);

    private static final String STATUS_OPEN = "OPEN";

    private final SisApi sis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RaiseAdvisingAlertHandler(SisApi sis) {
        this.sis = sis;
    }

    @Override
    public String name() { return "raiseAdvisingAlert"; }

    @Override
    public String direction() { return "LMS_TO_SIS"; }

    @Override
    public boolean supports(String eventType) {
        return "learner.at_risk".equals(eventType);
    }

    @Override
    public HandlerResult handle(InboxEvent event) {
        LmsLearnerAtRisk risk = readPayload(event);

        if (risk == null || !risk.hasRequiredFields()) {
            // Muc 18.5: external reference khong hop le — loi du lieu, khong thu lai vo han
            throw new ApiException("LMS", 422, "(payload)",
                    "su kien learner.at_risk thieu userExternalRef hoac courseExternalCode",
                    ErrorClass.DATA_ERROR, null);
        }

        if (!risk.isAtRisk()) {
            // UniLearn khong phat su kien cho tin hieu NORMAL nen nhanh nay le ra khong xay ra.
            // Giu lai nhu mot lop phong thu: tieu chi nghiem thu thu hai cua muc 18.6 yeu cau
            // NORMAL tuyet doi khong tao canh bao.
            return HandlerResult.noop(null,
                    "riskType=" + risk.riskType() + " khong phai AT_RISK, khong tao canh bao");
        }

        String studentId = risk.userExternalRef();
        String sectionId = risk.courseExternalCode();

        // --- Chong trung o TANG NGHIEP VU -----------------------------------------
        if (hasOpenAlert(studentId, sectionId, risk.riskType())) {
            return HandlerResult.noop(studentId + "/" + sectionId,
                    "da co canh bao " + risk.riskType() + " dang mo cho " + studentId
                            + " tai " + sectionId + ", khong tao them");
        }

        try {
            sis.createAlert(studentId, sectionId, LmsLearnerAtRisk.AT_RISK, risk.toDetails());

        } catch (ApiException e) {
            if (e.getStatus() == 422) {
                // Muc 18.5 — 422 STUDENT_NOT_FOUND: du lieu nguon khong hop le voi UniSIS.
                // Thu lai vo ich vi sinh vien se khong tu xuat hien.
                log.warn("UniSIS tu choi tao canh bao cho {} tai {} — nhieu kha nang studentId "
                                + "khong ton tai. Chuyen dead-letter, KHONG thu lai.",
                        studentId, sectionId);
                throw e.reclassify(ErrorClass.DATA_ERROR);
            }
            throw e;
        }

        return HandlerResult.created(studentId + "/" + sectionId,
                "tao canh bao AT_RISK cho " + studentId + " tai " + sectionId
                        + " (" + risk.toDetails() + ")");
    }

    /**
     * Hoi chinh UniSIS xem da co canh bao dang mo chua.
     *
     * <p>Nguon su that la he thong dich chu khong phai mot bang cuc bo — nho vay canh bao do
     * nguoi khac tao, hoac canh bao con sot lai tu lan chay truoc khi co so du lieu tich hop
     * bi xoa, deu duoc tinh den.
     */
    private boolean hasOpenAlert(String studentId, String sectionId, String riskType) {
        String wanted = riskType == null ? LmsLearnerAtRisk.AT_RISK : riskType;
        for (JsonNode a : sis.listAlerts(studentId)) {
            boolean sameSection = sectionId.equals(text(a, "sectionId"));
            boolean sameRisk = wanted.equalsIgnoreCase(String.valueOf(text(a, "riskType")));
            boolean stillOpen = STATUS_OPEN.equalsIgnoreCase(String.valueOf(text(a, "status")));
            if (sameSection && sameRisk && stillOpen) {
                return true;
            }
        }
        return false;
    }

    private LmsLearnerAtRisk readPayload(InboxEvent event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(event.getPayload());
            JsonNode data = root.has("data") ? root.get("data") : root;
            return LmsLearnerAtRisk.from(data);
        } catch (Exception e) {
            log.warn("evt={} khong doc duoc payload: {}", event.getEventId(), e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
