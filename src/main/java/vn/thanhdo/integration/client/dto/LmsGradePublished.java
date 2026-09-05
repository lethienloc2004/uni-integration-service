package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Du lieu su kien {@code grade.published} do UniLearn phat ra — INT-06.
 *
 * <p>Day la luong NGUOC dau tien: LMS → SIS. Khac hai ham hoi tu truoc, o day khong co
 * trang thai de hoi tu ma la GHI NHAN MOT SU KIEN da xay ra (viec cong bo diem).
 *
 * <p><b>Diem then chot:</b> chi duoc dung {@code userExternalRef} va {@code courseExternalCode}
 * de xac dinh Student/Section ben UniSIS. Hai truong {@code userId} va {@code courseId} la
 * ID SO NOI BO cua LMS, khong co y nghia gi ben SIS — dung nham la vi pham bat bien BV-4 va
 * tieu chi nghiem thu thu ba cua muc 16.6.
 */
public record LmsGradePublished(
        String gradeId,
        String userExternalRef,
        String courseExternalCode,
        Double finalGrade) {

    public static LmsGradePublished from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new LmsGradePublished(
                text(n, "gradeId"),
                text(n, "userExternalRef"),
                text(n, "courseExternalCode"),
                n.hasNonNull("finalGrade") ? n.get("finalGrade").asDouble() : null);
    }

    public boolean hasRequiredFields() {
        return notBlank(userExternalRef) && notBlank(courseExternalCode) && finalGrade != null;
    }

    /** Thang diem cua UniLearn la 0..100 (rang buoc {@code Field(ge=0, le=100)} ben LMS). */
    public boolean isGradeInRange() {
        return finalGrade != null && finalGrade >= 0.0 && finalGrade <= 100.0;
    }

    /**
     * Quy doi thang 100 sang thang 10 — bang anh xa muc 16.2:
     * {@code finalScore = round(finalGrade / 10, 2)}.
     *
     * <p>Lam tron HALF_UP: 87.5 → 8.75, 87.55 → 8.76. Dung {@link BigDecimal} chu khong
     * dung so thuc, vi lam tron so thuc nhi phan cho ket qua khong on dinh o chu so thu hai —
     * ma day la DIEM SO CHINH THUC cua sinh vien.
     */
    public BigDecimal toScoreOutOfTen() {
        return BigDecimal.valueOf(finalGrade)
                .divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
