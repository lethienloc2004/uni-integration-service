package vn.thanhdo.integration.mapping;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Truy cap bang anh xa ID. */
@Repository
interface IdMappingRepository extends JpaRepository<IdMapping, Long> {
    Optional<IdMapping> findBySourceSystemAndEntityTypeAndSourceKey(
            String sourceSystem, String entityType, String sourceKey);
}

/**
 * Doc va ghi anh xa ID.
 *
 * <p>Luu y ve tinh than su dung: KHONG BAO GIO tin bang nay lam cau tra loi cuoi.
 * Anh xa chi giup bo qua mot luot tra cuu; khi no vang mat hoac tro sai, tang
 * nghiep vu luon tra lai duoc bang khoa nghiep vu o he thong dich.
 */
@Service
public class IdMappingService {

    private final IdMappingRepository repo;

    public IdMappingService(IdMappingRepository repo) {
        this.repo = repo;
    }

    public Optional<String> findTargetId(String sourceSystem, String entityType, String sourceKey) {
        return repo.findBySourceSystemAndEntityTypeAndSourceKey(sourceSystem, entityType, sourceKey)
                .map(IdMapping::getTargetId);
    }

    /** Ghi moi hoac cap nhat anh xa. An toan khi goi lai nhieu lan. */
    public void upsert(String sourceSystem, String entityType, String sourceKey, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        Optional<IdMapping> existing =
                repo.findBySourceSystemAndEntityTypeAndSourceKey(sourceSystem, entityType, sourceKey);
        if (existing.isPresent()) {
            IdMapping m = existing.get();
            if (!targetId.equals(m.getTargetId())) {
                m.setTargetId(targetId);
                repo.save(m);
            }
            return;
        }
        try {
            repo.save(new IdMapping(sourceSystem, entityType, sourceKey, targetId));
        } catch (DataIntegrityViolationException race) {
            // mot luong khac vua ghi cung khoa — ket qua cuoi van dung
            repo.findBySourceSystemAndEntityTypeAndSourceKey(sourceSystem, entityType, sourceKey)
                    .ifPresent(m -> {
                        if (!targetId.equals(m.getTargetId())) {
                            m.setTargetId(targetId);
                            repo.save(m);
                        }
                    });
        }
    }

    /** Dung cho ca kiem thu I10 va cho thao tac ra soat thu cong. */
    public void deleteFor(String sourceSystem, String entityType, String sourceKey) {
        repo.findBySourceSystemAndEntityTypeAndSourceKey(sourceSystem, entityType, sourceKey)
                .ifPresent(repo::delete);
    }
}
