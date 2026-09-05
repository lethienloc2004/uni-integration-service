package vn.thanhdo.integration.reconcile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ket qua mot luot doi soat.
 *
 * <p>O che do {@code dryRun}, bao cao nay la toan bo dau ra — khong ghi gi ca. Do la cach
 * trinh dien thuyet phuc nhat cho kich ban T11: bao N cho lech, chay that, roi bao lai con 0.
 */
public class ReconcileReport {

    private final String runId;
    private final boolean dryRun;
    private final Instant startedAt = Instant.now();
    private final Map<String, EntityStat> entities = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private long durationMs;
    private int requestsUsed;

    public ReconcileReport(String runId, boolean dryRun) {
        this.runId = runId;
        this.dryRun = dryRun;
    }

    /** Thong ke cho mot doi tuong duoc doi soat. */
    public static class EntityStat {
        public int sourceCount;
        public int targetCount;
        /** Co o nguon, thieu o dich. */
        public int missing;
        /** Co ca hai ben nhung lech truong do nguon su that lam chu. */
        public int drifted;
        /** Con hieu luc o dich nhung nguon da huy. */
        public int extra;
        /** Da dua vao hang doi de sua (bang 0 khi dryRun). */
        public int enqueued;
        public final List<String> samples = new ArrayList<>();

        void sample(String key) {
            if (samples.size() < 10) {
                samples.add(key);
            }
        }

        public int totalDrift() {
            return missing + drifted + extra;
        }
    }

    EntityStat entity(String name) {
        return entities.computeIfAbsent(name, k -> new EntityStat());
    }

    void warn(String message) {
        if (warnings.size() < 20) {
            warnings.add(message);
        }
    }

    void finish(long durationMs, int requestsUsed) {
        this.durationMs = durationMs;
        this.requestsUsed = requestsUsed;
    }

    public int totalDrift() {
        return entities.values().stream().mapToInt(EntityStat::totalDrift).sum();
    }

    public int totalEnqueued() {
        return entities.values().stream().mapToInt(e -> e.enqueued).sum();
    }

    public String getRunId() { return runId; }
    public boolean isDryRun() { return dryRun; }
    public Instant getStartedAt() { return startedAt; }
    public long getDurationMs() { return durationMs; }
    public int getRequestsUsed() { return requestsUsed; }
    public Map<String, EntityStat> getEntities() { return entities; }
    public List<String> getWarnings() { return warnings; }
}
