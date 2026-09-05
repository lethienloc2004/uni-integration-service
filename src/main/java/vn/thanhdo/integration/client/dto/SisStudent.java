package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Student ben UniSIS — NGUON SU THAT cho danh tinh, email va trang thai (muc 4.1).
 *
 * @param status ACTIVE | SUSPENDED | ON_LEAVE | GRADUATED | DROPPED_OUT
 */
public record SisStudent(
        String studentId,
        String firstName,
        String lastName,
        String email,
        String programCode,
        String cohort,
        String status) {

    /** Doc tu JSON cua API hoac tu payload su kien — hai noi dung cung mot hinh dang. */
    public static SisStudent from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new SisStudent(
                text(n, "studentId"),
                text(n, "firstName"),
                text(n, "lastName"),
                text(n, "email"),
                text(n, "programCode"),
                text(n, "cohort"),
                text(n, "status"));
    }

    /** Du truong de dung lam desired ma khong can doc lai nguon (muc 3.3). */
    public boolean isCompleteEnoughForSync() {
        return notBlank(studentId) && notBlank(status)
                && notBlank(email) && (notBlank(lastName) || notBlank(firstName));
    }

    /**
     * displayName = lastName + " " + firstName, chuan hoa khoang trang thua.
     * Bang anh xa INT-01, muc 4.3. Ca kiem thu U03.
     */
    public String displayName() {
        String joined = (nullToEmpty(lastName) + " " + nullToEmpty(firstName)).trim();
        return joined.replaceAll("\\s+", " ");
    }

    /**
     * ACTIVE -> true; MOI trang thai khac -> false.
     * Day chinh la toan bo noi dung nghiep vu cua INT-05. Ca kiem thu U02.
     */
    public boolean enabled() {
        return "ACTIVE".equalsIgnoreCase(nullToEmpty(status).trim());
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
