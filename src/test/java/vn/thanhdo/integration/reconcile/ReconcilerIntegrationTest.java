package vn.thanhdo.integration.reconcile;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.thanhdo.integration.client.CourseCatalog;
import vn.thanhdo.integration.inbox.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu bo doi soat — NFR-06 va kich ban cham T11.
 *
 * <p>Cac ca kiem thu: I55 … I61.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recondb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
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
class ReconcilerIntegrationTest {

    static WireMockServer sisMock;
    static WireMockServer lmsMock;

    @Autowired Reconciler reconciler;
    @Autowired Worker worker;
    @Autowired InboxRepository inboxRepo;
    @Autowired DeadLetterRepository deadLetterRepo;
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
    @Test
    @DisplayName("I55 — phat hien Student co o SIS ma thieu o LMS, sinh su kien tong hop de sua")
    void detectsMissingStudentAndEnqueuesRepair() {
        stubBaseline(
                /* students */ """
                        [{"studentId":"SV001","firstName":"A","lastName":"Nguyen",
                          "email":"a@sv.edu.vn","status":"ACTIVE"},
                         {"studentId":"SV002","firstName":"B","lastName":"Tran",
                          "email":"b@sv.edu.vn","status":"ACTIVE"}]""",
                /* users */ """
                        [{"id":"1","externalRef":"SV001","displayName":"Nguyen A",
                          "emailAddress":"a@sv.edu.vn","enabled":true}]""");

        ReconcileReport r = reconciler.reconcile(false);

        ReconcileReport.EntityStat stat = r.getEntities().get("STUDENT_USER");
        assertThat(stat.sourceCount).isEqualTo(2);
        assertThat(stat.targetCount).isEqualTo(1);
        assertThat(stat.missing).isEqualTo(1);
        assertThat(stat.enqueued).isEqualTo(1);
        assertThat(stat.samples).contains("SV002");

        // Su kien tong hop nam trong CUNG bang inbox, nguon RECON
        InboxEvent e = inboxRepo.findAll().stream()
                .filter(x -> "RECON".equals(x.getSourceSystem()))
                .findFirst().orElseThrow();
        assertThat(e.getEventId()).startsWith("recon:");
        assertThat(e.getBusinessKey()).isEqualTo("SV002");
    }

    @Test
    @DisplayName("I56 — che do dryRun chi bao cao, TUYET DOI khong ghi gi")
    void dryRunReportsWithoutWriting() {
        stubBaseline("""
                [{"studentId":"SV001","firstName":"A","lastName":"Nguyen",
                  "email":"a@sv.edu.vn","status":"ACTIVE"}]""", "[]");

        ReconcileReport r = reconciler.reconcile(true);

        assertThat(r.isDryRun()).isTrue();
        assertThat(r.getEntities().get("STUDENT_USER").missing).isEqualTo(1);
        assertThat(r.totalEnqueued()).isZero();
        assertThat(inboxRepo.count()).isZero();   // khong mot ban ghi nao duoc tao
    }

    // ==================================================================
    // I57 — day la ca kiem thu QUAN TRONG NHAT cua bo doi soat
    // ==================================================================
    @Test
    @DisplayName("I57 — chay doi soat HAI luot lien tiep: luot thu hai VAN sinh duoc viec")
    void secondRunIsNotBlockedByDeduplication() {
        stubBaseline("""
                [{"studentId":"SV001","firstName":"A","lastName":"Nguyen",
                  "email":"a@sv.edu.vn","status":"ACTIVE"}]""", "[]");

        ReconcileReport first = reconciler.reconcile(false);
        assertThat(first.getEntities().get("STUDENT_USER").enqueued).isEqualTo(1);

        ReconcileReport second = reconciler.reconcile(false);

        // Neu ma su kien tong hop dat theo THUC THE (recon:student:SV001) thi rang buoc
        // UNIQUE se coi luot nay la trung va bo qua sach — khien viec bam doi soat truoc
        // mat giang vien khong lam gi ca. runId sinh moi moi luot chinh la de tranh dieu do.
        assertThat(second.getEntities().get("STUDENT_USER").enqueued).isEqualTo(1);
        assertThat(second.getRunId()).isNotEqualTo(first.getRunId());
        assertThat(inboxRepo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("I58 — phat hien theo tap hop chi ton 6 request cho ca ba doi tuong")
    void detectionCostsSixRequests() {
        stubBaseline("[]", "[]");

        ReconcileReport r = reconciler.reconcile(true);

        assertThat(r.getRequestsUsed()).isEqualTo(6);
        sisMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/students")));
        sisMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/sections")));
        sisMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/enrollments")));
        lmsMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users")));
        lmsMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/courses")));
        lmsMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/memberships")));
    }

    @Test
    @DisplayName("I59 — su kien tong hop MANG THEO du lieu nen bo xu ly khong doc lai nguon")
    void syntheticEventCarriesResolvedData() {
        stubBaseline("""
                [{"studentId":"SV002","firstName":"B","lastName":"Tran Thi",
                  "email":"b@sv.edu.vn","programCode":"CNTT","cohort":2026,"status":"ACTIVE"}]""",
                "[]");
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("{\"id\":\"9\",\"externalRef\":\"SV002\",\"enabled\":true}")));

        reconciler.reconcile(false);
        worker.drainOnce();

        // KHONG doc lai /students/{id} — day la ly do mot dot sua khong bung so request
        sisMock.verify(0, getRequestedFor(urlPathMatching("/api/v1/students/.+")));
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withRequestBody(matchingJsonPath("$.displayName", equalTo("Tran Thi B")))
                .withRequestBody(matchingJsonPath("$.emailAddress", equalTo("b@sv.edu.vn"))));
    }

    @Test
    @DisplayName("I60 — membership khong dich nguoc duoc ve ma nghiep vu thi TUYET DOI khong go")
    void neverRemovesUnmappableMembership() {
        stubBaseline("[]", "[]");
        // Membership tro toi courseId/userId khong co trong danh sach da tai
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/memberships"))
                .willReturn(okJson("""
                        [{"membershipId":1,"courseId":999,"userId":888,
                          "role":"STUDENT","status":"ACTIVE"}]""")));

        ReconcileReport r = reconciler.reconcile(false);

        // Khong sinh su kien go nao — xoa nham thi khong khoi phuc duoc
        assertThat(r.getEntities().get("ENROLLMENT_MEMBERSHIP").extra).isZero();
        assertThat(inboxRepo.count()).isZero();
        assertThat(r.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("I61 — dang ky da huy o SIS nhung membership con ACTIVE o LMS thi sinh viec go")
    void detectsStaleMembership() {
        stubBaseline("""
                [{"studentId":"SV001","firstName":"A","lastName":"Nguyen",
                  "email":"a@sv.edu.vn","status":"ACTIVE"}]""",
                """
                [{"id":"1","externalRef":"SV001","displayName":"Nguyen A",
                  "emailAddress":"a@sv.edu.vn","enabled":true}]""");
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses"))
                .willReturn(okJson("""
                        [{"id":"7","externalCode":"SEC-01","title":"INT402","term":"2026-1",
                          "state":"PUBLISHED","teacherExternalRef":"GV0001"}]""")));
        // UniSIS: dang ky da DROPPED
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/enrollments"))
                .willReturn(okJson("""
                        [{"enrollmentId":"ENR-1","studentId":"SV001","sectionId":"SEC-01",
                          "status":"DROPPED"}]""")));
        // UniLearn: membership van con ACTIVE
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/memberships"))
                .willReturn(okJson("""
                        [{"membershipId":1,"courseId":7,"userId":1,
                          "role":"STUDENT","status":"ACTIVE"}]""")));

        ReconcileReport r = reconciler.reconcile(false);

        ReconcileReport.EntityStat stat = r.getEntities().get("ENROLLMENT_MEMBERSHIP");
        assertThat(stat.extra).isEqualTo(1);
        assertThat(stat.samples).contains("SV001|SEC-01");

        InboxEvent e = inboxRepo.findAll().stream()
                .filter(x -> "enrollment.dropped".equals(x.getEventType()))
                .findFirst().orElseThrow();
        assertThat(e.getBusinessKey()).isEqualTo("SV001|SEC-01");
    }

    // ------------------------------------------------------------------

    /** Sau endpoint ma mot luot doi soat can. */
    private static void stubBaseline(String studentsJson, String usersJson) {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/students")).willReturn(okJson(studentsJson)));
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/sections")).willReturn(okJson("[]")));
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/enrollments")).willReturn(okJson("[]")));
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson(usersJson)));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/courses")).willReturn(okJson("[]")));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/memberships")).willReturn(okJson("[]")));
    }

    private static void stubToken(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/token"))
                .willReturn(okJson("""
                        {"accessToken":"tok-test-123","expiresIn":43200}""")));
    }
}
