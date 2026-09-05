package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Du lieu su kien {@code learner.at_risk} do UniLearn phat ra — INT-08.
 *
 * <p>Luong nguoc thu hai: LMS → SIS. UniLearn chi phat su kien nay khi thoa DONG THOI
 * {@code completionPercent < 30} va {@code inactiveDays >= 14} (muc 7.4 cua de bai).
 * Tin hieu {@code NORMAL} khong phat su kien, nen khong bao gio den duoc bo xu ly.
 */
public record LmsLearnerAtRisk(
        String riskId,
        String userExternalRef,
        String courseExternalCode,
        Double completionPercent,
        Integer inactiveDays,
        String riskType) {

    public static final String AT_RISK = "AT_RISK";

    public static LmsLearnerAtRisk from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new LmsLearnerAtRisk(
                text(n, "riskId"),
                text(n, "userExternalRef"),
                text(n, "courseExternalCode"),
                n.hasNonNull("completionPercent") ? n.get("completionPercent").asDouble() : null,
                n.hasNonNull("inactiveDays") ? n.get("inactiveDays").asInt() : null,
                text(n, "riskType"));
    }

    public boolean hasRequiredFields() {
        return notBlank(userExternalRef) && notBlank(courseExternalCode);
    }

    /**
     * Phong thu: chi xu ly tin hieu AT_RISK. UniLearn khong phat su kien cho NORMAL nen
     * dieu kien nay le ra luon dung — nhung kiem tra van re hon la tao nham mot canh bao.
     */
    public boolean isAtRisk() {
        return AT_RISK.equalsIgnoreCase(riskType == null ? AT_RISK : riskType.trim());
    }

    /**
     * Mo ta gui kem canh bao — bang anh xa muc 18.2 yeu cau
     * "chi tiet du de co van hieu nguyen nhan canh bao".
     */
    public String toDetails() {
        return "Tien do hoc tap %s%%, khong hoat dong %s ngay (nguon: UniLearn, riskId=%s)"
                .formatted(
                        completionPercent == null ? "?" : trimZero(completionPercent),
                        inactiveDays == null ? "?" : inactiveDays,
                        riskId == null ? "?" : riskId);
    }

    private static String trimZero(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
