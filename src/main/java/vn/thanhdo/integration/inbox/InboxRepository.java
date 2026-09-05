package vn.thanhdo.integration.inbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InboxRepository extends JpaRepository<InboxEvent, Long> {

    Optional<InboxEvent> findBySourceSystemAndEventId(String sourceSystem, String eventId);

    /**
     * Cac ban ghi den luot xu ly: vua nhan, hoac dang cho thu lai va da den han.
     * Dung JPQL thay vi SQL thuan de bam dung anh xa thuc the.
     */
    @Query("""
           SELECT e FROM InboxEvent e
            WHERE e.status = vn.thanhdo.integration.inbox.InboxStatus.RECEIVED
               OR (e.status IN (vn.thanhdo.integration.inbox.InboxStatus.RETRYING,
                                vn.thanhdo.integration.inbox.InboxStatus.PENDING_DEPENDENCY)
                   AND e.nextAttemptAt <= :now)
            ORDER BY e.id ASC
           """)
    List<InboxEvent> findDue(@Param("now") Instant now, Pageable pageable);

    /**
     * GIANH VIEC bang cau lenh cap nhat co dieu kien — muc 7.2.
     *
     * <p>H2 khong ho tro {@code SELECT ... FOR UPDATE SKIP LOCKED}, nen dung thao tac
     * so-sanh-roi-doi nguyen tu nay thay the. He quan tri tuan tu hoa tren khoa hang:
     * tien trinh thang nhan ve 1 dong, tien trinh thua nhan ve 0 dong roi bo qua.
     * SO DONG BI ANH HUONG chinh la ket qua cuoc tranh chap.
     *
     * <p>Cach nay khong phu thuoc nha cung cap, nen chuyen sang PostgreSQL sau nay
     * khong phai sua gi.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE InboxEvent e
              SET e.status    = vn.thanhdo.integration.inbox.InboxStatus.PROCESSING,
                  e.workerId  = :workerId,
                  e.pickedAt  = :now,
                  e.attempt   = e.attempt + 1
            WHERE e.id     = :id
              AND e.status IN :allowed
           """)
    int claim(@Param("id") Long id,
              @Param("workerId") String workerId,
              @Param("now") Instant now,
              @Param("allowed") Collection<InboxStatus> allowed);

    long countByStatus(InboxStatus status);

    List<InboxEvent> findTop200ByOrderByIdDesc();

    List<InboxEvent> findTop200ByStatusOrderByIdDesc(InboxStatus status);
}
