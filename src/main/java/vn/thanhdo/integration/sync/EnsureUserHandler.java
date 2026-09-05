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
import vn.thanhdo.integration.client.dto.LmsUser;
import vn.thanhdo.integration.client.dto.SisStudent;
import vn.thanhdo.integration.config.IntegrationProperties;
import vn.thanhdo.integration.inbox.InboxEvent;
import vn.thanhdo.integration.mapping.IdMapping;
import vn.thanhdo.integration.mapping.IdMappingService;

import java.util.Optional;

/**
 * HAM HOI TU THU NHAT — dong bo Student cua UniSIS thanh User cua UniLearn.
 *
 * <p>Phuc vu dong thoi <b>INT-01</b> (tao/cap nhat danh tinh) va <b>INT-05</b>
 * (dong bo trang thai sang enabled), cong voi buoc doi soat Student sau nay.
 * Ba yeu cau dung chung MOT ham vi ca ba deu chi la cau hoi "LMS User cua sinh vien
 * nay dang dung chua".
 *
 * <p>Bang anh xa: muc 4.3 cua De xuat giai phap v2.0.
 * <pre>
 *   studentId                  -> username      (giu nguyen, ghi mot lan)
 *   studentId                  -> externalRef   (khoa noi, ghi mot lan)
 *   lastName + " " + firstName -> displayName
 *   email                      -> emailAddress
 *   "LEARNER"                  -> userType
 *   status == ACTIVE           -> enabled
 * </pre>
 */
@Component
public class EnsureUserHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EnsureUserHandler.class);

    private final SisApi sis;
    private final LmsApi lms;
    private final IdMappingService mappings;
    private final IntegrationProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnsureUserHandler(SisApi sis, LmsApi lms, IdMappingService mappings,
                             IntegrationProperties props) {
        this.sis = sis;
        this.lms = lms;
        this.mappings = mappings;
        this.props = props;
    }

    @Override
    public String name() { return "ensureUser"; }

    @Override
    public String direction() { return "SIS_TO_LMS"; }

    @Override
    public boolean supports(String eventType) {
        return "student.created".equals(eventType) || "student.updated".equals(eventType);
    }

    @Override
    public HandlerResult handle(InboxEvent event) {
        SisStudent hint = readHint(event);
        String studentId = hint != null ? hint.studentId() : event.getBusinessKey();

        if (studentId == null || studentId.isBlank()) {
            throw new ApiException("SIS", 422, "(payload)",
                    "su kien khong co studentId", ErrorClass.DATA_ERROR, null);
        }

        boolean useHint = hint != null
                && hint.isCompleteEnoughForSync()
                && event.hintIsFresh(props.getDesiredHint().getMaxAgeSeconds());

        return ensureUser(studentId, useHint ? hint : null);
    }

    /**
     * Dua LMS ve dung trang thai ma UniSIS dang mo ta.
     *
     * <p>Ba buoc co dinh: xac dinh trang thai MONG MUON, doc trang thai THUC TE,
     * roi chi lam dung phan chenh lech. Goi lai lan thu hai se roi vao nhanh NOOP.
     *
     * @param desiredHint du lieu lay tu payload su kien; {@code null} thi doc lai
     *                    tu nguon su that. Quy tac chon: muc 3.3.
     */
    public HandlerResult ensureUser(String studentId, SisStudent desiredHint) {

        // --- 1. Trang thai MONG MUON, luon quy chieu ve nguon su that -------------
        SisStudent desired = desiredHint;
        if (desired == null) {
            desired = sis.getStudent(studentId).orElseThrow(() -> new ApiException(
                    "SIS", 422, "/api/v1/students/" + studentId,
                    "Student khong ton tai o UniSIS — khong the dong bo",
                    ErrorClass.DATA_ERROR, null));
        } else {
            log.debug("key={} dung payload su kien lam desired, bo qua mot luot GET", studentId);
        }

        // --- 2. Trang thai THUC TE o he thong dich --------------------------------
        Optional<LmsUser> actual = lms.findUserByExternalRef(studentId);

        // --- 3. Chi lam dung phan chenh lech --------------------------------------
        if (actual.isEmpty()) {
            return createUser(studentId, desired);
        }
        return convergeExisting(studentId, desired, actual.get());
    }

    private HandlerResult createUser(String studentId, SisStudent desired) {
        try {
            LmsUser created = lms.createUser(desired);
            mappings.upsert("SIS", IdMapping.STUDENT_USER, studentId, created.id());
            return HandlerResult.created(created.id(),
                    "tao LMS User moi cho " + studentId);

        } catch (ApiException e) {
            if (e.getStatus() != 409) {
                throw e;
            }
            // 409 USER_EXISTS — KHONG phai loi. Hai su kien cua cung mot sinh vien
            // toi gan nhu dong thoi, hoac mot luot truoc da tao xong nhung ta chua
            // thay. Tra lai theo khoa nghiep vu roi hoi tu tiep (hop dong loi 4.4).
            log.info("key={} nhan 409 khi tao User, tra lai theo externalRef", studentId);
            LmsUser existing = lms.findUserByExternalRef(studentId).orElseThrow(() -> e);
            return convergeExisting(studentId, desired, existing);
        }
    }

    private HandlerResult convergeExisting(String studentId, SisStudent desired, LmsUser actual) {
        return convergeExisting(studentId, desired, actual, false);
    }

    private HandlerResult convergeExisting(String studentId, SisStudent desired,
                                           LmsUser actual, boolean alreadyRetried) {
        // Lam moi anh xa ngay ca khi khong phai sua gi: day chinh la co che
        // TU PHUC HOI khi bang anh xa mat ban ghi ma du lieu dich van con (BV-4).
        mappings.upsert("SIS", IdMapping.STUDENT_USER, studentId, actual.id());

        if (!actual.differsFrom(desired)) {
            return HandlerResult.noop(actual.id(),
                    "LMS User da dung trang thai, khong lam gi");
        }

        LmsUser updated;
        try {
            updated = lms.updateUser(actual.id(), desired);

        } catch (ApiException e) {
            if (e.getStatus() != 404 || alreadyRetried) {
                throw e;
            }
            // 404 khi PATCH — muc 11.5 cua de bai. User bien mat giua luc tra cuu va luc ghi,
            // hoac id dang dung da cu. Tra lai theo KHOA NGHIEP VU; neu that su khong con thi
            // tao moi. Co chan mot vong de khong lap vo han.
            log.info("key={} PATCH tra 404, tra lai theo externalRef", studentId);
            mappings.deleteFor("SIS", IdMapping.STUDENT_USER, studentId);

            Optional<LmsUser> fresh = lms.findUserByExternalRef(studentId);
            if (fresh.isEmpty()) {
                return createUser(studentId, desired);
            }
            return convergeExisting(studentId, desired, fresh.get(), true);
        }

        String targetId = updated != null && updated.id() != null ? updated.id() : actual.id();
        return HandlerResult.updated(targetId,
                "cap nhat displayName/email/enabled cho " + studentId
                        + " (enabled=" + desired.enabled() + ")");
    }

    /** Doc payload su kien thanh SisStudent. Ho tro ca dang boc trong "data". */
    private SisStudent readHint(InboxEvent event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(event.getPayload());
            JsonNode data = root.has("data") ? root.get("data") : root;
            return SisStudent.from(data);
        } catch (Exception e) {
            log.warn("evt={} khong doc duoc payload: {}", event.getEventId(), e.getMessage());
            return null;
        }
    }
}
