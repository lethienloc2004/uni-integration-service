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
 * Kiem thu tich hop INT-02 (mo lop) va INT-07 (doi giang vien phu trach).
 *
 * <p>Ca hai yeu cau dung chung mot ham hoi tu {@code ensureCourse}, nen INT-07 khong
 * can mot dong ma rieng nao — no chi la mot truong hop chenh lech ma ham von da xu ly.
 *
 * <p>Cac ca kiem thu: I23 … I30.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:coursedb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
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
class EnsureCourseIntegrationTest {

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
        catalog.invalidate();   // bo nho dem la singleton, phai buoc nap lai moi ca
        stubToken(sisMock);
        stubToken(lmsMock);
        stubCatalog();
    }

    // ==================================================================
    // INT-02 — mo lop hoc phan
    // ==================================================================
    @Test
    @DisplayName("I23 — section.created tao dung mot LMS Course voi day du truong anh xa")
    void createsCourseOnSectionCreated() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .withQueryParam("externalCode", equalTo("SEC-TEAM07-L23"))
                .willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        {"id":"9849","externalCode":"SEC-TEAM07-L23",
                         "title":"INT402 - Kien truc va tich hop he thong","term":"2026-1",
                         "state":"PUBLISHED","teacherExternalRef":"GV0001"}""")));

        deliver("evt_s01", "section.created", payload("SEC-TEAM07-L23", "INT402", "GV0001", "OPEN"));
        worker.drainOnce();

        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/courses")));

        // Bang anh xa muc 12.2 — title PHAI chua courseCode
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/courses"))
                .withRequestBody(matchingJsonPath("$.externalCode", equalTo("SEC-TEAM07-L23")))
                .withRequestBody(matchingJsonPath("$.title",
                        equalTo("INT402 - Kien truc va tich hop he thong")))
                .withRequestBody(matchingJsonPath("$.term", equalTo("2026-1")))
                .withRequestBody(matchingJsonPath("$.teacherExternalRef", equalTo("GV0001")))
                .withRequestBody(matchingJsonPath("$.state", equalTo("PUBLISHED"))));

        InboxEvent e = event("evt_s01");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("CREATE");
        assertThat(mappings.findTargetId("SIS", IdMapping.SECTION_COURSE, "SEC-TEAM07-L23"))
                .contains("9849");
    }

    @Test
    @DisplayName("I24 — section.created gui lap khong tao Course thu hai")
    void duplicateSectionEventCreatesOneCourse() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        {"id":"9849","externalCode":"SEC-TEAM07-L24"}""")));

        String p = payload("SEC-TEAM07-L24", "INT402", "GV0001", "OPEN");
        assertThat(deliver("evt_s02", "section.created", p)).isEqualTo(InboxService.Result.INSERTED);
        assertThat(deliver("evt_s02", "section.created", p)).isEqualTo(InboxService.Result.DUPLICATE);
        worker.drainOnce();

        assertThat(inboxRepo.count()).isEqualTo(1);
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/courses")));
    }

    @Test
    @DisplayName("I25 — Course da ton tai nhung anh xa cuc bo chua co: khong tao trung, tu khoi phuc anh xa")
    void adoptsExistingCourseWithoutLocalMapping() {
        assertThat(mappings.findTargetId("SIS", IdMapping.SECTION_COURSE, "SEC-TEAM07-L25")).isEmpty();

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        [{"id":"9849","externalCode":"SEC-TEAM07-L25",
                          "title":"INT402 - Kien truc va tich hop he thong","term":"2026-1",
                          "state":"PUBLISHED","teacherExternalRef":"GV0001"}]""")));

        deliver("evt_s03", "section.created", payload("SEC-TEAM07-L25", "INT402", "GV0001", "OPEN"));
        worker.drainOnce();

        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/courses")));
        assertThat(event("evt_s03").getLastAction()).isEqualTo("NOOP");
        assertThat(mappings.findTargetId("SIS", IdMapping.SECTION_COURSE, "SEC-TEAM07-L25"))
                .contains("9849");
    }

    @Test
    @DisplayName("I26 — LMS tra 503 khi tao Course: qua RETRYING roi DONE, khong mat su kien")
    void retriesWhenLmsUnavailable() {
        // Tu lan thu lai thu hai, quy tac muc 3.3 buoc doc lai nguon su that
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/sections"))
                .willReturn(okJson("""
                        [{"sectionId":"SEC-TEAM07-L26","courseCode":"INT402",
                          "semesterCode":"2026-1","lecturerId":"GV0001","capacity":60,
                          "status":"OPEN"}]""")));

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("da-on"));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .inScenario("flaky").whenScenarioStateIs("da-on")
                .willReturn(okJson("""
                        {"id":"9849","externalCode":"SEC-TEAM07-L26"}""")));

        deliver("evt_s04", "section.created", payload("SEC-TEAM07-L26", "INT402", "GV0001", "OPEN"));

        worker.drainOnce();
        assertThat(event("evt_s04").getStatus()).isEqualTo(InboxStatus.RETRYING);

        worker.drainOnce();
        InboxEvent done = event("evt_s04");
        assertThat(done.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(done.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("I29 — khong tra duoc ten hoc phan: title rut gon con courseCode, su kien van xong")
    void fallsBackToCourseCodeWhenCourseNameUnknown() {
        // IT999 khong co trong danh muc — dung tinh huong ma muc 12.5 cua de bai du lieu
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        {"id":"9849","externalCode":"SEC-TEAM07-L29"}""")));

        deliver("evt_s05", "section.created", payload("SEC-TEAM07-L29", "IT999", "GV0001", "OPEN"));
        worker.drainOnce();

        // Muc 12.5: title toi thieu van PHAI chua courseCode
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/courses"))
                .withRequestBody(matchingJsonPath("$.title", equalTo("IT999"))));
        assertThat(event("evt_s05").getStatus()).isEqualTo(InboxStatus.DONE);
    }

    @Test
    @DisplayName("I31 — danh muc hoc phan tam loi: dung du lieu dem cu, khong lam hong su kien")
    void survivesCatalogOutageUsingCachedNames() {
        // Nap danh muc mot lan cho vao bo nho dem
        assertThat(catalog.courseName("INT402")).isEqualTo("Kien truc va tich hop he thong");

        // Sau do UniSIS tra 503 cho danh muc va buoc nap lai
        sisMock.resetAll();
        stubToken(sisMock);
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .willReturn(aResponse().withStatus(503)));
        catalog.invalidate();

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        {"id":"9851","externalCode":"SEC-TEAM07-L31"}""")));

        deliver("evt_s09", "section.created", payload("SEC-TEAM07-L31", "INT402", "GV0001", "OPEN"));
        worker.drainOnce();

        // Van ghep duoc title day du nho du lieu dem — bo nho dem la lop chiu loi,
        // khong phai nguon su that
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/courses"))
                .withRequestBody(matchingJsonPath("$.title",
                        equalTo("INT402 - Kien truc va tich hop he thong"))));
        assertThat(event("evt_s09").getStatus()).isEqualTo(InboxStatus.DONE);
    }

    @Test
    @DisplayName("I30 — Section CLOSED thi Course chuyen sang state ARCHIVED")
    void mapsClosedSectionToArchivedCourse() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        {"id":"9850","externalCode":"SEC-TEAM07-LAB09"}""")));

        deliver("evt_s06", "section.updated", payload("SEC-TEAM07-LAB09", "INT402", "GV0001", "CLOSED"));
        worker.drainOnce();

        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/courses"))
                .withRequestBody(matchingJsonPath("$.state", equalTo("ARCHIVED"))));
    }

    // ==================================================================
    // INT-07 — doi giang vien phu trach
    // ==================================================================
    @Test
    @DisplayName("I27 — doi GV0001 sang GV0002: PATCH teacherExternalRef, Course id GIU NGUYEN, khong POST")
    void updatesTeacherWithoutCreatingNewCourse() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .withQueryParam("externalCode", equalTo("SEC-TEAM07-L27"))
                .willReturn(okJson("""
                        [{"id":"9849","externalCode":"SEC-TEAM07-L27",
                          "title":"INT402 - Kien truc va tich hop he thong","term":"2026-1",
                          "state":"PUBLISHED","teacherExternalRef":"GV0001"}]""")));

        lmsMock.stubFor(patch(urlPathEqualTo("/api/v1/courses/9849"))
                .willReturn(okJson("""
                        {"id":"9849","externalCode":"SEC-TEAM07-L27",
                         "teacherExternalRef":"GV0002"}""")));

        deliver("evt_s07", "section.updated", payload("SEC-TEAM07-L27", "INT402", "GV0002", "OPEN"));
        worker.drainOnce();

        // Tieu chi nghiem thu muc 17.6
        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/courses")));
        lmsMock.verify(1, patchRequestedFor(urlPathEqualTo("/api/v1/courses/9849"))
                .withRequestBody(matchingJsonPath("$.teacherExternalRef", equalTo("GV0002"))));

        InboxEvent e = event("evt_s07");
        assertThat(e.getLastAction()).isEqualTo("UPDATE");
        // ID noi bo cua Course khong doi
        assertThat(mappings.findTargetId("SIS", IdMapping.SECTION_COURSE, "SEC-TEAM07-L27"))
                .contains("9849");
    }

    @Test
    @DisplayName("I28 — gui lap cung section.updated: lan hai la NOOP, PATCH cung gia tri van an toan")
    void repeatedSectionUpdateIsNoop() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        [{"id":"9849","externalCode":"SEC-TEAM07-L28",
                          "title":"INT402 - Kien truc va tich hop he thong","term":"2026-1",
                          "state":"PUBLISHED","teacherExternalRef":"GV0002"}]""")));

        deliver("evt_s08", "section.updated", payload("SEC-TEAM07-L28", "INT402", "GV0002", "OPEN"));
        worker.drainOnce();

        lmsMock.verify(0, patchRequestedFor(urlPathMatching("/api/v1/courses/.*")));
        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/courses")));
        assertThat(event("evt_s08").getLastAction()).isEqualTo("NOOP");
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

    /** Payload dung hinh dang that ma UniSIS phat ra cho section.created / section.updated. */
    private static String payload(String sectionId, String courseCode,
                                  String lecturerId, String status) {
        return """
               {"eventId":"x","eventType":"section.created","occurredAt":"%s",
                "data":{"sectionId":"%s","courseCode":"%s","semesterCode":"2026-1",
                        "lecturerId":"%s","capacity":60,"status":"%s"}}
               """.formatted(Instant.now(), sectionId, courseCode, lecturerId, status);
    }

    private static void stubToken(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/token"))
                .willReturn(okJson("""
                        {"accessToken":"tok-test-123","expiresIn":43200}""")));
    }

    /** Danh muc hoc phan cua UniSIS — dung de ghep title. */
    private static void stubCatalog() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        [{"courseCode":"INT402","courseName":"Kien truc va tich hop he thong",
                          "credits":4,"departmentCode":"CNTT","status":"ACTIVE"}]""")));
    }
}
