package vn.thanhdo.integration.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

import static vn.thanhdo.integration.client.ApiClient.redact;

@Repository
interface IntegrationLogRepository extends JpaRepository<IntegrationLog, Long> {
    List<IntegrationLog> findByEventIdOrderByIdAsc(String eventId);
    List<IntegrationLog> findTop200ByOrderByIdDesc();
}

/**
 * Ghi nhat ky kiem toan vao ca CSDL va console, theo dinh dang o muc 10.1.
 *
 * <p>Moi dong deu di qua bo che bi mat truoc khi ghi (NFR-07) — ca kiem thu I15
 * quet lai toan bo log sinh ra trong bo kiem thu de xac nhan khong lot token nao.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("INTEGRATION");

    private final IntegrationLogRepository repo;

    public AuditService(IntegrationLogRepository repo) {
        this.repo = repo;
    }

    public void write(IntegrationLog entry) {
        repo.save(entry);
        log.info("evt={} type={} src={} fn={} key={} action={} target={} http={} attempt={} dur={}ms status={}{}",
                entry.getEventId(),
                entry.getEventType(),
                entry.getSourceSystem(),
                entry.getHandler(),
                entry.getBusinessKey(),
                entry.getAction(),
                entry.getEndpoint(),
                entry.getHttpStatus(),
                entry.getAttempt(),
                entry.getDurationMs(),
                entry.getStatus(),
                entry.getMessage() == null ? "" : " msg=" + redact(entry.getMessage()));
    }

    public List<IntegrationLog> findByEventId(String eventId) {
        return repo.findByEventIdOrderByIdAsc(eventId);
    }

    public List<IntegrationLog> recent() {
        return repo.findTop200ByOrderByIdDesc();
    }
}
