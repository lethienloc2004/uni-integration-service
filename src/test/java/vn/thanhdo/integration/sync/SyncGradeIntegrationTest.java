package vn.thanhdo.integration.sync;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.thanhdo.integration.inbox.*;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu tich hop INT-06 — dong bo diem da cong bo tu UniLearn ve UniSIS.
 *
 * <p>Luong NGUOC: LMS → SIS. Cac ca kiem thu I42 … I47.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gradedb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
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
class SyncGradeIntegrationTest {

    static WireMockServer sisMock;
    static WireMockServer lmsMock;

    @Autowired InboxService inbox;
    @Autowired Worker worker;
    @Autowired InboxRepository inboxRepo;
    @Autowired DeadLetterRepository deadLetterRepo;

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
        stubToken(sisMock);
        stubToken(lmsMock);
    }

    @Test
    @DisplayName("I42 — 87.5/100 tro thanh 8.75/10 tai dung Student/Section, source=LMS")
    void convertsAndWritesOfficialGrade() {
        sisMock.stubFor(put(urlPathEqualTo("/api/v1/grades/SV07LAB01/SEC-TEAM07-LAB01"))
                .willReturn(okJson("""
                        {"studentId":"SV07LAB01","sectionId":"SEC-TEAM07-LAB01",
                         "finalScore":8.75,"letterGrade":"A","source":"LMS"}""")));

        deliver("evt_g01", payload("SV07LAB01", "SEC-TEAM07-LAB01", 87.5));
        worker.drainOnce();

        // Tieu chi nghiem thu 1 va 3 cua muc 16.6
        sisMock.verify(1, putRequestedFor(urlPathEqualTo("/api/v1/grades/SV07LAB01/SEC-TEAM07-LAB01"))
                .withRequestBody(matchingJsonPath("$.finalScore", equalTo("8.75")))
                .withRequestBody(matchingJsonPath("$.source", equalTo("LMS"))));

        InboxEvent e = event("evt_g01");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("I43 — CO Y khong gui letterGrade de UniSIS tu tinh")
    void doesNotSendLetterGrade() {
        sisMock.stubFor(put(urlPathMatching("/api/v1/grades/.*"))
                .willReturn(okJson("{\"finalScore\":8.75,\"letterGrade\":\"A\"}")));

        deliver("evt_g02", payload("SV07LAB01", "SEC-TEAM07-LAB01", 87.5));
        worker.drainOnce();

        // Tieu chi nghiem thu thu tu cua muc 16.6
        sisMock.verify(putRequestedFor(urlPathMatching("/api/v1/grades/.*"))
                .withRequestBody(notMatching(".*letterGrade.*")));
    }

    @Test
    @DisplayName("I44 — dung external reference chu KHONG dung id noi bo cua LMS")
    void usesExternalReferencesNotInternalIds() {
        sisMock.stubFor(put(urlPathMatching("/api/v1/grades/.*"))
                .willReturn(okJson("{\"finalScore\":8.75}")));

        // Payload co CA external reference lan id noi bo — phai chon dung loai
        String withBothIdKinds = """
               {"eventId":"x","eventType":"grade.published","occurredAt":"%s",
                "data":{"gradeId":"5","courseId":9849,"courseExternalCode":"SEC-TEAM07-LAB01",
                        "userId":48329,"userExternalRef":"SV07LAB01","finalGrade":87.5}}
               """.formatted(Instant.now());
        deliver("evt_g03", withBothIdKinds);
        worker.drainOnce();

        // Duong dan phai la ma nghiep vu, tuyet doi khong phai 48329/9849
        sisMock.verify(1, putRequestedFor(
                urlPathEqualTo("/api/v1/grades/SV07LAB01/SEC-TEAM07-LAB01")));
        sisMock.verify(0, putRequestedFor(urlPathEqualTo("/api/v1/grades/48329/9849")));
    }

    @Test
    @DisplayName("I45 — 422 ENROLLMENT_NOT_FOUND vao dead-letter ngay, KHONG thu lai vo han")
    void enrollmentNotFoundGoesStraightToDeadLetter() {
        sisMock.stubFor(put(urlPathMatching("/api/v1/grades/.*"))
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"ENROLLMENT_NOT_FOUND\"}")));

        deliver("evt_g04", payload("SV_KHONG_HOC", "SEC-TEAM07-LAB01", 87.5));
        worker.drainOnce();

        InboxEvent e = event("evt_g04");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DEAD_LETTER);
        assertThat(e.getAttempt()).isEqualTo(1);       // dung ngay, khong dot ngan sach thu lai
        assertThat(deadLetterRepo.count()).isEqualTo(1);

        // Muc 16.5: TUYET DOI khong duoc tu tao enrollment de "chua" tinh huong nay
        sisMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/enrollments")));
    }

    @Test
    @DisplayName("I46 — UniSIS tra 503 roi hoat dong lai: qua RETRYING roi DONE")
    void retriesWhenSisTemporarilyDown() {
        sisMock.stubFor(put(urlPathMatching("/api/v1/grades/.*"))
                .inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("da-on"));
        sisMock.stubFor(put(urlPathMatching("/api/v1/grades/.*"))
                .inScenario("flaky").whenScenarioStateIs("da-on")
                .willReturn(okJson("{\"finalScore\":8.75}")));

        deliver("evt_g05", payload("SV07LAB01", "SEC-TEAM07-LAB01", 87.5));

        worker.drainOnce();
        assertThat(event("evt_g05").getStatus()).isEqualTo(InboxStatus.RETRYING);

        worker.drainOnce();
        InboxEvent done = event("evt_g05");
        assertThat(done.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(done.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("I47 — gui lap grade.published chi ghi diem MOT lan")
    void duplicatePublishWritesOnce() {
        sisMock.stubFor(put(urlPathMatching("/api/v1/grades/.*"))
                .willReturn(okJson("{\"finalScore\":8.75}")));

        String p = payload("SV07LAB01", "SEC-TEAM07-LAB01", 87.5);
        assertThat(deliver("evt_g06", p)).isEqualTo(InboxService.Result.INSERTED);
        assertThat(deliver("evt_g06", p)).isEqualTo(InboxService.Result.DUPLICATE);
        assertThat(deliver("evt_g06", p)).isEqualTo(InboxService.Result.DUPLICATE);
        worker.drainOnce();

        assertThat(inboxRepo.count()).isEqualTo(1);
        sisMock.verify(1, putRequestedFor(urlPathMatching("/api/v1/grades/.*")));
    }

    @Test
    @DisplayName("I48 — diem ngoai thang 0..100 bi chan TRUOC khi goi API")
    void rejectsOutOfRangeGradeWithoutCallingApi() {
        deliver("evt_g07", payload("SV07LAB01", "SEC-TEAM07-LAB01", 150.0));
        worker.drainOnce();

        assertThat(event("evt_g07").getStatus()).isEqualTo(InboxStatus.DEAD_LETTER);
        // Khong ton mot luot nao trong ngan sach tan suat cho mot loi doan truoc duoc
        sisMock.verify(0, putRequestedFor(urlPathMatching("/api/v1/grades/.*")));
    }

    @Test
    @DisplayName("INT-06 — diem DRAFT khong bao gio den day vi UniLearn khong phat su kien")
    void draftGradeNeverReachesHandler() {
        // UniLearn chi phat grade.published khi bam Publish. Bo xu ly nay CHI nhan loai do,
        // nen thao tac luu nhap khong the gay ra bat ky loi goi nao toi UniSIS.
        deliver("evt_g08_draft", """
               {"eventId":"x","eventType":"grade.draft","occurredAt":"%s",
                "data":{"userExternalRef":"SV07LAB01","courseExternalCode":"SEC-TEAM07-LAB01",
                        "finalGrade":87.5}}
               """.formatted(Instant.now()), "grade.draft");
        worker.drainOnce();

        sisMock.verify(0, putRequestedFor(urlPathMatching("/api/v1/grades/.*")));
        assertThat(event("evt_g08_draft").getLastAction()).isEqualTo("NOOP");
    }

    // ------------------------------------------------------------------

    private InboxService.Result deliver(String eventId, String payload) {
        return deliver(eventId, payload, "grade.published");
    }

    private InboxService.Result deliver(String eventId, String payload, String eventType) {
        return inbox.record(InboxService.Channel.WEBHOOK, "LMS", eventId, eventType,
                payload, Instant.now());
    }

    private InboxEvent event(String eventId) {
        return inboxRepo.findBySourceSystemAndEventId("LMS", eventId).orElseThrow();
    }

    /** Hinh dang that ma UniLearn phat ra khi publish diem. */
    private static String payload(String userExternalRef, String courseExternalCode, double finalGrade) {
        return """
               {"eventId":"x","eventType":"grade.published","occurredAt":"%s",
                "data":{"gradeId":"5","courseId":9849,"courseExternalCode":"%s",
                        "userId":48329,"userExternalRef":"%s","finalGrade":%s,
                        "publishedAt":"%s"}}
               """.formatted(Instant.now(), courseExternalCode, userExternalRef,
                             finalGrade, Instant.now());
    }

    private static void stubToken(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/token"))
                .willReturn(okJson("""
                        {"accessToken":"tok-test-123","expiresIn":43200}""")));
    }
}
