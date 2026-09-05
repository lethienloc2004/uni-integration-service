package vn.thanhdo.integration.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.thanhdo.integration.client.ApiClient;
import vn.thanhdo.integration.client.dto.LmsGradePublished;
import vn.thanhdo.integration.client.dto.SisStudent;
import vn.thanhdo.integration.util.Timestamps;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ca kiem thu U02, U03 va phan don vi cua I15 — bang anh xa INT-01 (muc 4.3).
 *
 * <p>Day la kiem thu THUAN: khong mang, khong CSDL, chay duoi mot giay.
 */
class SisStudentMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SisStudent student(String json) throws Exception {
        return SisStudent.from(mapper.readTree(json));
    }

    // ------------------------------------------------------------------
    // U02 — anh xa trang thai sinh vien sang enabled. Day la TOAN BO
    //       noi dung nghiep vu cua INT-05.
    // ------------------------------------------------------------------
    @ParameterizedTest(name = "U02: status={0} -> enabled={1}")
    @CsvSource({
            "ACTIVE,      true",
            "SUSPENDED,   false",
            "ON_LEAVE,    false",
            "GRADUATED,   false",
            "DROPPED_OUT, false"
    })
    @DisplayName("U02 — ACTIVE thi bat, moi trang thai khac thi tat")
    void mapsStatusToEnabled(String status, boolean expected) throws Exception {
        SisStudent s = student("""
                {"studentId":"SV07LAB01","firstName":"An","lastName":"Nguyen Van",
                 "email":"an@sv.edu.vn","status":"%s"}
                """.formatted(status));

        assertThat(s.enabled()).isEqualTo(expected);
    }

    @Test
    @DisplayName("U02 — chu thuong hay chu hoa deu nhan dung ACTIVE")
    void statusIsCaseInsensitive() throws Exception {
        assertThat(student("""
                {"studentId":"SV1","status":"active"}""").enabled()).isTrue();
    }

    @Test
    @DisplayName("U02 — trang thai la ma khong biet thi coi nhu KHONG hoat dong")
    void unknownStatusIsNotEnabled() throws Exception {
        // An toan mot chieu: tha khoa nham con hon mo nham tai khoan da bi dinh chi
        assertThat(student("""
                {"studentId":"SV1","status":"SOMETHING_NEW"}""").enabled()).isFalse();
    }

    // ------------------------------------------------------------------
    // U03 — ghep displayName = lastName + " " + firstName, chuan hoa khoang trang
    // ------------------------------------------------------------------
    @Test
    @DisplayName("U03 — ghep ho truoc ten sau")
    void buildsDisplayName() throws Exception {
        assertThat(student("""
                {"studentId":"SV1","firstName":"An","lastName":"Nguyen Van"}""")
                .displayName()).isEqualTo("Nguyen Van An");
    }

    @Test
    @DisplayName("U03 — khoang trang thua bi thu gon")
    void collapsesExtraWhitespace() throws Exception {
        assertThat(student("""
                {"studentId":"SV1","firstName":"  An  ","lastName":" Nguyen   Van "}""")
                .displayName()).isEqualTo("Nguyen Van An");
    }

    @Test
    @DisplayName("U03 — thieu mot phan ten van cho ket qua sach")
    void handlesMissingNamePart() throws Exception {
        assertThat(student("""
                {"studentId":"SV1","lastName":"Nguyen Van"}""")
                .displayName()).isEqualTo("Nguyen Van");
        assertThat(student("""
                {"studentId":"SV1","firstName":"An"}""")
                .displayName()).isEqualTo("An");
    }

    // ------------------------------------------------------------------
    // Muc 3.3 — payload chi duoc dung lam desired khi DU TRUONG
    // ------------------------------------------------------------------
    @Test
    @DisplayName("payload du truong thi dung duoc lam desired, thieu thi phai doc lai nguon")
    void detectsIncompletePayload() throws Exception {
        assertThat(student("""
                {"studentId":"SV1","firstName":"An","lastName":"Nguyen Van",
                 "email":"an@sv.edu.vn","status":"ACTIVE"}""")
                .isCompleteEnoughForSync()).isTrue();

        // thieu email -> khong du de ghi de nguon su that
        assertThat(student("""
                {"studentId":"SV1","lastName":"Nguyen Van","status":"ACTIVE"}""")
                .isCompleteEnoughForSync()).isFalse();

        // thieu status -> khong suy ra duoc enabled
        assertThat(student("""
                {"studentId":"SV1","lastName":"Nguyen Van","email":"an@sv.edu.vn"}""")
                .isCompleteEnoughForSync()).isFalse();
    }

    // ------------------------------------------------------------------
    // U01 — quy doi thang diem cho INT-06. De bai muc 16.7 neu dich danh
    //       ba gia tri phai thu: 87.5, 0 va 100.
    // ------------------------------------------------------------------
    private LmsGradePublished grade(double finalGrade) throws Exception {
        return LmsGradePublished.from(mapper.readTree("""
                {"gradeId":"1","userExternalRef":"SV07LAB01",
                 "courseExternalCode":"SEC-TEAM07-LAB01","finalGrade":%s}"""
                .formatted(finalGrade)));
    }

    @ParameterizedTest(name = "U01: {0}/100 -> {1}/10")
    @CsvSource({
            "87.5,  8.75",
            "0,     0.00",
            "100,  10.00",
            "45,    4.50",
            "99.9,  9.99"
    })
    @DisplayName("U01 — quy doi thang 100 sang thang 10: round(finalGrade / 10, 2)")
    void convertsGradeScale(double from, String expected) throws Exception {
        assertThat(grade(from).toScoreOutOfTen()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("U01 — lam tron HALF_UP o chu so thu hai, khong dung so thuc nhi phan")
    void roundsHalfUp() throws Exception {
        // Day la DIEM CHINH THUC cua sinh vien: lam tron phai on dinh va doan truoc duoc
        assertThat(grade(87.55).toScoreOutOfTen()).isEqualByComparingTo("8.76");
        assertThat(grade(87.54).toScoreOutOfTen()).isEqualByComparingTo("8.75");
    }

    @Test
    @DisplayName("U01 — diem ngoai thang 0..100 bi tu choi TRUOC khi goi API")
    void rejectsOutOfRangeGrade() throws Exception {
        assertThat(grade(-1).isGradeInRange()).isFalse();
        assertThat(grade(100.1).isGradeInRange()).isFalse();
        assertThat(grade(0).isGradeInRange()).isTrue();
        assertThat(grade(100).isGradeInRange()).isTrue();
    }

    @Test
    @DisplayName("U01 — thieu external reference thi khong du de ghi diem")
    void requiresExternalReferences() throws Exception {
        // userId/courseId noi bo cua LMS KHONG thay the duoc external reference (BV-4)
        LmsGradePublished onlyInternalIds = LmsGradePublished.from(mapper.readTree("""
                {"gradeId":"1","userId":48329,"courseId":9849,"finalGrade":87.5}"""));
        assertThat(onlyInternalIds.hasRequiredFields()).isFalse();
    }

    // ------------------------------------------------------------------
    // U09 — doc moc thoi gian. Hai kenh cua he thong nguon tra ve HAI dinh dang
    //       khac nhau; neu chi dung Instant.parse thi diem moc polling khong bao
    //       gio tien va bo doc se tai lai toan bo bang su kien moi 10 giay.
    // ------------------------------------------------------------------
    @Test
    @DisplayName("U09 — doc duoc ca ba dinh dang moc thoi gian ma lab tra ve")
    void parsesEveryTimestampShapeTheLabEmits() {
        Instant expected = Instant.parse("2026-08-15T01:51:15.196920Z");

        // webhook: isoformat() cua Python co offset
        assertThat(Timestamps.parse("2026-08-15T01:51:15.196920+00:00")).isEqualTo(expected);
        // GET /events: SQLite lam rot mui gio
        assertThat(Timestamps.parse("2026-08-15T01:51:15.196920")).isEqualTo(expected);
        // ben trong payload: json.dumps(default=str) dung dau cach thay chu T
        assertThat(Timestamps.parse("2026-08-15 01:51:15.196920")).isEqualTo(expected);
        // dang chuan co chu Z
        assertThat(Timestamps.parse("2026-08-15T01:51:15.196920Z")).isEqualTo(expected);

        assertThat(Timestamps.parse(null)).isNull();
        assertThat(Timestamps.parse("  ")).isNull();
        assertThat(Timestamps.parse("khong-phai-thoi-gian")).isNull();
    }

    // ------------------------------------------------------------------
    // I15 (phan don vi) — NFR-07, khong bao gio de lot bi mat ra log
    // ------------------------------------------------------------------
    @Test
    @DisplayName("I15 — bo che lam mo token va client secret")
    void redactsSecrets() {
        assertThat(ApiClient.redact("""
                {"accessToken":"eyJhbGciOiJIUzI1NiJ9.abc","tenantId":"TEAM07"}"""))
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .contains("TEAM07");

        assertThat(ApiClient.redact("""
                {"clientSecret":"s3cr3t-value"}"""))
                .doesNotContain("s3cr3t-value");

        assertThat(ApiClient.redact("Authorization: Bearer eyJhbGciOi.xyz123"))
                .doesNotContain("eyJhbGciOi.xyz123");
    }
}
