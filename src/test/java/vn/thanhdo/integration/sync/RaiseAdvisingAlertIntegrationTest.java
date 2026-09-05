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
 * Kiem thu tich hop INT-08 — chuyen Learning Risk thanh Advising Alert.
 *
 * <p>Cac ca kiem thu: I49 … I54.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:alertdb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
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
class RaiseAdvisingAlertIntegrationTest {

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
    @DisplayName("I49 — completion 20% + 16 ngay khong hoat dong tao dung 01 Advising Alert AT_RISK")
    void createsAlertForAtRiskLearner() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts")).willReturn(okJson("[]")));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("""
                        {"alertId":"ALT-1","studentId":"SV070001","sectionId":"SEC-TEAM07-01",
                         "riskType":"AT_RISK","status":"OPEN"}""")));

        deliver("evt_r01", payload("SV070001", "SEC-TEAM07-01", 20.0, 16, "AT_RISK"));
        worker.drainOnce();

        // Bang anh xa muc 18.2
        sisMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts"))
                .withRequestBody(matchingJsonPath("$.studentId", equalTo("SV070001")))
                .withRequestBody(matchingJsonPath("$.sectionId", equalTo("SEC-TEAM07-01")))
                .withRequestBody(matchingJsonPath("$.riskType", equalTo("AT_RISK"))));

        // details phai chua CA tien do lan so ngay khong hoat dong
        sisMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts"))
                .withRequestBody(matchingJsonPath("$.details", containing("20")))
                .withRequestBody(matchingJsonPath("$.details", containing("16"))));

        InboxEvent e = event("evt_r01");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("I50 — da co canh bao AT_RISK dang mo thi KHONG tao them (chong doi)")
    void doesNotDuplicateOpenAlert() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("""
                        [{"alertId":"ALT-CU","studentId":"SV070001","sectionId":"SEC-TEAM07-01",
                          "riskType":"AT_RISK","status":"OPEN"}]""")));

        deliver("evt_r02", payload("SV070001", "SEC-TEAM07-01", 15.0, 20, "AT_RISK"));
        worker.drainOnce();

        sisMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts")));
        assertThat(event("evt_r02").getLastAction()).isEqualTo("NOOP");
    }

    @Test
    @DisplayName("I50b — canh bao cu da DONG thi van tao canh bao moi")
    void createsNewAlertWhenPreviousIsClosed() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("""
                        [{"alertId":"ALT-CU","studentId":"SV070001","sectionId":"SEC-TEAM07-01",
                          "riskType":"AT_RISK","status":"CLOSED"}]""")));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("{\"alertId\":\"ALT-2\"}")));

        deliver("evt_r03", payload("SV070001", "SEC-TEAM07-01", 15.0, 20, "AT_RISK"));
        worker.drainOnce();

        sisMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts")));
        assertThat(event("evt_r03").getLastAction()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("I51 — canh bao cua LOP KHAC khong chan viec tao canh bao cho lop nay")
    void alertForAnotherSectionDoesNotBlock() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("""
                        [{"alertId":"ALT-KHAC","studentId":"SV070001","sectionId":"SEC-TEAM07-99",
                          "riskType":"AT_RISK","status":"OPEN"}]""")));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("{\"alertId\":\"ALT-3\"}")));

        deliver("evt_r04", payload("SV070001", "SEC-TEAM07-01", 20.0, 16, "AT_RISK"));
        worker.drainOnce();

        sisMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts"))
                .withRequestBody(matchingJsonPath("$.sectionId", equalTo("SEC-TEAM07-01"))));
    }

    @Test
    @DisplayName("I52 — gui lap cung eventId chi tao canh bao MOT lan")
    void duplicateEventCreatesOneAlert() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts")).willReturn(okJson("[]")));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(okJson("{\"alertId\":\"ALT-4\"}")));

        String p = payload("SV070001", "SEC-TEAM07-01", 20.0, 16, "AT_RISK");
        assertThat(deliver("evt_r05", p)).isEqualTo(InboxService.Result.INSERTED);
        assertThat(deliver("evt_r05", p)).isEqualTo(InboxService.Result.DUPLICATE);
        worker.drainOnce();

        assertThat(inboxRepo.count()).isEqualTo(1);
        sisMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts")));
    }

    @Test
    @DisplayName("I53 — 422 STUDENT_NOT_FOUND vao dead-letter ngay, khong thu lai vo han")
    void studentNotFoundGoesToDeadLetter() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts")).willReturn(okJson("[]")));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"STUDENT_NOT_FOUND\"}")));

        deliver("evt_r06", payload("SV_KHONG_TON_TAI", "SEC-TEAM07-01", 20.0, 16, "AT_RISK"));
        worker.drainOnce();

        InboxEvent e = event("evt_r06");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DEAD_LETTER);
        assertThat(e.getAttempt()).isEqualTo(1);
        assertThat(deadLetterRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("I54 — UniSIS tra 503 roi hoat dong lai: qua RETRYING roi DONE")
    void retriesWhenSisTemporarilyDown() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts")).willReturn(okJson("[]")));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("da-on"));
        sisMock.stubFor(post(urlPathEqualTo("/api/v1/advising/alerts"))
                .inScenario("flaky").whenScenarioStateIs("da-on")
                .willReturn(okJson("{\"alertId\":\"ALT-5\"}")));

        deliver("evt_r07", payload("SV070001", "SEC-TEAM07-01", 20.0, 16, "AT_RISK"));

        worker.drainOnce();
        assertThat(event("evt_r07").getStatus()).isEqualTo(InboxStatus.RETRYING);

        worker.drainOnce();
        assertThat(event("evt_r07").getStatus()).isEqualTo(InboxStatus.DONE);
    }

    @Test
    @DisplayName("Tieu chi 18.6 — tin hieu NORMAL tuyet doi khong tao canh bao")
    void normalRiskNeverCreatesAlert() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/advising/alerts")).willReturn(okJson("[]")));

        // UniLearn khong phat su kien cho NORMAL; day la lop phong thu neu no van loi vao
        deliver("evt_r08", payload("SV070001", "SEC-TEAM07-01", 50.0, 3, "NORMAL"));
        worker.drainOnce();

        sisMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/advising/alerts")));
        assertThat(event("evt_r08").getLastAction()).isEqualTo("NOOP");
    }

    @Test
    @DisplayName("Muc 18.5 — thieu external reference la loi du lieu, khong thu lai vo han")
    void missingExternalReferenceIsDataError() {
        deliver("evt_r09", """
               {"eventId":"x","eventType":"learner.at_risk","occurredAt":"%s",
                "data":{"riskId":"RSK-1","userId":48329,"courseId":9849,
                        "completionPercent":20,"inactiveDays":16,"riskType":"AT_RISK"}}
               """.formatted(Instant.now()));
        worker.drainOnce();

        InboxEvent e = event("evt_r09");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DEAD_LETTER);
        assertThat(e.getAttempt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------

    private InboxService.Result deliver(String eventId, String payload) {
        return inbox.record(InboxService.Channel.WEBHOOK, "LMS", eventId, "learner.at_risk",
                payload, Instant.now());
    }

    private InboxEvent event(String eventId) {
        return inboxRepo.findBySourceSystemAndEventId("LMS", eventId).orElseThrow();
    }

    /** Hinh dang that ma UniLearn phat ra khi danh gia rui ro hoc tap. */
    private static String payload(String userExternalRef, String courseExternalCode,
                                  double completion, int inactiveDays, String riskType) {
        return """
               {"eventId":"x","eventType":"learner.at_risk","occurredAt":"%s",
                "data":{"riskId":"RSK-TEST","courseId":9849,"courseExternalCode":"%s",
                        "userId":48329,"userExternalRef":"%s",
                        "completionPercent":%s,"inactiveDays":%s,"riskType":"%s"}}
               """.formatted(Instant.now(), courseExternalCode, userExternalRef,
                             completion, inactiveDays, riskType);
    }

    private static void stubToken(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/token"))
                .willReturn(okJson("""
                        {"accessToken":"tok-test-123","expiresIn":43200}""")));
    }
}
