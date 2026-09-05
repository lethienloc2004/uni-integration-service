package vn.thanhdo.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Toan bo cau hinh doc tu bien moi truong (NFR-07: khong hard-code bi mat).
 */
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private String tenantId;
    private System sis = new System();
    private System lms = new System();
    private RateLimit rateLimit = new RateLimit();
    private Retry retry = new Retry();
    private DesiredHint desiredHint = new DesiredHint();
    private Worker worker = new Worker();
    private Webhook webhook = new Webhook();
    private Poller poller = new Poller();
    private Reconciler reconciler = new Reconciler();

    public static class System {
        private String baseUrl;
        private String clientId;
        private String clientSecret;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getClientId() { return clientId; }
        public void setClientId(String v) { this.clientId = v; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String v) { this.clientSecret = v; }
    }

    public static class RateLimit {
        private int permitsPerMinute = 80;
        public int getPermitsPerMinute() { return permitsPerMinute; }
        public void setPermitsPerMinute(int v) { this.permitsPerMinute = v; }
    }

    public static class Retry {
        private int maxAttempts = 6;
        private List<Integer> backoffSeconds = List.of(1, 2, 4, 8, 16, 32);
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { this.maxAttempts = v; }
        public List<Integer> getBackoffSeconds() { return backoffSeconds; }
        public void setBackoffSeconds(List<Integer> v) { this.backoffSeconds = v; }
    }

    public static class DesiredHint {
        private long maxAgeSeconds = 60;
        public long getMaxAgeSeconds() { return maxAgeSeconds; }
        public void setMaxAgeSeconds(long v) { this.maxAgeSeconds = v; }
    }

    public static class Worker {
        private boolean enabled = true;
        private long pollIntervalMs = 1000;
        private int batchSize = 20;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long v) { this.pollIntervalMs = v; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int v) { this.batchSize = v; }
    }

    public static class Webhook {
        private boolean enabled = true;
        private String publicUrl;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getPublicUrl() { return publicUrl; }
        public void setPublicUrl(String v) { this.publicUrl = v; }
    }

    public static class Poller {
        private boolean enabled = true;
        private long intervalMs = 10000;
        private int overlapSeconds = 5;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long v) { this.intervalMs = v; }
        public int getOverlapSeconds() { return overlapSeconds; }
        public void setOverlapSeconds(int v) { this.overlapSeconds = v; }
    }

    public static class Reconciler {
        /** BAT BUOC theo muc 6.5: lan duy nhat bu duoc du lieu co san ma khong co su kien. */
        private boolean runOnStartup = true;
        /** Lop phong thu du phong — duoc phep tat dau tien khi can nhuong bang thong. */
        private boolean runOnSchedule = true;
        private long intervalMs = 600_000;

        public boolean isRunOnStartup() { return runOnStartup; }
        public void setRunOnStartup(boolean v) { this.runOnStartup = v; }
        public boolean isRunOnSchedule() { return runOnSchedule; }
        public void setRunOnSchedule(boolean v) { this.runOnSchedule = v; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long v) { this.intervalMs = v; }
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public System getSis() { return sis; }
    public void setSis(System v) { this.sis = v; }
    public System getLms() { return lms; }
    public void setLms(System v) { this.lms = v; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit v) { this.rateLimit = v; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry v) { this.retry = v; }
    public DesiredHint getDesiredHint() { return desiredHint; }
    public void setDesiredHint(DesiredHint v) { this.desiredHint = v; }
    public Worker getWorker() { return worker; }
    public void setWorker(Worker v) { this.worker = v; }
    public Webhook getWebhook() { return webhook; }
    public void setWebhook(Webhook v) { this.webhook = v; }
    public Poller getPoller() { return poller; }
    public void setPoller(Poller v) { this.poller = v; }
    public Reconciler getReconciler() { return reconciler; }
    public void setReconciler(Reconciler v) { this.reconciler = v; }
}
