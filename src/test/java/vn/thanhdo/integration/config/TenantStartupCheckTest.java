package vn.thanhdo.integration.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-08 — cach ly tenant. Ca kiem thu U10 … U12.
 *
 * <p>Tron tenant la nguyen nhan so mot khien du lieu hai ben khong bao gio khop ma nhat ky
 * van bao thanh cong. Vi vay cau hinh sai phai lam dich vu DUNG NGAY chu khong chi canh bao.
 */
class TenantStartupCheckTest {

    private IntegrationProperties valid() {
        IntegrationProperties p = new IntegrationProperties();
        p.setTenantId("TEAM07");
        p.getSis().setBaseUrl("http://127.0.0.1:8001");
        p.getSis().setClientSecret("secret");
        p.getLms().setBaseUrl("http://127.0.0.1:8002");
        p.getLms().setClientSecret("secret");
        return p;
    }

    @Test
    @DisplayName("U10 — cau hinh day du thi khong co van de nao")
    void acceptsValidConfiguration() {
        assertThat(TenantStartupCheck.validate(valid())).isEmpty();
    }

    @Test
    @DisplayName("U11 — thieu tenant hoac thieu secret thi chan khoi dong")
    void rejectsMissingTenantOrSecret() {
        IntegrationProperties noTenant = valid();
        noTenant.setTenantId("  ");
        assertThat(TenantStartupCheck.validate(noTenant))
                .anySatisfy(m -> assertThat(m).contains("TENANT_ID"));

        IntegrationProperties noSecret = valid();
        noSecret.getLms().setClientSecret(null);
        assertThat(TenantStartupCheck.validate(noSecret))
                .anySatisfy(m -> assertThat(m).contains("UniLearn"));
    }

    @Test
    @DisplayName("U12 — hai he thong tro cung mot dia chi gan nhu chac chan la nham")
    void rejectsIdenticalUrls() {
        IntegrationProperties same = valid();
        same.getLms().setBaseUrl(same.getSis().getBaseUrl());
        assertThat(TenantStartupCheck.validate(same))
                .anySatisfy(m -> assertThat(m).contains("cung mot dia chi"));
    }
}
