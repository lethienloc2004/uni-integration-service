package vn.thanhdo.integration.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.ApiException;
import vn.thanhdo.integration.client.ErrorClass;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.client.dto.LmsGradePublished;
import vn.thanhdo.integration.inbox.InboxEvent;

import java.math.BigDecimal;

/**
 * INT-06 — dong bo diem da cong bo tu UniLearn ve diem chinh thuc cua UniSIS.
 *
 * <p>Day la LUONG NGUOC dau tien: LMS → SIS. Khac ba ham hoi tu truoc, o day khong co trang
 * thai de hoi tu ma la ghi nhan mot su kien da xay ra. Vi vay no giu dang bo xu ly rieng,
 * dung nhu thiet ke o muc 3.2 cua De xuat giai phap v2.0.
 *
 * <p>Bang anh xa — muc 16.2:
 * <pre>
 *   userExternalRef     -> studentId              (BAT BUOC dung external reference)
 *   courseExternalCode  -> sectionId              (BAT BUOC dung external code)
 *   finalGrade 0..100   -> finalScore 0..10       round(finalGrade / 10, 2)
 *   (hang)              -> source = "LMS"
 *   khong gui           -> letterGrade            de UniSIS tu tinh
 * </pre>
 *
 * <p><b>Chi xu ly {@code grade.published}.</b> Diem con o trang thai nhap nhap (DRAFT) khong
 * phat su kien nao, nen khong bao gio den duoc day — dung yeu cau "diem chi dong bo sau thao
 * tac publish" cua tieu chi nghiem thu thu hai muc 16.6.
 */
@Component
public class SyncGradeHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(SyncGradeHandler.class);

    private final SisApi sis;
    private final ObjectMapper mapper = new ObjectMapper();

    public SyncGradeHandler(SisApi sis) {
        this.sis = sis;
    }

    @Override
    public String name() { return "syncGrade"; }

    @Override
    public String direction() { return "LMS_TO_SIS"; }

    @Override
    public boolean supports(String eventType) {
        return "grade.published".equals(eventType);
    }

    @Override
    public HandlerResult handle(InboxEvent event) {
        LmsGradePublished grade = readPayload(event);

        if (grade == null || !grade.hasRequiredFields()) {
            throw new ApiException("LMS", 422, "(payload)",
                    "su kien grade.published thieu userExternalRef, courseExternalCode hoac finalGrade",
                    ErrorClass.DATA_ERROR, null);
        }

        if (!grade.isGradeInRange()) {
            // Kiem tra TRUOC khi goi API — khoi ton mot luot trong ngan sach tan suat
            // de nhan ve mot loi validation da doan truoc duoc.
            throw new ApiException("LMS", 422, "(payload)",
                    "finalGrade=" + grade.finalGrade() + " nam ngoai thang 0..100",
                    ErrorClass.DATA_ERROR, null);
        }

        String studentId = grade.userExternalRef();
        String sectionId = grade.courseExternalCode();
        BigDecimal score10 = grade.toScoreOutOfTen();

        log.debug("key={}|{} quy doi {}/100 -> {}/10",
                studentId, sectionId, grade.finalGrade(), score10);

        try {
            sis.putGrade(studentId, sectionId, score10);

        } catch (ApiException e) {
            throw reclassifyGradeError(e, studentId, sectionId);
        }

        // Endpoint dich la upsert: goi lai cung gia tri khong tao ban ghi thu hai.
        // Muc 16.5 van yeu cau ghi ro tinh idempotent trong nhat ky.
        return HandlerResult.updated(studentId + "/" + sectionId,
                "ghi diem chinh thuc " + grade.finalGrade() + "/100 -> " + score10
                        + "/10 cho " + studentId + " tai " + sectionId
                        + " (source=LMS, upsert, letterGrade de UniSIS tu tinh)");
    }

    /**
     * Muc 16.5 — {@code 422 ENROLLMENT_NOT_FOUND} la RANH GIOI TRACH NHIEM, khong phai
     * loi tam thoi.
     *
     * <p>UniSIS tu choi ghi diem cho cap Student/Section chua co dang ky hoc. Integration
     * Service <b>khong duoc phep</b> tu tao enrollment de "chua" tinh huong nay: dang ky hoc
     * thuoc tham quyen cua UniSIS va cua Phong Dao tao. Thu lai cung vo ich vi du lieu se
     * khong tu xuat hien.
     *
     * <p>Hanh dong dung: chuyen thang dead-letter kem log neu ro nguyen nhan de con nguoi
     * quyet dinh — dung yeu cau NFR-03 "khong thu lai vo han loi du lieu".
     */
    private ApiException reclassifyGradeError(ApiException e, String studentId, String sectionId) {
        if (e.getStatus() == 422) {
            log.warn("evt grade.published: UniSIS tu choi ghi diem cho {} tai {} — "
                            + "nhieu kha nang chua co dang ky hoc. Chuyen dead-letter, KHONG thu lai. "
                            + "Integration Service khong duoc tu tao enrollment.",
                    studentId, sectionId);
            return e.reclassify(ErrorClass.DATA_ERROR);
        }
        return e;
    }

    private LmsGradePublished readPayload(InboxEvent event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(event.getPayload());
            JsonNode data = root.has("data") ? root.get("data") : root;
            return LmsGradePublished.from(data);
        } catch (Exception e) {
            log.warn("evt={} khong doc duoc payload: {}", event.getEventId(), e.getMessage());
            return null;
        }
    }
}
