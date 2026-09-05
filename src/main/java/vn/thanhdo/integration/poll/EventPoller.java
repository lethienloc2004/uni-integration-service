package vn.thanhdo.integration.poll;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import vn.thanhdo.integration.client.LmsApi;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.inbox.InboxService;
import vn.thanhdo.integration.util.Timestamps;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * XUONG SONG bao dam khong mat su kien (muc 6.5).
 *
 * <p>Diem mau chot: {@code /api/v1/events} la mot BANG DU LIEU BEN VUNG chu khong phai
 * hang doi tieu thu mot lan. Su kien da phat se nam do, nen mot bo doc co diem moc lay
 * duoc MOI su kien — ke ca nhung su kien ma webhook da lam rot vi qua thoi gian cho.
 * Webhook chi la lop tang toc va ve nguyen tac co the bo hoan toan ma he thong van dung.
 */
@Component
public class EventPoller {

    private static final Logger log = LoggerFactory.getLogger(EventPoller.class);

    private final SisApi sis;
    private final LmsApi lms;
    private final InboxService inbox;
    private final PollCheckpointRepository checkpoints;
    private final IntegrationProperties props;

    public EventPoller(SisApi sis, LmsApi lms, InboxService inbox,
                       PollCheckpointRepository checkpoints, IntegrationProperties props) {
        this.sis = sis;
        this.lms = lms;
        this.inbox = inbox;
        this.checkpoints = checkpoints;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${integration.poller.interval-ms:10000}")
    public void poll() {
        if (!props.getPoller().isEnabled()) {
            return;
        }
        pollOne("SIS", sis::getEvents);
        pollOne("LMS", lms::getEvents);
    }

    /** Tach rieng de ca kiem thu goi truc tiep. Tra ve so su kien MOI da ghi vao inbox. */
    public int pollOne(String system, Function<Instant, List<JsonNode>> fetch) {
        try {
            Instant since = readCheckpoint(system);
            List<JsonNode> events = fetch.apply(since);

            int inserted = 0;
            Instant newest = since;

            for (JsonNode ev : events) {
                String eventId = text(ev, "eventId");
                String eventType = text(ev, "eventType");
                if (eventId == null || eventType == null) {
                    continue;
                }
                Instant occurredAt = Timestamps.parse(text(ev, "occurredAt"));

                // Su kien da vao qua webhook thi rang buoc UNIQUE chan lai o day —
                // dung y do: hai kenh khu trung lan nhau ma khong can phoi hop gi.
                if (inbox.record(InboxService.Channel.POLLER, system, eventId, eventType,
                        ev.toString(), occurredAt) == InboxService.Result.INSERTED) {
                    inserted++;
                }
                if (occurredAt != null && (newest == null || occurredAt.isAfter(newest))) {
                    newest = occurredAt;
                }
            }

            if (newest != null) {
                writeCheckpoint(system, newest);
            }
            if (inserted > 0) {
                log.info("[{}] poller ghi them {} su kien moi", system, inserted);
            }
            return inserted;

        } catch (Exception e) {
            // Poller khong duoc lam sap ung dung. Luot sau thu lai tu dung diem moc cu.
            log.warn("[{}] poller loi mot luot: {}", system, e.getMessage());
            return 0;
        }
    }

    /**
     * Diem moc voi KHOANG CHONG LAN (muc 6.3).
     *
     * <p>Bo loc {@code since} dung phep so sanh lon hon tuyet doi, nen hai su kien cung
     * mot moc thoi gian co the khien su kien thu hai bi bo sot. Cach chua: luu moc cua
     * su kien cuoi nhung truy van lan sau LUI LAI vai giay. Phan chong lan tao ra cac
     * su kien da xu ly, va lop chong trung theo eventId loai bo chung mien phi.
     * Chi phi bang khong, rui ro mat su kien bang khong.
     */
    private Instant readCheckpoint(String system) {
        return checkpoints.findById(system)
                .map(PollCheckpoint::getLastEventAt)
                .map(t -> t.minusSeconds(props.getPoller().getOverlapSeconds()))
                .orElse(null);
    }

    private void writeCheckpoint(String system, Instant newest) {
        PollCheckpoint cp = checkpoints.findById(system).orElseGet(() -> new PollCheckpoint(system));
        cp.setLastEventAt(newest);
        checkpoints.save(cp);
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}

/** Moc thoi gian da doc den cua moi he thong. */
@Entity
@Table(name = "poll_checkpoint")
class PollCheckpoint {

    @Id
    @Column(name = "source_system", length = 16)
    private String sourceSystem;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PollCheckpoint() { }

    PollCheckpoint(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    String getSourceSystem() { return sourceSystem; }
    Instant getLastEventAt() { return lastEventAt; }

    void setLastEventAt(Instant v) {
        this.lastEventAt = v;
        this.updatedAt = Instant.now();
    }
}

@Repository
interface PollCheckpointRepository extends JpaRepository<PollCheckpoint, String> { }
