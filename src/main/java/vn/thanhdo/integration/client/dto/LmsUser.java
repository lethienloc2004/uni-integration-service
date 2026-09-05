package vn.thanhdo.integration.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * User ben UniLearn LMS.
 *
 * <p>{@code id} la ID SO NOI BO cua LMS — chi co y nghia trong LMS va KHONG BAO GIO
 * duoc dung lam ma nghiep vu (bat bien BV-4, muc 4.2).
 * Khoa noi that giua hai he thong la {@code externalRef}.
 */
public record LmsUser(
        String id,
        String username,
        String displayName,
        String emailAddress,
        String userType,
        boolean enabled,
        String externalRef) {

    public static LmsUser from(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return new LmsUser(
                text(n, "id"),
                text(n, "username"),
                text(n, "displayName"),
                text(n, "emailAddress"),
                text(n, "userType"),
                n.hasNonNull("enabled") && n.get("enabled").asBoolean(),
                text(n, "externalRef"));
    }

    /**
     * Cac truong do UniSIS lam chu co lech khong (muc 4.1).
     * Chi ba truong nay duoc phep ghi de; username va externalRef la khoa noi,
     * ghi mot lan khi tao va khong bao gio sua.
     */
    public boolean differsFrom(SisStudent desired) {
        return !Objects.equals(nullToEmpty(displayName), desired.displayName())
                || !Objects.equals(nullToEmpty(emailAddress), nullToEmpty(desired.email()))
                || enabled != desired.enabled();
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
