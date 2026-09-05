package vn.thanhdo.integration.client;

import java.time.Duration;

/** Loi tra ve tu UniSIS hoac UniLearn, da duoc phan loai theo hop dong loi 4.4. */
public class ApiException extends RuntimeException {

    private final String system;
    private final int status;
    private final String endpoint;
    private final String body;
    private final ErrorClass errorClass;
    private final Duration retryAfter;

    public ApiException(String system, int status, String endpoint, String body,
                        ErrorClass errorClass, Duration retryAfter) {
        super("%s %s -> HTTP %d [%s]".formatted(system, endpoint, status, errorClass));
        this.system = system;
        this.status = status;
        this.endpoint = endpoint;
        this.body = body;
        this.errorClass = errorClass;
        this.retryAfter = retryAfter;
    }

    /** Loi mang / khong ket noi duoc — coi la tam thoi. */
    public static ApiException transientFailure(String system, String endpoint, Throwable cause) {
        ApiException e = new ApiException(system, 0, endpoint,
                cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                ErrorClass.TRANSIENT, null);
        e.initCause(cause);
        return e;
    }

    /**
     * Tinh chinh phan loai cho ma 422: handler biet phu thuoc thieu co nam trong
     * tham quyen tao cua minh hay khong.
     */
    public ApiException reclassify(ErrorClass newClass) {
        return new ApiException(system, status, endpoint, body, newClass, retryAfter);
    }

    public boolean isStatus(int s) { return this.status == s; }

    public String getSystem() { return system; }
    public int getStatus() { return status; }
    public String getEndpoint() { return endpoint; }
    public String getBody() { return body; }
    public ErrorClass getErrorClass() { return errorClass; }
    public Duration getRetryAfter() { return retryAfter; }
}
