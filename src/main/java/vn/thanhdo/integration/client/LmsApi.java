package vn.thanhdo.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import vn.thanhdo.integration.client.dto.LmsCourse;
import vn.thanhdo.integration.client.dto.LmsUser;
import vn.thanhdo.integration.client.dto.SisSection;
import vn.thanhdo.integration.client.dto.SisStudent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cac loi goi nghiep vu toi UniLearn LMS (Phu luc A cua de bai). */
@Component
public class LmsApi {

    private final ApiClient client;

    public LmsApi(@Qualifier("lmsClient") ApiClient lmsClient) {
        this.client = lmsClient;
    }

    public ApiClient raw() {
        return client;
    }

    /**
     * GET /api/v1/users?externalRef={studentId} — tra cuu theo KHOA NOI THAT.
     *
     * <p>Day la co che phuc hoi khi bang anh xa mat ban ghi (bat bien BV-4):
     * anh xa chi la bo nho dem, con externalRef thi luon tra lai duoc.
     */
    public Optional<LmsUser> findUserByExternalRef(String externalRef) {
        return client.getFirst("/api/v1/users?externalRef={ref}", externalRef)
                .map(LmsUser::from);
    }

    /** GET /api/v1/users — tai toan bo, phuc vu doi soat theo tap hop. */
    public List<LmsUser> listUsers() {
        return client.getList("/api/v1/users").stream()
                .map(LmsUser::from)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** POST /api/v1/users — tao User theo bang anh xa INT-01 (muc 4.3). */
    public LmsUser createUser(SisStudent s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", s.studentId());        // giu nguyen ma nghiep vu
        body.put("externalRef", s.studentId());     // khoa noi giua hai he thong
        body.put("displayName", s.displayName());
        body.put("emailAddress", s.email());
        body.put("userType", "LEARNER");
        body.put("enabled", s.enabled());
        return LmsUser.from(client.post("/api/v1/users", body));
    }

    /**
     * PATCH /api/v1/users/{id} — chi cap nhat ba truong do UniSIS lam chu.
     * username va externalRef KHONG BAO GIO duoc sua: chung la khoa noi.
     */
    public LmsUser updateUser(String lmsUserId, SisStudent s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", s.displayName());
        body.put("emailAddress", s.email());
        body.put("enabled", s.enabled());
        return LmsUser.from(client.patch("/api/v1/users/{id}", body, lmsUserId));
    }

    // ------------------------------------------------------------------
    // Course — INT-02 va INT-07
    // ------------------------------------------------------------------

    /** GET /api/v1/courses?externalCode={sectionId} — tra cuu theo KHOA NOI THAT. */
    public Optional<LmsCourse> findCourseByExternalCode(String externalCode) {
        return client.getFirst("/api/v1/courses?externalCode={code}", externalCode)
                .map(LmsCourse::from);
    }

    /** GET /api/v1/courses — tai toan bo, phuc vu doi soat theo tap hop. */
    public List<LmsCourse> listCourses() {
        return client.getList("/api/v1/courses").stream()
                .map(LmsCourse::from)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** POST /api/v1/courses — tao Course theo bang anh xa INT-02 (muc 12.2). */
    public LmsCourse createCourse(SisSection s, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalCode", s.sectionId());     // khoa noi giua hai he thong
        body.put("title", title);
        body.put("term", s.semesterCode());
        body.put("state", s.lmsState());
        body.put("teacherExternalRef", s.lecturerId());
        return LmsCourse.from(client.post("/api/v1/courses", body));
    }

    /**
     * PATCH /api/v1/courses/{id} — day la loi goi cua INT-07.
     *
     * <p>Chi gui ba truong ma {@code CoursePatch} ben LMS chap nhan. {@code term} khong
     * nam trong so do nen hoc ky khong the sua sau khi tao; {@code externalCode} la khoa
     * noi nen tuyet doi khong sua.
     */
    public LmsCourse updateCourse(String lmsCourseId, String title, String state, String teacherRef) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("state", state);
        body.put("teacherExternalRef", teacherRef);
        return LmsCourse.from(client.patch("/api/v1/courses/{id}", body, lmsCourseId));
    }

    // ------------------------------------------------------------------
    // Membership — INT-03 va INT-04
    // ------------------------------------------------------------------

    /**
     * GET /api/v1/courses/{courseId}/members — CHI tra ve membership dang ACTIVE.
     *
     * <p>Vi vay "co mat trong danh sach" dong nghia voi "dang ACTIVE"; khong can doc them
     * truong status de biet trang thai thuc te.
     */
    public List<String> activeMemberUserIds(String courseId) {
        return client.getList("/api/v1/courses/{id}/members", courseId).stream()
                .filter(n -> n.hasNonNull("userId"))
                .map(n -> n.get("userId").asText())
                .toList();
    }

    /** GET /api/v1/memberships — toan bo membership ACTIVE cua tenant, phuc vu doi soat. */
    public List<JsonNode> listAllActiveMemberships() {
        return client.getList("/api/v1/memberships");
    }

    /**
     * POST /api/v1/courses/{courseId}/members — INT-03.
     *
     * <p>Ma loi cua he thong dich BAT DOI XUNG, can luu y khi phan loai:
     * <ul>
     *   <li><b>404</b> COURSE_NOT_FOUND — thieu Course</li>
     *   <li><b>422</b> USER_NOT_FOUND — thieu User</li>
     *   <li><b>409</b> MEMBERSHIP_EXISTS — da co va dang ACTIVE</li>
     * </ul>
     * Ca 404 lan 422 o day deu la THIEU PHU THUOC ma Integration Service co quyen tao,
     * nen phai khoi phuc roi thu lai chu khong duoc day vao dead-letter.
     */
    public void addMember(String courseId, String userId, String role) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", Integer.parseInt(userId));   // MemberIn.userId la so nguyen
        body.put("role", role);
        client.post("/api/v1/courses/{id}/members", body, courseId);
    }

    /**
     * DELETE /api/v1/courses/{courseId}/members/{userId}?role=... — INT-04.
     *
     * <p>Chi go MEMBERSHIP. Tuyet doi khong xoa User hay Course: de bai yeu cau ro
     * "Membership khong con ACTIVE; khong xoa User/Course".
     *
     * <p>Ben LMS thao tac nay dat {@code status = REMOVED} chu khong xoa ban ghi, nen goi
     * lai lan hai se nhan 404 MEMBERSHIP_NOT_FOUND — luc do trang thai cuoi da dung roi.
     */
    public void removeMember(String courseId, String userId, String role) {
        client.delete("/api/v1/courses/{cid}/members/{uid}?role={role}", courseId, userId, role);
    }

    /** GET /api/v1/events?since=... — phia LMS phat grade.published va learner.at_risk. */
    public List<JsonNode> getEvents(Instant since) {
        return since == null
                ? client.getList("/api/v1/events")
                : client.getList("/api/v1/events?since={since}", since.toString());
    }
}
