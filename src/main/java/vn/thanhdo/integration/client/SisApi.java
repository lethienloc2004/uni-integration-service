package vn.thanhdo.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.dto.SisEnrollment;
import vn.thanhdo.integration.client.dto.SisSection;
import vn.thanhdo.integration.client.dto.SisStudent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cac loi goi nghiep vu toi UniSIS (Phu luc A cua de bai). */
@Component
public class SisApi {

    private final ApiClient client;

    /*
     * @Qualifier TUONG MINH thay vi dua vao ten tham so.
     *
     * Co hai bean ApiClient — sisClient va lmsClient. Neu chi dua vao ten tham so, viec phan
     * giai phu thuoc vao co -parameters luc bien dich va vao cach tung IDE/cong cu dong goi
     * giu ten tham so. Chi rõ ten bean thi khong con phu thuoc vao dieu do, va noi nham client
     * o day se lam toan bo du lieu di sai he thong — loai loi rat kho nhin ra.
     */
    public SisApi(@Qualifier("sisClient") ApiClient sisClient) {
        this.client = sisClient;
    }

    public ApiClient raw() {
        return client;
    }

    /** GET /api/v1/students/{studentId} — lay theo MA NGHIEP VU, khong phai id noi bo. */
    public Optional<SisStudent> getStudent(String studentId) {
        try {
            JsonNode node = client.get("/api/v1/students/{studentId}", studentId);
            return Optional.ofNullable(SisStudent.from(node));
        } catch (ApiException e) {
            if (e.isStatus(404)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** GET /api/v1/students — tai TOAN BO mot lan, phuc vu doi soat theo tap hop (muc 6.4). */
    public List<SisStudent> listStudents() {
        return client.getList("/api/v1/students").stream()
                .map(SisStudent::from)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * GET /api/v1/sections — tai TOAN BO danh sach lop hoc phan.
     *
     * <p>UniSIS khong co endpoint lay mot Section theo ma, nen day cung la duong duy nhat
     * de doc lai mot lop. Dung cho doi soat Section va cho truong hop payload su kien
     * khong du truong.
     */
    public List<SisSection> listSections() {
        return client.getList("/api/v1/sections").stream()
                .map(SisSection::from)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Loc mot Section tu danh sach. Ton mot request cho ca danh sach — dung tiet kiem. */
    public Optional<SisSection> getSection(String sectionId) {
        return listSections().stream()
                .filter(s -> sectionId.equals(s.sectionId()))
                .findFirst();
    }

    /**
     * GET /api/v1/enrollments?studentId=..&sectionId=.. — doc trang thai dang ky HIEN HANH.
     *
     * <p>Day la cach xac dinh trang thai MONG MUON cua membership khi khong the tin payload
     * su kien: neu su kien da nam trong hang cho mot luc, hoac dang o lan thu lai thu hai,
     * thi {@code enrollment.created} cu co the da bi mot lan huy moi hon lam lac hau.
     */
    public Optional<SisEnrollment> findEnrollment(String studentId, String sectionId) {
        List<JsonNode> rows = client.getList(
                "/api/v1/enrollments?studentId={sv}&sectionId={sec}", studentId, sectionId);
        return rows.stream().map(SisEnrollment::from)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /** GET /api/v1/enrollments — tai toan bo, phuc vu doi soat Enrollment. */
    public List<SisEnrollment> listEnrollments() {
        return client.getList("/api/v1/enrollments").stream()
                .map(SisEnrollment::from)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * GET /api/v1/courses — danh muc hoc phan, dung de lay courseName ghep vao title
     * cua LMS Course. Endpoint nay khong co bo loc nen luon tra ve toan bo danh muc;
     * vi vay ket qua duoc dem lai o {@link CourseCatalog}.
     */
    public Map<String, String> courseCatalog() {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode n : client.getList("/api/v1/courses")) {
            if (n.hasNonNull("courseCode")) {
                out.put(n.get("courseCode").asText(),
                        n.hasNonNull("courseName") ? n.get("courseName").asText() : null);
            }
        }
        return out;
    }

    /**
     * PUT /api/v1/grades/{studentId}/{sectionId} — ghi diem chinh thuc, INT-06.
     *
     * <p>Duong dan dung MA NGHIEP VU cua UniSIS, khong phai ID noi bo cua LMS.
     *
     * <p><b>CO Y khong gui letterGrade.</b> Tieu chi nghiem thu thu tu cua muc 16.6 yeu cau
     * UniSIS tu tinh chu diem. Tu tinh o phia Integration Service la lan sang tham quyen cua
     * nguon su that va tao nguy co lech quy tac quy doi.
     *
     * <p>Endpoint nay la UPSERT: goi lai voi cung gia tri khong tao ban ghi thu hai.
     *
     * @throws ApiException 422 ENROLLMENT_NOT_FOUND neu cap Student/Section chua co dang ky hoc
     */
    public void putGrade(String studentId, String sectionId, BigDecimal scoreOutOfTen) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("finalScore", scoreOutOfTen);
        body.put("source", "LMS");
        client.put("/api/v1/grades/{sv}/{sec}", body, studentId, sectionId);
    }

    /** GET /api/v1/grades — toan bo diem chinh thuc cua tenant, phuc vu doi soat. */
    public List<JsonNode> listGrades() {
        return client.getList("/api/v1/grades");
    }

    /**
     * GET /api/v1/advising/alerts?studentId=.. — INT-08.
     *
     * <p>Dung de chong tao canh bao trung. AdvisingAlert khong co khoa tu nhien va endpoint
     * tao cung khong tu chong trung (muc 18.4), nen phai tu kiem tra truoc khi ghi. Nguon
     * su that la chinh UniSIS chu khong phai mot bang cuc bo — dung tinh than bat bien BV-4.
     */
    public List<JsonNode> listAlerts(String studentId) {
        return client.getList("/api/v1/advising/alerts?studentId={sv}", studentId);
    }

    /**
     * POST /api/v1/advising/alerts — tao canh bao co van, INT-08.
     *
     * @throws ApiException 422 STUDENT_NOT_FOUND neu ma sinh vien khong ton tai o UniSIS
     */
    public void createAlert(String studentId, String sectionId, String riskType, String details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        body.put("sectionId", sectionId);
        body.put("riskType", riskType);
        body.put("details", details);
        client.post("/api/v1/advising/alerts", body);
    }

    /**
     * GET /api/v1/events?since=... — XUONG SONG bao dam khong mat su kien (muc 6.5).
     * {@code /events} la bang du lieu ben vung chu khong phai hang doi tieu thu mot lan,
     * nen bo doc co diem moc lay duoc ca nhung su kien webhook da lam rot.
     */
    public List<JsonNode> getEvents(Instant since) {
        return since == null
                ? client.getList("/api/v1/events")
                : client.getList("/api/v1/events?since={since}", since.toString());
    }
}
