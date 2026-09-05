package vn.thanhdo.integration.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.thanhdo.integration.client.ApiClient;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.inbox.InboxRepository;
import vn.thanhdo.integration.inbox.InboxStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NFR-09 — de bai yeu cau dich vu co {@code /health} va log du de giang vien kiem tra.
 *
 * <p>Mac dinh la phep kiem tra RE: khong goi ra ngoai, chi bao cau hinh, so luot con lai
 * trong ngan sach tan suat va so ban ghi inbox ton dong. Muon thu that thi goi
 * {@code /health?deep=true} — luc do moi ton hai luot goi de lay token.
 */
@RestController
public class HealthController {

    private final IntegrationProperties props;
    private final ApiClient sisClient;
    private final ApiClient lmsClient;
    private final InboxRepository inbox;

    public HealthController(IntegrationProperties props, ApiClient sisClient,
                            ApiClient lmsClient, InboxRepository inbox) {
        this.props = props;
        this.sisClient = sisClient;
        this.lmsClient = lmsClient;
        this.inbox = inbox;
    }

    @GetMapping("/health")
    public Map<String, Object> health(@RequestParam(defaultValue = "false") boolean deep) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("tenantId", props.getTenantId());

        out.put("sis", systemInfo(sisClient, props.getSis().getBaseUrl(), deep));
        out.put("lms", systemInfo(lmsClient, props.getLms().getBaseUrl(), deep));

        Map<String, Object> queue = new LinkedHashMap<>();
        for (InboxStatus s : InboxStatus.values()) {
            long n = inbox.countByStatus(s);
            if (n > 0 || s == InboxStatus.RECEIVED || s == InboxStatus.RETRYING) {
                queue.put(s.name(), n);
            }
        }
        out.put("inbox", queue);

        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put("webhook", props.getWebhook().isEnabled());
        modes.put("polling", props.getPoller().isEnabled());
        modes.put("worker", props.getWorker().isEnabled());
        out.put("modes", modes);

        return out;
    }

    private Map<String, Object> systemInfo(ApiClient client, String baseUrl, boolean deep) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl", baseUrl);
        m.put("permitsRemaining", client.getRateLimiter().availablePermits());
        if (deep) {
            m.put("canAuthenticate", client.canAuthenticate());
        }
        return m;
    }
}
