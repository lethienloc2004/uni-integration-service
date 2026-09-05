package vn.thanhdo.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bo boc MOI loi goi HTTP toi UniSIS va UniLearn.
 *
 * <p>Trach nhiem (muc 5.2 cua De xuat giai phap v2.0):
 * <ul>
 *   <li>Tu lay lai token khi gap 401 — trong suot voi tang nghiep vu</li>
 *   <li>Dieu tiet theo token bucket truoc MOI luot goi (NFR-12)</li>
 *   <li>Phan loai loi theo hop dong 4.4</li>
 *   <li>Che token va client secret trong log (NFR-07)</li>
 * </ul>
 *
 * <p>Luu y: lop nay KHONG thu lai 429/503. Viec thu lai duoc luu ben trong bang
 * inbox va do Worker dieu phoi, de trang thai thu lai song sot qua khoi dong lai.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private final String system;          // "SIS" hoac "LMS"
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final RateLimiter rateLimiter;
    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public ApiClient(String system, String baseUrl, String clientId, String clientSecret,
                     String tenantId, RateLimiter rateLimiter) {
        this.system = system;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.rateLimiter = rateLimiter;

        // Ep HTTP/1.1: mac dinh JDK HttpClient thu nang cap len HTTP/2 bang co che
        // Upgrade tren ket noi http:// khong ma hoa. Nhieu may chu — ke ca WireMock
        // dung trong bo kiem thu — dong ket noi thay vi tu choi tu te, gay EOFException.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build());
        factory.setReadTimeout(Duration.ofSeconds(15));

        this.http = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .build();
    }

    // ------------------------------------------------------------------
    // API cong khai
    // ------------------------------------------------------------------

    /*
     * MOI phuong thuc nhan MOT MAU URI cung cac BIEN, vi du:
     *     get("/api/v1/users?externalRef={ref}", studentId)
     *
     * TUYET DOI khong tu goi URLEncoder roi ghep chuoi: RestClient se ma hoa mot lan nua,
     * bien dau hai cham thanh %253A va he thong nguon tra 422. Loi nay am tham vi bo doc
     * chi ghi mot dong canh bao roi thu lai luot sau — nhin tu ben ngoai giong het "khong
     * co su kien moi".
     */

    public JsonNode get(String path, Object... uriVars) {
        return exchange(HttpMethod.GET, path, null, uriVars);
    }

    public JsonNode post(String path, Object body, Object... uriVars) {
        return exchange(HttpMethod.POST, path, body, uriVars);
    }

    public JsonNode patch(String path, Object body, Object... uriVars) {
        return exchange(HttpMethod.PATCH, path, body, uriVars);
    }

    public JsonNode put(String path, Object body, Object... uriVars) {
        return exchange(HttpMethod.PUT, path, body, uriVars);
    }

    public JsonNode delete(String path, Object... uriVars) {
        return exchange(HttpMethod.DELETE, path, null, uriVars);
    }

    /**
     * Doc mot danh sach va tra ve phan tu dau tien, neu co.
     * Dung cho cac phep tra cuu theo khoa nghiep vu nhu
     * {@code GET /api/v1/users?externalRef=SV010001}.
     */
    public Optional<JsonNode> getFirst(String path, Object... uriVars) {
        List<JsonNode> items = getList(path, uriVars);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.getFirst());
    }

    /**
     * Doc mot danh sach, chap nhan ca hai dang vo boc thuong gap:
     * mang thuan {@code [...]} hoac doi tuong co truong content/items/data/results.
     *
     * <p>Viet khoan dung nhu vay vi hinh dang vo boc cua lab CHUA duoc xac minh
     * bang Swagger that. Khi co credentials, hay kiem tra lai va thu gon neu can.
     */
    public List<JsonNode> getList(String path, Object... uriVars) {
        return unwrapList(get(path, uriVars));
    }

    public List<JsonNode> unwrapList(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null || node.isNull()) {
            return out;
        }
        JsonNode array = node;
        if (!node.isArray()) {
            for (String field : List.of("content", "items", "data", "results")) {
                if (node.has(field) && node.get(field).isArray()) {
                    array = node.get(field);
                    break;
                }
            }
        }
        if (array.isArray()) {
            array.forEach(out::add);
        } else if (array.isObject()) {
            out.add(array);   // API tra ve mot doi tuong don le
        }
        return out;
    }

    public String getSystem() { return system; }

    public RateLimiter getRateLimiter() { return rateLimiter; }

    /** Dung cho /health — kiem tra lay duoc token bang credentials hien tai. */
    public boolean canAuthenticate() {
        try {
            fetchToken();
            return true;
        } catch (Exception e) {
            log.warn("[{}] khong lay duoc token: {}", system, redact(e.getMessage()));
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Loi goi thuc te
    // ------------------------------------------------------------------

    private JsonNode exchange(HttpMethod method, String path, Object body, Object... uriVars) {
        return doExchange(method, path, body, false, uriVars);
    }

    private JsonNode doExchange(HttpMethod method, String path, Object body,
                                boolean isRetryAfterAuth, Object... uriVars) {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.transientFailure(system, path, e);
        }

        Response resp;
        try {
            String token = currentToken();
            resp = send(method, path, body, token, uriVars);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.transientFailure(system, path, e);
        }

        // 401 — lay token moi roi goi lai DUNG mot lan (hop dong loi 4.4)
        if (resp.status == 401 && !isRetryAfterAuth) {
            log.debug("[{}] {} tra 401, lam moi token roi goi lai mot lan", system, path);
            invalidateToken();
            return doExchange(method, path, body, true, uriVars);
        }

        if (resp.status >= 200 && resp.status < 300) {
            return parse(resp.body);
        }

        throw new ApiException(system, resp.status, path, truncate(redact(resp.body)),
                ErrorClass.fromHttpStatus(resp.status), resp.retryAfter);
    }

    private Response send(HttpMethod method, String path, Object body, String token,
                          Object... uriVars) {
        RestClient.RequestBodySpec spec = http.method(method)
                .uri(path, uriVars)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tenant-ID", tenantId)
                .accept(MediaType.APPLICATION_JSON);

        RestClient.RequestHeadersSpec<?> ready = (body != null)
                ? spec.contentType(MediaType.APPLICATION_JSON).body(body)
                : spec;

        return toResponse(ready);
    }

    /**
     * Tat xu ly loi mac dinh cua RestClient de TU phan loai theo hop dong 4.4.
     * Neu de RestClient nem ngoai le, ta mat mat ma trang thai va header Retry-After.
     */
    private Response toResponse(RestClient.RequestHeadersSpec<?> spec) {
        ResponseEntity<String> re = spec.retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(String.class);

        return new Response(
                re.getStatusCode().value(),
                re.getBody(),
                parseRetryAfter(re.getHeaders().getFirst("Retry-After")));
    }

    // ------------------------------------------------------------------
    // Token
    // ------------------------------------------------------------------

    private String currentToken() {
        String t = accessToken;
        if (t != null && Instant.now().isBefore(tokenExpiresAt)) {
            return t;
        }
        return fetchToken();
    }

    private synchronized String fetchToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;   // mot luong khac vua lam moi xong
        }
        Map<String, String> payload = Map.of(
                "clientId", clientId,
                "clientSecret", clientSecret,
                "tenantId", tenantId);

        Response resp;
        try {
            resp = toResponse(http.post()
                    .uri("/api/v1/auth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // He thong nguon chua san sang hoac mang chap chon. Day la loi TAM THOI:
            // phai thu lai chu khong duoc day vao dead-letter, neu khong thi khoi dong
            // dich vu truoc khi lab san sang se lam mat toan bo su kien dau tien.
            throw ApiException.transientFailure(system, "/api/v1/auth/token", e);
        }

        if (resp.status < 200 || resp.status >= 300) {
            throw new ApiException(system, resp.status, "/api/v1/auth/token",
                    truncate(redact(resp.body)), ErrorClass.fromHttpStatus(resp.status), null);
        }

        JsonNode node = parse(resp.body);
        String token = firstNonBlank(node, "accessToken", "access_token", "token");
        if (token == null) {
            throw new ApiException(system, resp.status, "/api/v1/auth/token",
                    "khong tim thay truong accessToken trong phan hoi",
                    ErrorClass.DATA_ERROR, null);
        }

        long expiresIn = node.has("expiresIn") ? node.get("expiresIn").asLong(43200) : 43200;
        this.accessToken = token;
        // tru 60 giay bien an toan de khong dung token sap het han
        this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));

        log.info("[{}] da lay token moi, het han sau {} giay", system, expiresIn);
        return token;
    }

    private synchronized void invalidateToken() {
        this.accessToken = null;
        this.tokenExpiresAt = Instant.EPOCH;
    }

    // ------------------------------------------------------------------
    // Tien ich
    // ------------------------------------------------------------------

    private JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return mapper.nullNode();
        }
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return mapper.nullNode();
        }
    }

    private static Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return null;   // dang HTTP-date — bo qua, roi ve backoff mac dinh
        }
    }

    private static String firstNonBlank(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.hasNonNull(f) && !node.get(f).asText().isBlank()) {
                return node.get(f).asText();
            }
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 900 ? s : s.substring(0, 900) + "...";
    }

    /**
     * Che bi mat truoc khi ghi log (NFR-07).
     * Ca kiem thu I15 quet lai toan bo log sinh ra trong bo kiem thu.
     */
    public static String redact(String s) {
        if (s == null) return null;
        return s
                // Khong khop khi "token" la mot doan duong dan URL nhu /auth/token —
                // neu khong, bo che se nuot luon thong bao loi that va rat kho go loi.
                .replaceAll(
                        "(?i)(?<![\\w/])(accessToken|access_token|token|clientSecret|client_secret|password)(\"?\\s*[:=]\\s*\"?)([^\",}\\s]+)",
                        "$1$2***")
                .replaceAll("(?i)(Bearer\\s+)([A-Za-z0-9._\\-]+)", "$1***");
    }

    private record Response(int status, String body, Duration retryAfter) { }
}
