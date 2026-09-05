package vn.thanhdo.integration.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI cho Integration Service — <code>/swagger-ui.html</code>.
 *
 * <p>Luu y ve pham vi: day KHONG phai mot API nghiep vu. Dich vu nay chu yeu la HTTP
 * client cua UniSIS va UniLearn; phan phuc vu vao chi gom bo nhan webhook, /health va
 * mot vai endpoint quan tri. Swagger o day dung de:
 * <ul>
 *   <li>trinh dien tang 3 cua quy trinh go loi bon tang khi bao ve (muc 10.2)</li>
 *   <li>bam thang <em>Try it out</em> tren /admin/reconcile va /admin/inbox/{id}/requeue
 *       thay vi phai go curl</li>
 * </ul>
 *
 * <p>Swagger cua HAI HE THONG NGUON nam o cho khac va do giang vien cung cap:
 * {@code http://<SIS>/docs} va {@code http://<LMS>/docs} (FastAPI tu sinh).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI integrationServiceOpenApi(IntegrationProperties props) {
        return new OpenAPI().info(new Info()
                .title("UniSIS ↔ UniLearn Integration Service")
                .version("1.0.0")
                .description("""
                        Dich vu tich hop doc lap noi UniSIS (SIS) va UniLearn (LMS).

                        **Tenant dang cau hinh:** `%s`

                        Cac nhom endpoint:
                        - `/health` — trang thai, ngan sach tan suat con lai, ton dong inbox (NFR-09)
                        - `/webhooks/**` — bo nhan callback, chi ghi inbox roi tra 200 ngay
                        - `/admin/**` — cong cu van hanh va trinh dien

                        Trang `/admin/inbox` chinh la **tang 3** trong quy trinh go loi bon tang
                        cua de bai: nhan su kien luc nao, dung anh xa nao, da thu lai may lan.
                        """.formatted(props.getTenantId()))
                .license(new License().name("Bai tap ket thuc hoc phan — Kien truc va Tich hop he thong")));
    }
}
