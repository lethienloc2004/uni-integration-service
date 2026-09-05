package vn.thanhdo.integration.reconcile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.CourseCatalog;
import vn.thanhdo.integration.client.LmsApi;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.client.dto.LmsCourse;
import vn.thanhdo.integration.client.dto.LmsUser;
import vn.thanhdo.integration.client.dto.SisEnrollment;
import vn.thanhdo.integration.client.dto.SisSection;
import vn.thanhdo.integration.client.dto.SisStudent;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.inbox.InboxService;

import java.time.Instant;
import java.util.*;

/**
 * Bo doi soat — NFR-06.
 *
 * <p><b>Vi sao bat buoc phai co:</b> viec khoi tao lai du lieu KHONG sinh lai lich su su kien.
 * Sau khi reset, UniSIS co 100 Student va 15 Section trong khi UniLearn chi co 80 User va
 * 12 Course. Phan lech do khong he co su kien tuong ung, nen mot dich vu chi nghe webhook
 * hoac polling se KHONG BAO GIO chua duoc — du chay bao lau. Day chinh la kich ban cham T11.
 *
 * <h2>Phat hien theo TAP HOP</h2>
 * Tai toan bo hai phia roi so trong bo nho: <b>6 request</b> cho ca ba doi tuong, thay vi
 * hang tram luot tra cuu tung ban ghi. Voi gioi han 100 request/phut, day khong phai toi uu
 * cho vui ma la dieu kien de song sot.
 *
 * <h2>Sua lech KHONG doc lai</h2>
 * Bo doi soat vua tai toan bo du lieu nen da co san moi thu trong bo nho. Su kien tong hop
 * mang theo luon phan du lieu da phan giai, va bo xu ly dung no lam {@code desiredHint}
 * (muc 3.3). Neu khong lam vay, mot dot sua 40 ban ghi se ton hang tram request va mat vai
 * phut — dung luc giang vien dang ngoi cho.
 *
 * <h2>Mot duong xu ly duy nhat</h2>
 * Bo doi soat KHONG co nhanh xu ly rieng. No phat hien lech roi sinh su kien tong hop dua
 * vao CUNG bang inbox, di qua CUNG cac ham hoi tu. Nho vay chi co mot duong can kiem thu.
 */
@Component
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    static final String SOURCE_RECON = "RECON";

    private final SisApi sis;
    private final LmsApi lms;
    private final CourseCatalog catalog;
    private final InboxService inbox;
    private final IntegrationProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile ReconcileReport lastReport;

    public Reconciler(SisApi sis, LmsApi lms, CourseCatalog catalog,
                      InboxService inbox, IntegrationProperties props) {
        this.sis = sis;
        this.lms = lms;
        this.catalog = catalog;
        this.inbox = inbox;
        this.props = props;
    }

    /**
     * Chay luc khoi dong — BAT BUOC theo muc 6.5 cua ban thiet ke.
     *
     * <p>Day la lan duy nhat bu duoc phan du lieu co san ma khong he co su kien nao. Bo qua
     * buoc nay dong nghia voi viec chap nhan mai mai lech 20 User va 3 Course sau moi lan reset.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.getReconciler().isRunOnStartup()) {
            log.info("doi soat luc khoi dong dang TAT theo cau hinh");
            return;
        }
        try {
            ReconcileReport r = reconcile(false);
            log.info("doi soat luc khoi dong xong: {} cho lech, da dua {} viec vao hang doi",
                    r.totalDrift(), r.totalEnqueued());
        } catch (Exception e) {
            // Khong duoc lam sap ung dung chi vi mot luot doi soat that bai
            log.warn("doi soat luc khoi dong that bai, se thu lai theo lich: {}", e.getMessage());
        }
    }

    /**
     * Chay dinh ky — LOP PHONG THU du phong, khong phai co che chinh.
     *
     * <p>Vi polling da bao dam khong mat su kien, du lieu chi con co the lech neu co nguoi sua
     * tay o he thong dich hoac do loi lap trinh. Chi phi khoang 6 request moi luot nen giu lai
     * gan nhu mien phi; neu can nhuong bang thong thi day la thu duoc phep tat dau tien.
     */
    @Scheduled(fixedDelayString = "${integration.reconciler.interval-ms:600000}",
               initialDelayString = "${integration.reconciler.interval-ms:600000}")
    public void onSchedule() {
        if (!props.getReconciler().isRunOnSchedule()) {
            return;
        }
        try {
            ReconcileReport r = reconcile(false);
            if (r.totalDrift() > 0) {
                log.info("doi soat dinh ky: {} cho lech, da dua {} viec vao hang doi",
                        r.totalDrift(), r.totalEnqueued());
            }
        } catch (Exception e) {
            log.warn("doi soat dinh ky that bai: {}", e.getMessage());
        }
    }

    /**
     * @param dryRun {@code true} thi chi quet va bao cao, KHONG ghi gi — dung de cho thay
     *               pham vi lech truoc khi sua, va de xac nhan da sach sau khi sua.
     */
    public synchronized ReconcileReport reconcile(boolean dryRun) {
        long t0 = System.currentTimeMillis();

        // runId sinh MOI moi luot. Neu dat ma su kien theo thuc the thi rang buoc UNIQUE
        // cua inbox se coi luot doi soat thu hai la trung va bo qua toan bo — xem muc 5.4.
        String runId = "r" + Long.toHexString(System.currentTimeMillis());
        ReconcileReport report = new ReconcileReport(runId, dryRun);

        log.info("bat dau doi soat runId={} dryRun={}", runId, dryRun);

        // ---- Phat hien theo tap hop: 6 request cho ca ba doi tuong -----------------
        List<SisStudent> students = sis.listStudents();
        List<SisSection> sections = sis.listSections();
        List<SisEnrollment> enrollments = sis.listEnrollments();
        List<LmsUser> users = lms.listUsers();
        List<LmsCourse> courses = lms.listCourses();
        List<JsonNode> memberships = lms.listAllActiveMemberships();

        reconcileStudents(students, users, report, runId, dryRun);
        reconcileSections(sections, courses, report, runId, dryRun);
        reconcileEnrollments(enrollments, students, sections, users, courses, memberships,
                report, runId, dryRun);

        report.finish(System.currentTimeMillis() - t0, 6);
        this.lastReport = report;

        log.info("doi soat runId={} xong sau {}ms: {} cho lech, {} viec vao hang doi",
                runId, report.getDurationMs(), report.totalDrift(), report.totalEnqueued());
        return report;
    }

    // ==================================================================
    // Student -> LMS User
    // ==================================================================
    private void reconcileStudents(List<SisStudent> students, List<LmsUser> users,
                                   ReconcileReport report, String runId, boolean dryRun) {
        ReconcileReport.EntityStat stat = report.entity("STUDENT_USER");
        stat.sourceCount = students.size();
        stat.targetCount = users.size();

        Map<String, LmsUser> byExternalRef = new HashMap<>();
        for (LmsUser u : users) {
            if (u.externalRef() != null) {
                byExternalRef.put(u.externalRef(), u);
            }
        }

        for (SisStudent s : students) {
            if (s.studentId() == null) {
                continue;
            }
            LmsUser actual = byExternalRef.get(s.studentId());

            boolean needsWork;
            if (actual == null) {
                stat.missing++;
                needsWork = true;
            } else if (actual.differsFrom(s)) {
                stat.drifted++;
                needsWork = true;
            } else {
                needsWork = false;
            }

            if (needsWork) {
                stat.sample(s.studentId());
                if (!dryRun) {
                    enqueue(runId, "student", s.studentId(), "student.updated",
                            studentPayload(s), stat);
                }
            }
        }
        // Khong co khai niem "thua" o day: mot LMS User khong con Student tuong ung van
        // duoc giu lai de bao toan lich su hoc tap. De bai khong yeu cau xoa.
    }

    // ==================================================================
    // Section -> LMS Course
    // ==================================================================
    private void reconcileSections(List<SisSection> sections, List<LmsCourse> courses,
                                   ReconcileReport report, String runId, boolean dryRun) {
        ReconcileReport.EntityStat stat = report.entity("SECTION_COURSE");
        stat.sourceCount = sections.size();
        stat.targetCount = courses.size();

        Map<String, LmsCourse> byExternalCode = new HashMap<>();
        for (LmsCourse c : courses) {
            if (c.externalCode() != null) {
                byExternalCode.put(c.externalCode(), c);
            }
        }

        for (SisSection s : sections) {
            if (s.sectionId() == null) {
                continue;
            }
            LmsCourse actual = byExternalCode.get(s.sectionId());
            String title = s.lmsTitle(catalog.courseName(s.courseCode()));

            boolean needsWork;
            if (actual == null) {
                stat.missing++;
                needsWork = true;
            } else if (actual.differsFrom(title, s.lmsState(), s.lecturerId())) {
                stat.drifted++;
                needsWork = true;
            } else {
                needsWork = false;
            }

            if (needsWork) {
                stat.sample(s.sectionId());
                if (!dryRun) {
                    enqueue(runId, "section", s.sectionId(), "section.updated",
                            sectionPayload(s), stat);
                }
            }
        }
    }

    // ==================================================================
    // Enrollment -> Membership
    // ==================================================================
    private void reconcileEnrollments(List<SisEnrollment> enrollments,
                                      List<SisStudent> students, List<SisSection> sections,
                                      List<LmsUser> users, List<LmsCourse> courses,
                                      List<JsonNode> memberships,
                                      ReconcileReport report, String runId, boolean dryRun) {
        ReconcileReport.EntityStat stat = report.entity("ENROLLMENT_MEMBERSHIP");

        // Phan giai hai chieu de con dich nguoc tu ID noi bo ve ma nghiep vu
        Map<String, String> userIdByStudent = new HashMap<>();
        Map<String, String> studentByUserId = new HashMap<>();
        for (LmsUser u : users) {
            if (u.externalRef() != null && u.id() != null) {
                userIdByStudent.put(u.externalRef(), u.id());
                studentByUserId.put(u.id(), u.externalRef());
            }
        }
        Map<String, String> courseIdBySection = new HashMap<>();
        Map<String, String> sectionByCourseId = new HashMap<>();
        for (LmsCourse c : courses) {
            if (c.externalCode() != null && c.id() != null) {
                courseIdBySection.put(c.externalCode(), c.id());
                sectionByCourseId.put(c.id(), c.externalCode());
            }
        }

        // Trang thai MONG MUON: cac cap dang ky con hieu luc o UniSIS
        Set<String> desired = new HashSet<>();
        for (SisEnrollment e : enrollments) {
            if (e.isActive() && e.studentId() != null && e.sectionId() != null) {
                desired.add(e.studentId() + "|" + e.sectionId());
            }
        }
        stat.sourceCount = desired.size();

        // Trang thai THUC TE: membership dang ACTIVE o UniLearn, dich nguoc ve ma nghiep vu
        Set<String> actual = new HashSet<>();
        for (JsonNode m : memberships) {
            String studentId = studentByUserId.get(text(m, "userId"));
            String sectionId = sectionByCourseId.get(text(m, "courseId"));
            if (studentId == null || sectionId == null) {
                // Khong dich nguoc duoc — TUYET DOI khong go. Co the la du lieu ngoai pham vi
                // tich hop, va xoa nham thi khong khoi phuc duoc.
                report.warn("membership courseId=%s userId=%s khong dich nguoc duoc ve ma nghiep vu, bo qua"
                        .formatted(text(m, "courseId"), text(m, "userId")));
                continue;
            }
            actual.add(studentId + "|" + sectionId);
        }
        stat.targetCount = actual.size();

        // Thieu o dich -> them
        for (String key : desired) {
            if (!actual.contains(key)) {
                stat.missing++;
                stat.sample(key);
                if (!dryRun) {
                    String[] p = key.split("\\|", 2);
                    enqueue(runId, "enrollment", key, "enrollment.created",
                            enrollmentPayload(p[0], p[1], "ENROLLED"), stat);
                }
            }
        }

        // Thua o dich -> go
        for (String key : actual) {
            if (!desired.contains(key)) {
                stat.extra++;
                stat.sample(key);
                if (!dryRun) {
                    String[] p = key.split("\\|", 2);
                    enqueue(runId, "enrollment", key, "enrollment.dropped",
                            enrollmentPayload(p[0], p[1], "DROPPED"), stat);
                }
            }
        }
    }

    // ==================================================================
    // Sinh su kien tong hop
    // ==================================================================

    /**
     * Ma su kien: {@code recon:{runId}:{entityType}:{businessKey}}.
     *
     * <p>{@code runId} sinh moi moi luot chay — day la diem then chot. Neu dat ma theo thuc the
     * thi rang buoc UNIQUE(source_system, event_id) se coi luot doi soat thu hai la trung va
     * bo qua sach, khien viec bam doi soat truoc mat giang vien khong lam gi ca (muc 5.4).
     */
    private void enqueue(String runId, String entityType, String businessKey,
                         String eventType, String payload, ReconcileReport.EntityStat stat) {
        String eventId = "recon:%s:%s:%s".formatted(runId, entityType, businessKey);
        if (inbox.record(InboxService.Channel.RECON, SOURCE_RECON, eventId, eventType,
                payload, Instant.now()) == InboxService.Result.INSERTED) {
            stat.enqueued++;
        }
    }

    /*
     * Su kien tong hop MANG THEO du lieu da phan giai. Bo xu ly dung no lam desiredHint
     * (muc 3.3) nen khong phai doc lai nguon — nho vay mot dot sua chi ton dung so request
     * bang so thao tac ghi that su can thiet.
     */

    private String studentPayload(SisStudent s) {
        ObjectNode data = mapper.createObjectNode();
        data.put("studentId", s.studentId());
        data.put("firstName", s.firstName());
        data.put("lastName", s.lastName());
        data.put("email", s.email());
        data.put("programCode", s.programCode());
        data.put("cohort", s.cohort());
        data.put("status", s.status());
        return wrap("student.updated", data);
    }

    private String sectionPayload(SisSection s) {
        ObjectNode data = mapper.createObjectNode();
        data.put("sectionId", s.sectionId());
        data.put("courseCode", s.courseCode());
        data.put("semesterCode", s.semesterCode());
        data.put("lecturerId", s.lecturerId());
        if (s.capacity() != null) {
            data.put("capacity", s.capacity());
        }
        data.put("status", s.status());
        return wrap("section.updated", data);
    }

    private String enrollmentPayload(String studentId, String sectionId, String status) {
        ObjectNode data = mapper.createObjectNode();
        data.put("studentId", studentId);
        data.put("sectionId", sectionId);
        data.put("status", status);
        return wrap(status.equals("ENROLLED") ? "enrollment.created" : "enrollment.dropped", data);
    }

    private String wrap(String eventType, ObjectNode data) {
        ObjectNode root = mapper.createObjectNode();
        root.put("eventType", eventType);
        root.put("occurredAt", Instant.now().toString());
        root.set("data", data);
        return root.toString();
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    public ReconcileReport getLastReport() {
        return lastReport;
    }
}
