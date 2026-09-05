package vn.thanhdo.integration.sync;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.thanhdo.integration.client.SisApi;
import vn.thanhdo.integration.inbox.*;
import vn.thanhdo.integration.mapping.IdMapping;
import vn.thanhdo.integration.mapping.IdMappingService;
import vn.thanhdo.integration.poll.EventPoller;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu tich hop INT-01 va INT-05 voi hai he thong nguon duoc GIA LAP.
 *
 * <p>WireMock la mau chot: no cho phep ra lenh tra 503 dung hai lan roi tra 200, hoac
 * tra 409 — nhung tinh huong khong the tao ra mot cach tin cay tren moi truong that.
 *
 * <p>Cac ca kiem thu trong lop nay: I01, I02, I03, I04, I07, I10.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=validate",
        "integration.tenant-id=TEAM07",
        "integration.worker.enabled=false",     // tu goi drainOnce, khong cho lich
        "integration.poller.enabled=false",
        "integration.reconciler.run-on-startup=false",
        "integration.reconciler.run-on-schedule=false",
        "integration.retry.backoff-seconds=0,0,0,0,0,0",   // khoi phai cho that
        "integration.sis.client-secret=test-secret",
        "integration.lms.client-secret=test-secret"
})
class EnsureUserIntegrationTest {

    static WireMockServer sisMock;
    static WireMockServer lmsMock;

    @Autowired InboxService inbox;
    @Autowired Worker worker;
    @Autowired InboxRepository inboxRepo;
    @Autowired DeadLetterRepository deadLetterRepo;
    @Autowired IdMappingService mappings;
    @Autowired EventPoller poller;
    @Autowired SisApi sisApi;

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
        deadLetterRepo.deleteAll();   // truoc inbox: co khoa ngoai tro toi inbox_event
        inboxRepo.deleteAll();
        stubToken(sisMock);
        stubToken(lmsMock);
    }

    // ==================================================================
    // I01 — duong thuan: tao Student moi thi LMS co dung MOT User
    // ==================================================================
    @Test
    @DisplayName("I01 — student.created tao dung mot LMS User voi day du truong anh xa")
    void createsUserOnStudentCreated() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("externalRef", equalTo("SV07LAB01"))
                .willReturn(okJson("[]")));

        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        {"id":"48329","username":"SV07LAB01","displayName":"Nguyen Van An",
                         "emailAddress":"an@sv.edu.vn","userType":"LEARNER","enabled":true,
                         "externalRef":"SV07LAB01"}""")));

        deliver("evt_001", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        // Dung mot lan tao, khong hon
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/users")));

        // Bang anh xa INT-01 duoc ap dung day du
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withRequestBody(matchingJsonPath("$.username", equalTo("SV07LAB01")))
                .withRequestBody(matchingJsonPath("$.externalRef", equalTo("SV07LAB01")))
                .withRequestBody(matchingJsonPath("$.displayName", equalTo("Nguyen Van An")))
                .withRequestBody(matchingJsonPath("$.emailAddress", equalTo("an@sv.edu.vn")))
                .withRequestBody(matchingJsonPath("$.userType", equalTo("LEARNER")))
                .withRequestBody(matchingJsonPath("$.enabled", equalTo("true"))));

        // Moi request deu mang dung tenant (NFR-08)
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withHeader("X-Tenant-ID", equalTo("TEAM07")));

        InboxEvent e = event("evt_001");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("CREATE");

        assertThat(mappings.findTargetId("SIS", IdMapping.STUDENT_USER, "SV07LAB01"))
                .contains("48329");
    }

    @Test
    @DisplayName("Muc 3.3 — payload du truong va con moi thi KHONG doc lai UniSIS")
    void usesEventPayloadAsDesired() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        {"id":"1","externalRef":"SV07LAB01","enabled":true}""")));

        deliver("evt_002", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        // Tiet kiem dung mot request — day la ca kiem thu cho toi uu o muc 3.3
        sisMock.verify(0, getRequestedFor(urlPathMatching("/api/v1/students/.*")));
    }

    @Test
    @DisplayName("Muc 3.3 — payload thieu truong thi PHAI doc lai nguon su that")
    void readsSourceWhenPayloadIncomplete() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/students/SV07LAB02"))
                .willReturn(okJson("""
                        {"studentId":"SV07LAB02","firstName":"Binh","lastName":"Tran Thi",
                         "email":"binh@sv.edu.vn","status":"ACTIVE"}""")));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        {"id":"2","externalRef":"SV07LAB02","enabled":true}""")));

        // payload chi co studentId — khong du de ghi de nguon su that
        deliver("evt_003", "student.created",
                """
                {"eventId":"evt_003","eventType":"student.created","occurredAt":"%s",
                 "data":{"studentId":"SV07LAB02"}}"""
                        .formatted(Instant.now()));
        worker.drainOnce();

        sisMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/students/SV07LAB02")));
        lmsMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withRequestBody(matchingJsonPath("$.displayName", equalTo("Tran Thi Binh"))));
    }

    // ==================================================================
    // I02 — cap nhat thi PATCH, tuyet doi khong POST them User thu hai
    // ==================================================================
    @Test
    @DisplayName("I02 — doi email o SIS thi PATCH User da co, khong tao User moi")
    void updatesExistingUserInsteadOfCreating() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("externalRef", equalTo("SV07LAB01"))
                .willReturn(okJson("""
                        [{"id":"48329","username":"SV07LAB01","displayName":"Nguyen Van An",
                          "emailAddress":"cu@sv.edu.vn","userType":"LEARNER","enabled":true,
                          "externalRef":"SV07LAB01"}]""")));

        lmsMock.stubFor(patch(urlPathEqualTo("/api/v1/users/48329"))
                .willReturn(okJson("""
                        {"id":"48329","externalRef":"SV07LAB01","emailAddress":"moi@sv.edu.vn",
                         "enabled":true}""")));

        deliver("evt_010", "student.updated", freshPayload("SV07LAB01", "moi@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/users")));
        lmsMock.verify(1, patchRequestedFor(urlPathEqualTo("/api/v1/users/48329"))
                .withRequestBody(matchingJsonPath("$.emailAddress", equalTo("moi@sv.edu.vn"))));

        assertThat(event("evt_010").getLastAction()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("INT-05 — doi ACTIVE sang SUSPENDED thi enabled=false")
    void disablesAccountOnStatusChange() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        [{"id":"48329","externalRef":"SV07LAB01","displayName":"Nguyen Van An",
                          "emailAddress":"an@sv.edu.vn","enabled":true}]""")));
        lmsMock.stubFor(patch(urlPathEqualTo("/api/v1/users/48329"))
                .willReturn(okJson("""
                        {"id":"48329","enabled":false}""")));

        deliver("evt_011", "student.updated", freshPayload("SV07LAB01", "an@sv.edu.vn", "SUSPENDED"));
        worker.drainOnce();

        lmsMock.verify(patchRequestedFor(urlPathEqualTo("/api/v1/users/48329"))
                .withRequestBody(matchingJsonPath("$.enabled", equalTo("false"))));
    }

    @Test
    @DisplayName("Trang thai da dung thi KHONG LAM GI — day la bang chung NOOP")
    void doesNothingWhenAlreadyConverged() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        [{"id":"48329","externalRef":"SV07LAB01","displayName":"Nguyen Van An",
                          "emailAddress":"an@sv.edu.vn","enabled":true}]""")));

        deliver("evt_012", "student.updated", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/users")));
        lmsMock.verify(0, patchRequestedFor(urlPathMatching("/api/v1/users/.*")));
        assertThat(event("evt_012").getLastAction()).isEqualTo("NOOP");
    }

    // ==================================================================
    // I03 — NFR-01: cung eventId giao ba lan chi gay MOT tac dong
    // ==================================================================
    @Test
    @DisplayName("I03 — giao cung eventId ba lan chi tao User mot lan")
    void duplicateEventCausesSingleEffect() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        {"id":"48329","externalRef":"SV07LAB01","enabled":true}""")));

        String payload = freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE");

        assertThat(deliver("evt_020", "student.created", payload))
                .isEqualTo(InboxService.Result.INSERTED);
        // Hai lan sau bi chan ngay o TANG CO SO DU LIEU boi rang buoc UNIQUE
        assertThat(deliver("evt_020", "student.created", payload))
                .isEqualTo(InboxService.Result.DUPLICATE);
        assertThat(deliver("evt_020", "student.created", payload))
                .isEqualTo(InboxService.Result.DUPLICATE);

        worker.drainOnce();

        assertThat(inboxRepo.count()).isEqualTo(1);
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/users")));
    }

    // ==================================================================
    // I07 — 409 KHONG phai loi: tra lai theo khoa nghiep vu roi hoi tu tiep
    // ==================================================================
    @Test
    @DisplayName("I07 — POST tra 409 USER_EXISTS thi tra lai theo externalRef va coi la thanh cong")
    void handlesConflictAsRecoverableState() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .inScenario("conflict").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("[]"))
                .willSetStateTo("da-ton-tai"));

        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"USER_EXISTS","message":"externalRef da ton tai"}""")));

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .inScenario("conflict").whenScenarioStateIs("da-ton-tai")
                .willReturn(okJson("""
                        [{"id":"48329","externalRef":"SV07LAB01","displayName":"Nguyen Van An",
                          "emailAddress":"an@sv.edu.vn","enabled":true}]""")));

        deliver("evt_030", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        InboxEvent e = event("evt_030");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);   // KHONG phai DEAD_LETTER
        assertThat(mappings.findTargetId("SIS", IdMapping.STUDENT_USER, "SV07LAB01"))
                .contains("48329");
    }

    // ==================================================================
    // I10 — BV-4: mat ban ghi anh xa nhung du lieu dich con thi tu phuc hoi
    // ==================================================================
    @Test
    @DisplayName("I10 — xoa anh xa ma User van con thi he thong tu tra lai, khong tao trung")
    void recoversLostMappingWithoutDuplicating() {
        mappings.upsert("SIS", IdMapping.STUDENT_USER, "SV07LAB01", "48329");
        mappings.deleteFor("SIS", IdMapping.STUDENT_USER, "SV07LAB01");
        assertThat(mappings.findTargetId("SIS", IdMapping.STUDENT_USER, "SV07LAB01")).isEmpty();

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        [{"id":"48329","externalRef":"SV07LAB01","displayName":"Nguyen Van An",
                          "emailAddress":"an@sv.edu.vn","enabled":true}]""")));

        deliver("evt_040", "student.updated", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        lmsMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/users")));
        assertThat(mappings.findTargetId("SIS", IdMapping.STUDENT_USER, "SV07LAB01"))
                .contains("48329");
        assertThat(event("evt_040").getStatus()).isEqualTo(InboxStatus.DONE);
    }

    // ==================================================================
    // I04 — NFR-02: 503 hai lan roi hoat dong lai, su kien KHONG bi mat
    // ==================================================================
    @Test
    @DisplayName("I04 — LMS tra 503 hai lan roi 201: su kien qua RETRYING roi DONE")
    void retriesTransientFailureThenSucceeds() {
        // Tu lan thu lai thu hai tro di, quy tac muc 3.3 bat buoc DOC LAI nguon su that
        // thay vi tin payload cu — nen UniSIS phai duoc gia lap cho cac luot sau.
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/students/SV07LAB01"))
                .willReturn(okJson("""
                        {"studentId":"SV07LAB01","firstName":"An","lastName":"Nguyen Van",
                         "email":"an@sv.edu.vn","status":"ACTIVE"}""")));

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));

        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("loi-lan-2"));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .inScenario("flaky").whenScenarioStateIs("loi-lan-2")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("da-on"));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .inScenario("flaky").whenScenarioStateIs("da-on")
                .willReturn(okJson("""
                        {"id":"48329","externalRef":"SV07LAB01","enabled":true}""")));

        deliver("evt_050", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));

        worker.drainOnce();
        assertThat(event("evt_050").getStatus()).isEqualTo(InboxStatus.RETRYING);

        worker.drainOnce();
        assertThat(event("evt_050").getStatus()).isEqualTo(InboxStatus.RETRYING);

        worker.drainOnce();
        InboxEvent done = event("evt_050");
        assertThat(done.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(done.getAttempt()).isEqualTo(3);
        assertThat(done.getRetryCount()).isEqualTo(2);

        // Lan dau dung payload; tu lan thu lai tro di doc lai nguon su that (muc 3.3)
        sisMock.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/students/SV07LAB01")));
    }

    // ==================================================================
    // I08 — muc 11.5 cua de bai: PATCH tra 404 thi tra lai theo khoa nghiep vu,
    //       neu that su khong con thi tao moi
    // ==================================================================
    @Test
    @DisplayName("I08 — PATCH tra 404 thi tra lai theo externalRef roi tao lai, khong dead-letter")
    void recreatesUserWhenPatchReturnsNotFound() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .inScenario("stale").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("""
                        [{"id":"48329","externalRef":"SV07LAB01","displayName":"Nguyen Van An",
                          "emailAddress":"cu@sv.edu.vn","enabled":true}]"""))
                .willSetStateTo("da-bien-mat"));

        // User bien mat giua luc tra cuu va luc ghi
        lmsMock.stubFor(patch(urlPathEqualTo("/api/v1/users/48329"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"USER_NOT_FOUND\"}")));

        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .inScenario("stale").whenScenarioStateIs("da-bien-mat")
                .willReturn(okJson("[]")));

        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("""
                        {"id":"55555","externalRef":"SV07LAB01","enabled":true}""")));

        deliver("evt_080", "student.updated", freshPayload("SV07LAB01", "moi@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        InboxEvent e = event("evt_080");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DONE);
        assertThat(e.getLastAction()).isEqualTo("CREATE");
        lmsMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/users")));

        // Anh xa tro sang id moi, khong con giu id cu da chet
        assertThat(mappings.findTargetId("SIS", IdMapping.STUDENT_USER, "SV07LAB01"))
                .contains("55555");
    }

    // ==================================================================
    // I63 / I64 — NFR-12 va kich ban cham T12: ton trong 429 va Retry-After
    // ==================================================================
    @Test
    @DisplayName("I63 — 429 kem Retry-After: cho DUNG khoang do, khong dung day backoff")
    void respectsRetryAfterHeaderOn429() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "7")));

        Instant truoc = Instant.now();
        deliver("evt_090", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        InboxEvent e = event("evt_090");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.RETRYING);

        // Day backoff trong cau hinh kiem thu la 0 giay; neu he thong dung backoff thay vi
        // doc Retry-After thi moc thu lai se la NGAY BAY GIO chu khong phai +7 giay.
        assertThat(e.getNextAttemptAt()).isAfter(truoc.plusSeconds(5));
        assertThat(e.getNextAttemptAt()).isBefore(truoc.plusSeconds(10));
    }

    @Test
    @DisplayName("I64 — 429 KHONG dot ngan sach thu lai: ap luc tan suat khong phai that bai")
    void rateLimitDoesNotConsumeRetryBudget() {
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));
        lmsMock.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));

        deliver("evt_091", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));

        // Chay nhieu luot hon so lan thu lai toi da (6). Neu 429 bi tinh la that bai thi
        // su kien da roi vao DEAD_LETTER tu luot thu bay.
        for (int i = 0; i < 9; i++) {
            worker.drainOnce();
        }

        InboxEvent e = event("evt_091");
        assertThat(e.getStatus())
                .as("gioi han tan suat la ap luc tam thoi, khong duoc coi la loi du lieu")
                .isEqualTo(InboxStatus.RETRYING);
        assertThat(deadLetterRepo.count()).isZero();
    }

    @Test
    @DisplayName("I21 — he thong nguon khong ket noi duoc la loi TAM THOI, phai RETRYING chu khong dead-letter")
    void networkFailureIsTransientNotFatal() {
        // Tinh huong that: khoi dong dich vu truoc khi lab san sang, hoac mang chap chon.
        // Neu phan loai nham thanh loi du lieu thi toan bo su kien dau tien mat sach.
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        deliver("evt_070", "student.created", freshPayload("SV07LAB01", "an@sv.edu.vn", "ACTIVE"));
        worker.drainOnce();

        InboxEvent e = event("evt_070");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.RETRYING);
        assertThat(e.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("NFR-03 — 422 tu UniSIS la loi du lieu: vao dead-letter chu khong thu lai vo han")
    void dataErrorGoesToDeadLetterWithoutEndlessRetry() {
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/students/SV_KHONG_CO"))
                .willReturn(aResponse().withStatus(404)));
        lmsMock.stubFor(get(urlPathEqualTo("/api/v1/users")).willReturn(okJson("[]")));

        deliver("evt_060", "student.updated",
                """
                {"eventId":"evt_060","eventType":"student.updated","occurredAt":"%s",
                 "data":{"studentId":"SV_KHONG_CO"}}""".formatted(Instant.now()));
        worker.drainOnce();

        InboxEvent e = event("evt_060");
        assertThat(e.getStatus()).isEqualTo(InboxStatus.DEAD_LETTER);
        assertThat(e.getAttempt()).isEqualTo(1);   // dung ngay, khong dot ngan sach thu lai
    }

    // ==================================================================
    // I22 — diem moc polling phai TIEN, va tham so since phai duoc ma hoa DUNG MOT LAN
    // ==================================================================
    @Test
    @DisplayName("I22 — poller ghi diem moc va gui since dung dinh dang, khong ma hoa hai lan")
    void pollerAdvancesCheckpointAndEncodesSinceExactlyOnce() {
        // occurredAt KHONG co mui gio — dung dinh dang that ma SQLite tra ve
        sisMock.stubFor(get(urlPathEqualTo("/api/v1/events"))
                .willReturn(okJson("""
                        [{"eventId":"evt_p1","eventType":"student.created",
                          "occurredAt":"2026-08-15T01:51:15.196920",
                          "data":{"studentId":"SV07POLL1","firstName":"An","lastName":"Nguyen Van",
                                  "email":"a@sv.edu.vn","status":"ACTIVE"}}]""")));

        assertThat(poller.pollOne("SIS", sisApi::getEvents)).isEqualTo(1);

        // Luot hai: diem moc da co nen phai kem since
        poller.pollOne("SIS", sisApi::getEvents);

        // Neu ma hoa hai lan, gia tri giai ma se la "2026-08-15T01%3A51:..." va khong khop
        sisMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/events"))
                .withQueryParam("since", matching("2026-08-15T01:51:1\\d\\.\\d+Z")));

        // Su kien lap lai o luot hai bi chan boi lop chong trung
        assertThat(poller.pollOne("SIS", sisApi::getEvents)).isZero();
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

    /** Payload day du va con moi — du dieu kien lam desired theo muc 3.3. */
    private static String freshPayload(String studentId, String email, String status) {
        return """
               {"eventId":"x","eventType":"student.created","occurredAt":"%s",
                "data":{"studentId":"%s","firstName":"An","lastName":"Nguyen Van",
                        "email":"%s","programCode":"CNTT","cohort":"K17","status":"%s"}}
               """.formatted(Instant.now(), studentId, email, status);
    }

    private static void stubToken(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/api/v1/auth/token"))
                .willReturn(okJson("""
                        {"accessToken":"tok-test-123","expiresIn":43200}""")));
    }
}
