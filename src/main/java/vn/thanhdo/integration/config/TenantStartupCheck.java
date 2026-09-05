package vn.thanhdo.integration.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kiem tra cau hinh tenant NGAY LUC KHOI DONG — NFR-08.
 *
 * <p><b>Vi sao dung han thay vi canh bao:</b> tron tenant la nguyen nhan so mot khien du lieu
 * hai ben khong bao gio khop MA NHAT KY VAN BAO THANH CONG. Neu chay voi cau hinh sai, dich
 * vu se lang le ghi du lieu vao khong gian cua nhom khac hoac vao mot tenant khong ton tai,
 * va nguoi dung mat hang gio de tim nguyen nhan. Dung ngay voi mot thong bao ro rang re hon
 * nhieu.
 *
 * <p>Day chi la kiem tra CAU HINH, khong goi ra ngoai — dich vu van phai khoi dong duoc khi
 * UniSIS/UniLearn chua san sang. Muon kiem chung ket noi that thi goi
 * {@code GET /health?deep=true}.
 */
@Component
public class TenantStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(TenantStartupCheck.class);

    private final IntegrationProperties props;

    public TenantStartupCheck(IntegrationProperties props) {
        this.props = props;
    }

    @PostConstruct
    void verify() {
        List<String> problems = validate(props);

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Cau hinh khong hop le, dich vu khong the khoi dong:"
                            + problems.stream().map(p -> "\n  - " + p).reduce("", String::concat)
                            + "\n\nXem HUONG-DAN-CHAY.md muc 2. Cach nhanh nhat: tao file .env"
                            + " o goc du an tu .env.example roi dien CLIENT_SECRET.");
        }

        // In dam tenant dang lam viec: khi go loi, day la thu can nhin dau tien
        log.info("=== TENANT DANG LAM VIEC: {} === SIS={} LMS={}",
                props.getTenantId(), props.getSis().getBaseUrl(), props.getLms().getBaseUrl());
    }

    /**
     * Tach rieng de kiem thu duoc ma khong phai khoi dong ca ung dung.
     *
     * @return danh sach van de; rong nghia la hop le
     */
    static List<String> validate(IntegrationProperties p) {
        List<String> problems = new ArrayList<>();

        if (isBlank(p.getTenantId())) {
            problems.add("TENANT_ID dang trong. Phai la tenant duoc giang vien cap, vi du TEAM07.");
        }
        if (isBlank(p.getSis().getBaseUrl())) {
            problems.add("SIS_URL dang trong.");
        }
        if (isBlank(p.getLms().getBaseUrl())) {
            problems.add("LMS_URL dang trong.");
        }
        if (isBlank(p.getSis().getClientSecret())) {
            problems.add("Client secret cua UniSIS dang trong. Dat CLIENT_SECRET"
                    + " (hoac SIS_CLIENT_SECRET) qua bien moi truong hoac file .env.");
        }
        if (isBlank(p.getLms().getClientSecret())) {
            problems.add("Client secret cua UniLearn dang trong. Dat CLIENT_SECRET"
                    + " (hoac LMS_CLIENT_SECRET) qua bien moi truong hoac file .env.");
        }
        if (p.getSis().getBaseUrl() != null
                && p.getSis().getBaseUrl().equals(p.getLms().getBaseUrl())) {
            problems.add("SIS_URL va LMS_URL dang tro cung mot dia chi — gan nhu chac chan la nham.");
        }
        return problems;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
