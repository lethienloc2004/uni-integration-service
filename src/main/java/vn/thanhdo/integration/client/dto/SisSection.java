package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lop hoc phan ben UniSIS — NGUON SU THAT cho lop, hoc ky va giang vien phu trach.
 *
 * <p>Luu y ve API nguon: UniSIS <b>khong co</b> {@code GET /api/v1/sections/{id}} —
 * chi co endpoint liet ke toan bo. Vi vay muon lay mot Section phai tai ca danh sach
 * roi loc, khac han truong hop Student. Day la ly do payload su kien duoc uu tien
 * dung lam desired bat cu khi nao du truong (muc 3.3).
 */
public record SisSection(
        String sectionId,
        String courseCode,
        String semesterCode,
        String lecturerId,
        Integer capacity,
        String status) {

    public static SisSection from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new SisSection(
                text(n, "sectionId"),
                text(n, "courseCode"),
                text(n, "semesterCode"),
                text(n, "lecturerId"),
                n.hasNonNull("capacity") ? n.get("capacity").asInt() : null,
                text(n, "status"));
    }

    /**
     * Du truong de dung lam desired ma khong can tai lai danh sach Section.
     * {@code capacity} khong nam trong bang anh xa nen khong bat buoc.
     */
    public boolean isCompleteEnoughForSync() {
        return notBlank(sectionId) && notBlank(courseCode)
                && notBlank(semesterCode) && notBlank(lecturerId);
    }

    /**
     * Tieu de Course ben LMS. De bai (muc 12.2) yeu cau title PHAI chua courseCode,
     * khuyen nghi dang "COURSECODE - Course Name".
     *
     * @param courseName lay tu danh muc hoc phan; neu tra cuu that bai thi truyen
     *                   {@code null} va title rut gon con moi courseCode — muc 12.5
     *                   cho phep dung title toi thieu chua courseCode.
     */
    public String lmsTitle(String courseName) {
        return (courseName == null || courseName.isBlank())
                ? courseCode
                : courseCode + " - " + courseName;
    }

    /**
     * Anh xa trang thai lop sang trang thai Course ben LMS.
     * Muc 17.2 de day la tuy chon nhung bat buoc phai mo ta trong thiet ke:
     * OPEN -> PUBLISHED, CLOSED -> ARCHIVED.
     */
    public String lmsState() {
        return "CLOSED".equalsIgnoreCase(nullToEmpty(status).trim()) ? "ARCHIVED" : "PUBLISHED";
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
