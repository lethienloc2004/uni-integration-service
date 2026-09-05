package vn.thanhdo.integration.client;

/**
 * Phan loai loi theo HOP DONG LOI — bang 4.4 cua De xuat giai phap v2.0.
 *
 * <p>Day la diem mau chot cua NFR-02 va NFR-03: loi tam thoi thi thu lai,
 * loi du lieu thi dung han va ghi vet de con nguoi ra soat. Khong bao gio
 * thu lai vo han.
 */
public enum ErrorClass {

    /** 401 — token het han. Lay token moi roi goi lai dung MOT lan, trong suot voi worker. */
    AUTH_REFRESH,

    /** 403 — sai tenant. Dung ngay, khong thu lai: day la loi cau hinh, thu lai vo ich. */
    TENANT_MISMATCH,

    /** 404 — khong tim thay. Voi thao tac go thi co the la trang thai cuoi da dung. */
    NOT_FOUND,

    /** 409 — da ton tai. KHONG phai loi: tra lai theo khoa nghiep vu roi hop nhat anh xa. */
    CONFLICT,

    /** 422 — thieu phu thuoc ma Integration Service CO quyen tao (vi du User/Course). */
    DEPENDENCY_MISSING,

    /**
     * 422 — loi du lieu that su, hoac thieu phu thuoc NGOAI tham quyen.
     * Vi du INT-06 gap ENROLLMENT_NOT_FOUND: dang ky hoc thuoc tham quyen cua
     * UniSIS, Integration Service khong duoc tu tao. Chuyen thang dead-letter.
     */
    DATA_ERROR,

    /** 429 — vuot gioi han. Doc Retry-After va cho DUNG khoang do, khong dung backoff. */
    RATE_LIMITED,

    /** 500/503 hoac loi mang — thu lai voi backoff luy thua co gioi han. */
    TRANSIENT;

    public boolean isRetryable() {
        return this == RATE_LIMITED || this == TRANSIENT || this == DEPENDENCY_MISSING;
    }

    /** Phan loai theo ma HTTP. Ma 422 duoc tinh chinh them boi tung handler. */
    public static ErrorClass fromHttpStatus(int status) {
        return switch (status) {
            case 401 -> AUTH_REFRESH;
            case 403 -> TENANT_MISMATCH;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 422 -> DATA_ERROR;
            case 429 -> RATE_LIMITED;
            case 500, 502, 503, 504 -> TRANSIENT;
            default -> status >= 500 ? TRANSIENT : DATA_ERROR;
        };
    }
}
