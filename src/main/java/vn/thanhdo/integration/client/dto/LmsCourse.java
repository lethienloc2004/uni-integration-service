package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Course truc tuyen ben UniLearn LMS.
 *
 * <p>{@code id} la ID SO NOI BO cua LMS, khong bao gio duoc dung lam ma nghiep vu
 * (bat bien BV-4). Khoa noi that giua hai he thong la {@code externalCode} = sectionId.
 */
public record LmsCourse(
        String id,
        String externalCode,
        String title,
        String term,
        String state,
        String teacherExternalRef) {

    public static LmsCourse from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new LmsCourse(
                text(n, "id"),
                text(n, "externalCode"),
                text(n, "title"),
                text(n, "term"),
                text(n, "state"),
                text(n, "teacherExternalRef"));
    }

    /**
     * Ba truong do UniSIS lam chu va CO THE sua bang PATCH.
     *
     * <p>Rang buoc cua he thong dich: {@code CoursePatch} ben LMS chi nhan
     * {@code title}, {@code state}, {@code teacherExternalRef} — <b>khong co term</b>.
     * Nghia la hoc ky chi dat duoc mot lan luc tao. Neu semesterCode ben SIS doi ve sau,
     * Integration Service khong the hoi tu truong do; day la gioi han cua API dich chu
     * khong phai thieu sot thiet ke, va duoc ghi lai o day de tra loi khi bao ve.
     *
     * <p>{@code externalCode} la khoa noi — ghi mot lan, khong bao gio sua.
     */
    public boolean differsFrom(String desiredTitle, String desiredState, String desiredTeacher) {
        return !Objects.equals(nullToEmpty(title), nullToEmpty(desiredTitle))
                || !Objects.equals(nullToEmpty(state), nullToEmpty(desiredState))
                || !Objects.equals(nullToEmpty(teacherExternalRef), nullToEmpty(desiredTeacher));
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
