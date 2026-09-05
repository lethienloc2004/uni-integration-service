package vn.thanhdo.integration.sync;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.thanhdo.integration.client.CourseCatalog;
import vn.thanhdo.integration.inbox.*;
import vn.thanhdo.integration.mapping.IdMapping;
import vn.thanhdo.integration.mapping.IdMappingService;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu tich hop INT-03 (dang ky hoc) va INT-04 (huy dang ky).
 *
 * <p>Ca hai dung chung ham hoi tu {@code ensureMembership}. Diem dac biet cua nhom nay so
 * voi INT-01/02: co PHU THUOC — membership chi ton tai duoc khi da co ca LMS User lan
 * LMS Course. De bai muc 13.5 yeu cau su kien den truoc khi phu thuoc san sang van khong
 * duoc bo.
 *
 * <p>Cac ca kiem thu: I32 … I41.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:memberdb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=validate",
        "integration.tenant-id=TEAM07",
        "integration.worker.enabled=false",
        "integration.poller.enabled=false",
        "integration.reconciler.run-on-startup=false",
        "integration.reconciler.run-on-schedule=false",
        "integration.retry.backoff-seconds=0,0,0,0,0,0",
        "integration.sis.client-secret=test-secret",
        "integration.lms.client-secret=test-secret"
})
class EnsureMembershipIntegrationTest {

    static WireMockServer sisMock;
    static WireMockServer lmsMock;

    @Autowired InboxService inbox;
    @Autowired Worker worker;
    @Autowired InboxRepository inboxRepo;
    @Autowired DeadLetterRepository deadLetterRepo;
    @Autowired IdMappingService mappings;
    @Autowired CourseCatalog catalog;

    @BeforeAll
    static void startMocks() {
        sisMock = new WireMockServer(options().dynamicPort());
        lmsMock = new WireMockServer(options().dynamicPort());
        sisMock.start();
        lmsMock.start();
    }

    @AfterAll
    static void stopMocks() {
        sisMock.stop();
        lmsMock.stop();
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry reg) {
        reg.add("integration.sis.base-url", () -> "http://localhost:" + sisMock.port());
        reg.add("integration.lms.base-url", () -> "http://localhost:" + lmsMock.port());
    }

    @BeforeEach
    void reset() {
        sisMock.resetAll();
        lmsMock.resetAll();
        deadLetterRepo.deleteAll();
        inboxRepo.deleteAll();
        catalog.invalidate();
        stubToken(sisMock);
        stubToken(lmsMock);
    }

    // ==================================================================
    // INT-03 — dang ky hoc
    // ==================================================================
    @Test
    @DisplayName("I32 — enrollment.created them dung mot Membership STUDENT vao dung Course")
    void addsMembershipOnEnrollmentCreated() {
        stubUser("SV32", "101");
        stubCourse("SEC-32", "201");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/201/members")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses/201/members"))
                .willReturn(okJson("""
                        {"membershipId":1,"courseId":201,"userId":101,
                         "role":"STUDENT","status":"ACTIVE"}""")));

        deliver("evt_m01", "enrollment.created", payload("SV32", "SEC-32", "ENROLLED"));
        worker.drainOnce();

        // Bang anh xa muc 13.2 — userId la ID NOI BO cua LMS, role la hang STUDENT
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/courses/201/members"))
                .withRequestBody(matchingJsonPath("$.userId", equalTo("101")))
                .withRequestBody(matchingJsonPath("$.role", equalTo("STUDENT"))));

        InboxEvent e = event("evt_m01");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("I33 — su kien dang ky den khi CHUA CO LMS User: tu goi INT-01 roi them thanh vien")
    void createsMissingUserBeforeAddingMembership() {
        // Chua co User. Trang thai kich ban chi doi SAU KHI POST thanh cong — neu doi ngay
        // o lan GET dau thi luot tra cuu ke tiep se thay User "da co" va POST khong bao gio chay.
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .inScenario("thieu-user").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .inScenario("thieu-user")
                .willReturn(okJson("""
                        {"id":"102","externalRef":"SV33","displayName":"Pham Thi Dung",
                         "emailAddress":"dung@sv.edu.vn","enabled":true}"""))
                .willSetStateTo("da-tao"));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .inScenario("thieu-user").whenScenarioStateIs("da-tao")
                .willReturn(okJson("""
                        [{"id":"102","externalRef":"SV33","displayName":"Pham Thi Dung",
                          "emailAddress":"dung@sv.edu.vn","enabled":true}]""")));

        // UniSIS phai tra ve Student de ensureUser dung lam desired
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/students/SV33"))
                .willReturn(okJson("""
                        {"studentId":"SV33","firstName":"Dung","lastName":"Pham Thi",
                         "email":"dung@sv.edu.vn","status":"ACTIVE"}""")));

        stubCourse("SEC-33", "202");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/202/members")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses/202/members"))
                .willReturn(okJson("{\"membershipId\":2}")));

        deliver("evt_m02", "enrollment.created", payload("SV33", "SEC-33", "ENROLLED"));
        worker.drainOnce();

        // Phu thuoc duoc tu khoi phuc NGAY trong luot xu ly — khong can hang cho rieng
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/users")));
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/courses/202/members")));
        assertThat(event("evt_m02").getStatus()).isEqualTo(InboxStatus.DONE);
    }

    @Test
    @DisplayName("I34 — su kien dang ky den khi CHUA CO LMS Course: tu goi INT-02 roi them thanh vien")
    void createsMissingCourseBeforeAddingMembership() {
        stubUser("SV34", "103");

        // Trang thai kich ban chi doi SAU KHI POST thanh cong
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .inScenario("thieu-course").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .inScenario("thieu-course")
                .willReturn(okJson("""
                        {"id":"203","externalCode":"SEC-34",
                         "title":"INT402 - Kien truc va tich hop he thong","term":"2026-1",
                         "state":"PUBLISHED","teacherExternalRef":"GV0001"}"""))
                .willSetStateTo("da-tao"));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .inScenario("thieu-course").whenScenarioStateIs("da-tao")
                .willReturn(okJson("""
                        [{"id":"203","externalCode":"SEC-34",
                          "title":"INT402 - Kien truc va tich hop he thong","term":"2026-1",
                          "state":"PUBLISHED","teacherExternalRef":"GV0001"}]""")));

        // ensureCourse doc lai Section tu UniSIS vi khong co payload section
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/sections"))
                .willReturn(okJson("""
                        [{"sectionId":"SEC-34","courseCode":"INT402","semesterCode":"2026-1",
                          "lecturerId":"GV0001","capacity":60,"status":"OPEN"}]""")));
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        [{"courseCode":"INT402","courseName":"Kien truc va tich hop he thong"}]""")));

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/203/members")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses/203/members"))
                .willReturn(okJson("{\"membershipId\":3}")));

        deliver("evt_m03", "enrollment.created", payload("SV34", "SEC-34", "ENROLLED"));
        worker.drainOnce();

        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/courses")));
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/courses/203/members")));
        assertThat(event("evt_m03").getStatus()).isEqualTo(InboxStatus.DONE);
    }

    @Test
    @DisplayName("I35 — 409 MEMBERSHIP_EXISTS la trang thai dich da dung, khong thu lai vo han")
    void treatsConflictAsIdempotentSuccess() {
        stubUser("SV35", "104");
        stubCourse("SEC-35", "204");
        // Danh sach bao chua co, nhung POST lai bao da ton tai — dung tinh huong tranh chap
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/204/members")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses/204/members"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"MEMBERSHIP_EXISTS\"}")));

        deliver("evt_m04", "enrollment.created", payload("SV35", "SEC-35", "ENROLLED"));
        worker.drainOnce();

        InboxEvent e = event("evt_m04");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);      // KHONG phai DEAD_LETTER
        assertThat(e.getLastAction()).isEqualTo("NOOP");
        assertThat(e.getAttempt()).isEqualTo(1);                    // khong dot ngan sach thu lai
    }

    @Test
    @DisplayName("I36 — da la thanh vien roi thi khong lam gi (chong trung)")
    void doesNothingWhenAlreadyMember() {
        stubUser("SV36", "105");
        stubCourse("SEC-36", "205");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/205/members"))
                .willReturn(okJson("""
                        [{"membershipId":9,"courseId":205,"userId":105,
                          "role":"STUDENT","status":"ACTIVE"}]""")));

        deliver("evt_m05", "enrollment.created", payload("SV36", "SEC-36", "ENROLLED"));
        worker.drainOnce();

        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/courses/205/members")));
        assertThat(event("evt_m05").getLastAction()).isEqualTo("NOOP");
    }

    @Test
    @DisplayName("I41 — 422 USER_NOT_FOUND khi them: khoi phuc phu thuoc roi thu lai, khong dead-letter")
    void repairsDependencyOn422() {
        stubUser("SV41", "106");
        stubCourse("SEC-41", "206");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/206/members")).willReturn(okJson("[]")));

        // Lan dau 422 (User bien mat), lan hai thanh cong
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses/206/members"))
                .inScenario("thieu-phu-thuoc").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"USER_NOT_FOUND\"}"))
                .willSetStateTo("da-sua"));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses/206/members"))
                .inScenario("thieu-phu-thuoc").whenScenarioStateIs("da-sua")
                .willReturn(okJson("{\"membershipId\":4}")));

        sisMock.stubFor(get(urlPathEqualTo("/api/v1/sections"))
                .willReturn(okJson("""
                        [{"sectionId":"SEC-41","courseCode":"INT402","semesterCode":"2026-1",
                          "lecturerId":"GV0001","capacity":60,"status":"OPEN"}]""")));
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/students/SV41"))
                .willReturn(okJson("""
                        {"studentId":"SV41","firstName":"E","lastName":"Nguyen",
                         "email":"e@sv.edu.vn","status":"ACTIVE"}""")));

        // Buoc khoi phuc goi lai ensureUser va ensureCourse; ca hai thay du lieu dich lech
        // so voi nguon nen se PATCH — day la hanh vi dung, chi can gia lap cho no thanh cong
        lmsMock.stubFor(patch(urlPathEqualTo("/api/v1/users/106"))
                .willReturn(okJson("{\"id\":\"106\",\"externalRef\":\"SV41\"}")));
        lmsMock.stubFor(patch(urlPathEqualTo("/api/v1/courses/206"))
                .willReturn(okJson("{\"id\":\"206\",\"externalCode\":\"SEC-41\"}")));

        deliver("evt_m06", "enrollment.created", payload("SV41", "SEC-41", "ENROLLED"));
        worker.drainOnce();

        InboxEvent e = event("evt_m06");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        lmsMock.verify(2, postRequestedFor(urlPathEqualTo("/api/v1/courses/206/members")));
    }

    // ==================================================================
    // INT-04 — huy dang ky
    // ==================================================================
    @Test
    @DisplayName("I37 — enrollment.dropped go Membership nhung GIU NGUYEN User va Course")
    void removesMembershipButKeepsUserAndCourse() {
        stubUser("SV37", "107");
        stubCourse("SEC-37", "207");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/207/members"))
                .willReturn(okJson("""
                        [{"membershipId":10,"courseId":207,"userId":107,
                          "role":"STUDENT","status":"ACTIVE"}]""")));
        lmsMock.stubFor(delete(urlPathEqualTo("/api/v1/courses/207/members/107"))
                .willReturn(okJson("""
                        {"courseId":207,"userId":107,"role":"STUDENT","status":"REMOVED"}""")));

        deliver("evt_m07", "enrollment.dropped", payload("SV37", "SEC-37", "DROPPED"));
        worker.drainOnce();

        lmsMock.verify(1, deleteRequestedFor(urlPathEqualTo("/api/v1/courses/207/members/107"))
                .withQueryParam("role", equalTo("STUDENT")));

        // Tieu chi nghiem thu 14.6 — KHONG duoc xoa User hay Course
        lmsMock.verify(0, deleteRequestedFor(urlPathMatching("/api/v1/users/.*")));
        lmsMock.verify(0, deleteRequestedFor(urlPathMatching("/api/v1/courses/\\d+$")));

        InboxEvent e = event("evt_m07");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("I38 — gui lap enrollment.dropped: lan hai khong con gi de go, van an toan")
    void repeatedDropIsSafe() {
        stubUser("SV38", "108");
        stubCourse("SEC-38", "208");
        // Da khong con thanh vien nao ACTIVE
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/208/members")).willReturn(okJson("[]")));

        deliver("evt_m08", "enrollment.dropped", payload("SV38", "SEC-38", "DROPPED"));
        worker.drainOnce();

        lmsMock.verify(0, deleteRequestedFor(urlPathMatching("/api/v1/courses/208/members/.*")));
        InboxEvent e = event("evt_m08");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("NOOP");
    }

    @Test
    @DisplayName("I40 — DELETE tra 404 MEMBERSHIP_NOT_FOUND: trang thai cuoi da dung, khong thu lai vo han")
    void notFoundOnDeleteIsIdempotentSuccess() {
        stubUser("SV40", "109");
        stubCourse("SEC-40", "209");
        // Danh sach bao con ACTIVE nhung DELETE lai bao khong tim thay — tranh chap
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/209/members"))
                .willReturn(okJson("""
                        [{"membershipId":11,"courseId":209,"userId":109,
                          "role":"STUDENT","status":"ACTIVE"}]""")));
        lmsMock.stubFor(delete(urlPathEqualTo("/api/v1/courses/209/members/109"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"MEMBERSHIP_NOT_FOUND\"}")));

        deliver("evt_m09", "enrollment.dropped", payload("SV40", "SEC-40", "DROPPED"));
        worker.drainOnce();

        InboxEvent e = event("evt_m09");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);   // KHONG phai DEAD_LETTER
        assertThat(e.getLastAction()).isEqualTo("NOOP");
        assertThat(e.getAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("I39 — mat anh xa cuc bo nhung User/Course con tren LMS: tra lai duoc va van go dung")
    void recoversLostMappingBeforeRemoving() {
        mappings.deleteFor("SIS", IdMapping.STUDENT_USER, "SV39");
        mappings.deleteFor("SIS", IdMapping.SECTION_COURSE, "SEC-39");

        // Khong co anh xa — phai tra lai bang khoa nghiep vu (bat bien BV-4)
        stubUser("SV39", "110");
        stubCourse("SEC-39", "210");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/210/members"))
                .willReturn(okJson("""
                        [{"membershipId":12,"courseId":210,"userId":110,
                          "role":"STUDENT","status":"ACTIVE"}]""")));
        lmsMock.stubFor(delete(urlPathEqualTo("/api/v1/courses/210/members/110"))
                .willReturn(okJson("{\"status\":\"REMOVED\"}")));

        deliver("evt_m10", "enrollment.dropped", payload("SV39", "SEC-39", "DROPPED"));
        worker.drainOnce();

        lmsMock.verify(1, deleteRequestedFor(urlPathEqualTo("/api/v1/courses/210/members/110")));
        assertThat(event("evt_m10").getLastAction()).isEqualTo("DELETE");

        // Anh xa duoc khoi phuc trong qua trinh xu ly
        assertThat(mappings.findTargetId("SIS", IdMapping.STUDENT_USER, "SV39")).contains("110");
        assertThat(mappings.findTargetId("SIS", IdMapping.SECTION_COURSE, "SEC-39")).contains("210");
    }

    @Test
    @DisplayName("Muc 3.3 — su kien cu thi doc lai UniSIS de biet trang thai dang ky hien hanh")
    void staleEventRereadsEnrollmentStatus() {
        stubUser("SV42", "111");
        stubCourse("SEC-42", "211");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses/211/members"))
                .willReturn(okJson("""
                        [{"membershipId":13,"courseId":211,"userId":111,
                          "role":"STUDENT","status":"ACTIVE"}]""")));
        lmsMock.stubFor(delete(urlPathEqualTo("/api/v1/courses/211/members/111"))
                .willReturn(okJson("{\"status\":\"REMOVED\"}")));

        // UniSIS noi dang ky nay DA BI HUY, du su kien la enrollment.created
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/enrollments"))
                .willReturn(okJson("""
                        [{"enrollmentId":"ENR-1","studentId":"SV42","sectionId":"SEC-42",
                          "status":"DROPPED"}]""")));

        // Su kien qua cu -> khong duoc tin loai su kien nua
        String stale = """
               {"eventId":"x","eventType":"enrollment.created","occurredAt":"2020-01-01T00:00:00Z",
                "data":{"enrollmentId":"ENR-1","studentId":"SV42","sectionId":"SEC-42",
                        "status":"ENROLLED"}}
               """;
        inbox.record(InboxService.Channel.POLLER, "SIS", "evt_m11", "enrollment.created",
                stale, Instant.parse("2020-01-01T00:00:00Z"));
        worker.drainOnce();

        // Trang thai hien hanh thang trang thai trong su kien cu
        lmsMock.verify(1, deleteRequestedFor(urlPathEqualTo("/api/v1/courses/211/members/111")));
        assertThat(event("evt_m11").getLastAction()).isEqualTo("DELETE");
    }

    // ------------------------------------------------------------------
    // Tien ich
    // ------------------------------------------------------------------

    private InboxService.Result deliver(String eventId, String eventType, String payload) {
        return inbox.record(InboxService.Channel.WEBHOOK, "SIS", eventId, eventType,
                payload, Instant.now());
    }

    private InboxEvent event(String eventId) {
        return inboxRepo.findBySourceSystemAndEventId("SIS", eventId).orElseThrow();
    }

    private static void stubUser(String externalRef, String lmsId) {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("externalRef", equalTo(externalRef))
                .willReturn(okJson("""
                        [{"id":"%s","externalRef":"%s","displayName":"X","emailAddress":"x@sv.edu.vn",
                          "enabled":true}]""".formatted(lmsId, externalRef))));
    }

    private static void stubCourse(String externalCode, String lmsId) {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .withQueryParam("externalCode", equalTo(externalCode))
                .willReturn(okJson("""
                        [{"id":"%s","externalCode":"%s","title":"T","term":"2026-1",
                          "state":"PUBLISHED","teacherExternalRef":"GV0001"}]"""
                        .formatted(lmsId, externalCode))));
    }

    /** Hinh dang that ma UniSIS phat cho enrollment.created / enrollment.dropped. */
    private static String payload(String studentId, String sectionId, String status) {
        return """
               {"eventId":"x","eventType":"enrollment.created","occurredAt":"%s",
                "data":{"enrollmentId":"ENR-TEST","studentId":"%s","sectionId":"%s","status":"%s"}}
               """.formatted(Instant.now(), studentId, sectionId, status);
    }

    private static void stubToken(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/token"))
                .willReturn(okJson("""
                        {"accessToken":"tok-test-123","expiresIn":43200}""")));
    }
}
