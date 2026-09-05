package vn.thanhdo.integration.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.ApiException;
import vn.thanhdo.integration.client.ErrorClass;
import vn.thanhdo.integration.client.LmsApi;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.client.dto.LmsCourse;
import vn.thanhdo.integration.client.dto.LmsUser;
import vn.thanhdo.integration.client.dto.SisEnrollment;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.inbox.InboxEvent;
import vn.thanhdo.integration.mapping.IdMapping;
import vn.thanhdo.integration.mapping.IdMappingService;

import java.util.List;
import java.util.Optional;

/**
 * HAM HOI TU THU BA — dong bo Enrollment cua UniSIS thanh Membership cua UniLearn.
 *
 * <p>Phuc vu dong thoi <b>INT-03</b> (dang ky hoc) va <b>INT-04</b> (huy dang ky), cong voi
 * buoc doi soat Enrollment sau nay. Ca hai chi la hai gia tri khac nhau cua cung mot cau hoi:
 * "cap (sinh vien, lop) nay co nen la thanh vien cua Course hay khong".
 *
 * <p><b>Diem khac biet so voi hai ham truoc:</b> ham nay co PHU THUOC. Membership chi ton tai
 * duoc khi da co LMS User va LMS Course. De bai (muc 13.5) yeu cau xu ly duoc tinh huong
 * su kien dang ky den TRUOC khi hai thu do kip tao. Cach giai quyet o day la tu khoi phuc
 * phu thuoc ngay trong luot xu ly, khong can hang cho phu thuoc rieng.
 *
 * <p>Bang anh xa — muc 4.3:
 * <pre>
 *   studentId  -> userId    (phan giai qua externalRef)
 *   sectionId  -> courseId  (phan giai qua externalCode)
 *   (hang)     -> role = STUDENT
 *   con hieu luc o SIS  -> co mat trong danh sach thanh vien ACTIVE
 * </pre>
 */
@Component
public class EnsureMembershipHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EnsureMembershipHandler.class);

    /** Vai tro duy nhat trong pham vi bai — de bai muc 13.2. */
    private static final String ROLE_STUDENT = "STUDENT";

    private final SisApi sis;
    private final LmsApi lms;
    private final EnsureUserHandler ensureUser;
    private final EnsureCourseHandler ensureCourse;
    private final IdMappingService mappings;
    private final IntegrationProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnsureMembershipHandler(SisApi sis, LmsApi lms,
                                   EnsureUserHandler ensureUser, EnsureCourseHandler ensureCourse,
                                   IdMappingService mappings, IntegrationProperties props) {
        this.sis = sis;
        this.lms = lms;
        this.ensureUser = ensureUser;
        this.ensureCourse = ensureCourse;
        this.mappings = mappings;
        this.props = props;
    }

    @Override
    public String name() { return "ensureMembership"; }

    @Override
    public String direction() { return "SIS_TO_LMS"; }

    @Override
    public boolean supports(String eventType) {
        return "enrollment.created".equals(eventType) || "enrollment.dropped".equals(eventType);
    }

    @Override
    public HandlerResult handle(InboxEvent event) {
        SisEnrollment hint = readHint(event);

        String studentId = hint != null ? hint.studentId() : keyPart(event, 0);
        String sectionId = hint != null ? hint.sectionId() : keyPart(event, 1);

        if (studentId == null || sectionId == null) {
            throw new ApiException("SIS", 422, "(payload)",
                    "su kien khong co studentId hoac sectionId", ErrorClass.DATA_ERROR, null);
        }

        // --- Trang thai MONG MUON -------------------------------------------------
        // Su kien con moi thi tin loai su kien; nguoc lai phai doc lai UniSIS, vi mot
        // enrollment.created cu co the da bi lan huy moi hon lam lac hau (muc 3.3).
        boolean desiredActive;
        if (hint != null && hint.isCompleteEnoughForSync()
                && event.hintIsFresh(props.getDesiredHint().getMaxAgeSeconds())) {
            desiredActive = "enrollment.created".equals(event.getEventType());
        } else {
            desiredActive = sis.findEnrollment(studentId, sectionId)
                    .map(SisEnrollment::isActive)
                    .orElse(false);   // khong con ban ghi nao -> chac chan khong con hieu luc
            log.debug("key={}|{} doc lai UniSIS: desiredActive={}", studentId, sectionId, desiredActive);
        }

        return ensureMembership(studentId, sectionId, desiredActive);
    }

    /**
     * Dua Membership ben LMS ve dung trang thai ma UniSIS dang mo ta.
     *
     * @param desiredActive {@code true} = phai la thanh vien ACTIVE (INT-03);
     *                      {@code false} = phai khong con la thanh vien (INT-04)
     */
    public HandlerResult ensureMembership(String studentId, String sectionId, boolean desiredActive) {

        // --- Phan giai phu thuoc ---------------------------------------------------
        // Khi can go thanh vien ma Course con chua ton tai thi khong co gi de go: trang
        // thai cuoi da dung roi. Tranh tao Course chi de roi go thanh vien khoi no.
        Optional<String> courseIdOpt = resolveCourseId(sectionId, desiredActive);
        if (courseIdOpt.isEmpty()) {
            return HandlerResult.noop(null,
                    "chua co LMS Course cho " + sectionId + " nen khong co membership de go");
        }
        String courseId = courseIdOpt.get();

        Optional<String> userIdOpt = resolveUserId(studentId, desiredActive);
        if (userIdOpt.isEmpty()) {
            return HandlerResult.noop(null,
                    "chua co LMS User cho " + studentId + " nen khong co membership de go");
        }
        String userId = userIdOpt.get();

        return applyMembership(studentId, sectionId, courseId, userId, desiredActive, false);
    }

    private HandlerResult applyMembership(String studentId, String sectionId,
                                          String courseId, String userId,
                                          boolean desiredActive, boolean alreadyRepaired) {
        // --- Trang thai THUC TE ---------------------------------------------------
        // Endpoint nay chi tra ve thanh vien ACTIVE, nen "co mat" == "dang ACTIVE".
        List<String> activeMembers = lms.activeMemberUserIds(courseId);
        boolean actuallyActive = activeMembers.contains(userId);

        // --- Chi lam dung phan chenh lech -----------------------------------------
        if (actuallyActive == desiredActive) {
            return HandlerResult.noop(courseId + "/" + userId,
                    desiredActive
                            ? "da la thanh vien ACTIVE, khong lam gi"
                            : "khong con la thanh vien, khong lam gi");
        }

        try {
            if (desiredActive) {
                lms.addMember(courseId, userId, ROLE_STUDENT);
                return HandlerResult.created(courseId + "/" + userId,
                        "them " + studentId + " vao " + sectionId
                                + " (courseId=" + courseId + ", userId=" + userId + ")");
            }
            lms.removeMember(courseId, userId, ROLE_STUDENT);
            return HandlerResult.deleted(courseId + "/" + userId,
                    "go " + studentId + " khoi " + sectionId
                            + " — User va Course GIU NGUYEN");

        } catch (ApiException e) {
            return recoverFromMembershipError(e, studentId, sectionId, courseId, userId,
                    desiredActive, alreadyRepaired);
        }
    }

    /**
     * Xu ly cac ma loi cua thao tac membership.
     *
     * <p>Diem dang luu: he thong dich tra 404 khi thieu Course nhung 422 khi thieu User —
     * BAT DOI XUNG. Neu de nguyen theo bang phan loai chung thi 404 va 422 deu roi vao
     * dead-letter, trong khi ca hai o day thuc chat la THIEU PHU THUOC co the tu khoi phuc.
     */
    private HandlerResult recoverFromMembershipError(ApiException e,
                                                     String studentId, String sectionId,
                                                     String courseId, String userId,
                                                     boolean desiredActive, boolean alreadyRepaired) {

        // 409 MEMBERSHIP_EXISTS — khong phai loi: trang thai dich da dung roi
        if (e.getStatus() == 409) {
            return HandlerResult.noop(courseId + "/" + userId,
                    "membership da ton tai (409), trang thai cuoi da dung");
        }

        // 404 khi GO — khong con membership de go, trang thai cuoi da dung (xoa idempotent)
        if (e.getStatus() == 404 && !desiredActive) {
            return HandlerResult.noop(courseId + "/" + userId,
                    "khong con membership de go (404), trang thai cuoi da dung");
        }

        // 404 COURSE_NOT_FOUND hoac 422 USER_NOT_FOUND khi THEM — thieu phu thuoc
        if ((e.getStatus() == 404 || e.getStatus() == 422) && desiredActive) {
            if (alreadyRepaired) {
                // Da khoi phuc mot lan ma van thieu — khong tu chua duoc nua
                throw e.reclassify(ErrorClass.DEPENDENCY_MISSING);
            }
            log.info("key={}|{} thieu phu thuoc (HTTP {}), khoi phuc roi thu lai",
                    studentId, sectionId, e.getStatus());

            // Anh xa co the da cu — xoa roi dung ham hoi tu tao lai tu dau
            mappings.deleteFor("SIS", IdMapping.SECTION_COURSE, sectionId);
            mappings.deleteFor("SIS", IdMapping.STUDENT_USER, studentId);

            String freshCourseId = ensureCourse.ensureCourse(sectionId, null).targetId();
            String freshUserId = ensureUser.ensureUser(studentId, null).targetId();

            if (freshCourseId == null || freshUserId == null) {
                throw e.reclassify(ErrorClass.DEPENDENCY_MISSING);
            }
            return applyMembership(studentId, sectionId, freshCourseId, freshUserId,
                    desiredActive, true);
        }

        throw e;
    }

    // ------------------------------------------------------------------
    // Phan giai ID — anh xa chi la bo nho dem, khoa nghiep vu moi la nguon phuc hoi (BV-4)
    // ------------------------------------------------------------------

    /**
     * @param createIfMissing {@code true} khi can them thanh vien — thieu thi tao.
     *                        {@code false} khi can go — thieu thi khoi tao lam gi.
     */
    private Optional<String> resolveCourseId(String sectionId, boolean createIfMissing) {
        Optional<String> cached = mappings.findTargetId("SIS", IdMapping.SECTION_COURSE, sectionId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<LmsCourse> found = lms.findCourseByExternalCode(sectionId);
        if (found.isPresent()) {
            mappings.upsert("SIS", IdMapping.SECTION_COURSE, sectionId, found.get().id());
            return Optional.of(found.get().id());
        }
        if (!createIfMissing) {
            return Optional.empty();
        }
        // INT-02 duoc goi lai ngay tai day — day chinh la co che tu khoi phuc phu thuoc
        log.info("key={} chua co LMS Course, goi ensureCourse truoc khi them thanh vien", sectionId);
        return Optional.ofNullable(ensureCourse.ensureCourse(sectionId, null).targetId());
    }

    private Optional<String> resolveUserId(String studentId, boolean createIfMissing) {
        Optional<String> cached = mappings.findTargetId("SIS", IdMapping.STUDENT_USER, studentId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<LmsUser> found = lms.findUserByExternalRef(studentId);
        if (found.isPresent()) {
            mappings.upsert("SIS", IdMapping.STUDENT_USER, studentId, found.get().id());
            return Optional.of(found.get().id());
        }
        if (!createIfMissing) {
            return Optional.empty();
        }
        log.info("key={} chua co LMS User, goi ensureUser truoc khi them thanh vien", studentId);
        return Optional.ofNullable(ensureUser.ensureUser(studentId, null).targetId());
    }

    // ------------------------------------------------------------------

    private SisEnrollment readHint(InboxEvent event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(event.getPayload());
            JsonNode data = root.has("data") ? root.get("data") : root;
            return SisEnrollment.from(data);
        } catch (Exception e) {
            log.warn("evt={} khong doc duoc payload: {}", event.getEventId(), e.getMessage());
            return null;
        }
    }

    /** businessKey cua enrollment co dang "studentId|sectionId". */
    private static String keyPart(InboxEvent event, int index) {
        String key = event.getBusinessKey();
        if (key == null) {
            return null;
        }
        String[] parts = key.split("\\|", 2);
        return index < parts.length && !parts[index].isBlank() ? parts[index] : null;
    }
}
