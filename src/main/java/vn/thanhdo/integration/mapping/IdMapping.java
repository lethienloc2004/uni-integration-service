package vn.thanhdo.integration.mapping;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Anh xa ID giua hai he thong — LA BO NHO DEM, KHONG PHAI NGUON SU THAT.
 *
 * <p>Bat bien BV-4 (muc 4.2): ID noi bo cua LMS khong bao gio duoc dung lam ma
 * nghiep vu. Khoa noi that la {@code externalRef} / {@code externalCode} o he thong
 * dich, nen neu mat mot ban ghi o bang nay ma du lieu dich van con thi he thong
 * tra lai duoc va tu phuc hoi — kiem chung boi ca kiem thu I10.
 */
@Entity
@Table(name = "id_mapping")
public class IdMapping {

    public static final String STUDENT_USER = "STUDENT_USER";
    public static final String SECTION_COURSE = "SECTION_COURSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", nullable = false, length = 16)
    private String sourceSystem;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "source_key", nullable = false, length = 128)
    private String sourceKey;      // studentId / sectionId

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;       // id noi bo cua LMS

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdMapping() { }

    public IdMapping(String sourceSystem, String entityType, String sourceKey, String targetId) {
        this.sourceSystem = sourceSystem;
        this.entityType = entityType;
        this.sourceKey = sourceKey;
        this.targetId = targetId;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSourceSystem() { return sourceSystem; }
    public String getEntityType() { return entityType; }
    public String getSourceKey() { return sourceKey; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; this.updatedAt = Instant.now(); }
    public Instant getUpdatedAt() { return updatedAt; }
}
