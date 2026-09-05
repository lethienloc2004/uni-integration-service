package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dang ky hoc ben UniSIS — NGUON SU THAT quyet dinh sinh vien co thuoc lop hay khong.
 *
 * <p>Luu y ve vong doi: UniSIS <b>khong xoa</b> ban ghi khi huy dang ky, ma doi
 * {@code status} tu {@code ENROLLED} sang {@code DROPPED}. Dang ky lai thi ban ghi cu
 * duoc kich hoat lai chu khong tao moi. Nho vay doc lai trang thai hien hanh luon cho
 * cau tra loi dung, ke ca khi su kien den sai thu tu.
 */
public record SisEnrollment(
        String enrollmentId,
        String studentId,
        String sectionId,
        String status) {

    public static SisEnrollment from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new SisEnrollment(
                text(n, "enrollmentId"),
                text(n, "studentId"),
                text(n, "sectionId"),
                text(n, "status"));
    }

    /** Chi {@code ENROLLED} moi tinh la con hieu luc; moi gia tri khac deu la khong. */
    public boolean isActive() {
        return "ENROLLED".equalsIgnoreCase(status == null ? "" : status.trim());
    }

    public boolean isCompleteEnoughForSync() {
        return notBlank(studentId) && notBlank(sectionId);
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
