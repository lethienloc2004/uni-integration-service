package vn.thanhdo.integration.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.ApiException;
import vn.thanhdo.integration.client.CourseCatalog;
import vn.thanhdo.integration.client.ErrorClass;
import vn.thanhdo.integration.client.LmsApi;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.client.dto.LmsCourse;
import vn.thanhdo.integration.client.dto.SisSection;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.inbox.InboxEvent;
import vn.thanhdo.integration.mapping.IdMapping;
import vn.thanhdo.integration.mapping.IdMappingService;

import java.util.Optional;

/**
 * HAM HOI TU THU HAI — dong bo Section cua UniSIS thanh Course cua UniLearn.
 *
 * <p>Phuc vu dong thoi <b>INT-02</b> (mo lop moi) va <b>INT-07</b> (doi giang vien
 * phu trach), cong voi buoc doi soat Section sau nay. INT-07 khong can mot dong ma
 * rieng nao: doi lecturerId chi la mot truong hop chenh lech ma ham nay von da xu ly.
 *
 * <p>Bang anh xa — muc 12.2 va 17.2 cua De xuat giai phap v2.0:
 * <pre>
 *   sectionId              -> externalCode        (khoa noi, ghi mot lan)
 *   courseCode + tenHocPhan -> title              ("COURSECODE - Course Name")
 *   semesterCode           -> term                (chi dat duoc luc tao)
 *   lecturerId             -> teacherExternalRef  (day la truong INT-07 cap nhat)
 *   status OPEN/CLOSED     -> state PUBLISHED/ARCHIVED
 * </pre>
 */
@Component
public class EnsureCourseHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EnsureCourseHandler.class);

    private final SisApi sis;
    private final LmsApi lms;
    private final CourseCatalog catalog;
    private final IdMappingService mappings;
    private final IntegrationProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnsureCourseHandler(SisApi sis, LmsApi lms, CourseCatalog catalog,
                               IdMappingService mappings, IntegrationProperties props) {
        this.sis = sis;
        this.lms = lms;
        this.catalog = catalog;
        this.mappings = mappings;
        this.props = props;
    }

    @Override
    public String name() { return "ensureCourse"; }

    @Override
    public String direction() { return "SIS_TO_LMS"; }

    @Override
    public boolean supports(String eventType) {
        return "section.created".equals(eventType) || "section.updated".equals(eventType);
    }

    @Override
    public HandlerResult handle(InboxEvent event) {
        SisSection hint = readHint(event);
        String sectionId = hint != null ? hint.sectionId() : event.getBusinessKey();

        if (sectionId == null || sectionId.isBlank()) {
            throw new ApiException("SIS", 422, "(payload)",
                    "su kien khong co sectionId", ErrorClass.DATA_ERROR, null);
        }

        boolean useHint = hint != null
                && hint.isCompleteEnoughForSync()
                && event.hintIsFresh(props.getDesiredHint().getMaxAgeSeconds());

        return ensureCourse(sectionId, useHint ? hint : null);
    }

    /**
     * Dua LMS ve dung trang thai ma UniSIS dang mo ta cho mot lop hoc phan.
     *
     * @param desiredHint du lieu lay tu payload su kien; {@code null} thi doc lai tu nguon.
     *                    Voi Section, doc lai DAT hon Student vi UniSIS khong co endpoint
     *                    lay mot Section theo ma — phai tai ca danh sach.
     */
    public HandlerResult ensureCourse(String sectionId, SisSection desiredHint) {

        // --- 1. Trang thai MONG MUON ---------------------------------------------
        SisSection desired = desiredHint;
        if (desired == null) {
            desired = sis.getSection(sectionId).orElseThrow(() -> new ApiException(
                    "SIS", 422, "/api/v1/sections",
                    "Section khong ton tai o UniSIS — khong the dong bo",
                    ErrorClass.DATA_ERROR, null));
        } else {
            log.debug("key={} dung payload su kien lam desired", sectionId);
        }

        // Ten hoc phan chi de ghep title. Tra cuu that bai thi rut gon con courseCode
        // (muc 12.5 cho phep) chu khong lam hong ca su kien.
        String title = desired.lmsTitle(catalog.courseName(desired.courseCode()));

        // --- 2. Trang thai THUC TE o he thong dich --------------------------------
        Optional<LmsCourse> actual = lms.findCourseByExternalCode(sectionId);

        // --- 3. Chi lam dung phan chenh lech --------------------------------------
        if (actual.isEmpty()) {
            return createCourse(sectionId, desired, title);
        }
        return convergeExisting(sectionId, desired, title, actual.get(), false);
    }

    private HandlerResult createCourse(String sectionId, SisSection desired, String title) {
        try {
            LmsCourse created = lms.createCourse(desired, title);
            mappings.upsert("SIS", IdMapping.SECTION_COURSE, sectionId, created.id());
            return HandlerResult.created(created.id(),
                    "tao LMS Course moi cho " + sectionId + " (title=" + title + ")");

        } catch (ApiException e) {
            if (e.getStatus() != 409) {
                throw e;
            }
            // 409 COURSE_EXISTS — KHONG phai loi cuoi. Tra lai theo externalCode roi
            // hoi tu tiep (muc 12.5 cua de bai).
            log.info("key={} nhan 409 khi tao Course, tra lai theo externalCode", sectionId);
            LmsCourse existing = lms.findCourseByExternalCode(sectionId).orElseThrow(() -> e);
            return convergeExisting(sectionId, desired, title, existing, false);
        }
    }

    private HandlerResult convergeExisting(String sectionId, SisSection desired, String title,
                                           LmsCourse actual, boolean alreadyRetried) {
        // Lam moi anh xa ke ca khi khong sua gi — co che tu phuc hoi khi mat ban ghi (BV-4)
        mappings.upsert("SIS", IdMapping.SECTION_COURSE, sectionId, actual.id());

        String desiredState = desired.lmsState();
        String desiredTeacher = desired.lecturerId();

        if (!actual.differsFrom(title, desiredState, desiredTeacher)) {
            return HandlerResult.noop(actual.id(),
                    "LMS Course da dung trang thai, khong lam gi");
        }

        boolean teacherChanged = !java.util.Objects.equals(
                nullToEmpty(actual.teacherExternalRef()), nullToEmpty(desiredTeacher));

        LmsCourse updated;
        try {
            // INT-07: PATCH dung Course da co — TUYET DOI khong POST tao Course thu hai
            // chi vi giang vien thay doi. ID noi bo cua Course phai giu nguyen.
            updated = lms.updateCourse(actual.id(), title, desiredState, desiredTeacher);

        } catch (ApiException e) {
            if (e.getStatus() != 404 || alreadyRetried) {
                throw e;
            }
            log.info("key={} PATCH Course tra 404, tra lai theo externalCode", sectionId);
            mappings.deleteFor("SIS", IdMapping.SECTION_COURSE, sectionId);

            Optional<LmsCourse> fresh = lms.findCourseByExternalCode(sectionId);
            if (fresh.isEmpty()) {
                return createCourse(sectionId, desired, title);
            }
            return convergeExisting(sectionId, desired, title, fresh.get(), true);
        }

        String targetId = updated != null && updated.id() != null ? updated.id() : actual.id();
        String detail = teacherChanged
                ? "doi giang vien phu trach " + actual.teacherExternalRef() + " -> " + desiredTeacher
                  + " (INT-07, Course id giu nguyen " + targetId + ")"
                : "cap nhat title/state cho " + sectionId;
        return HandlerResult.updated(targetId, detail);
    }

    /** Doc payload su kien thanh SisSection. Ho tro ca dang boc trong "data". */
    private SisSection readHint(InboxEvent event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(event.getPayload());
            JsonNode data = root.has("data") ? root.get("data") : root;
            return SisSection.from(data);
        } catch (Exception e) {
            log.warn("evt={} khong doc duoc payload: {}", event.getEventId(), e.getMessage());
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
