package edu.ooad.joballoc.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ooad.joballoc.App;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end over HTTP through all three tiers with a real database: the REST contract (201, 204,
 * 400, 401, 403, 404, 409, 422) and security controls mapped to OWASP Top 10 / API Top 10.
 */
public class ApiSecurityIT {

    private static App.Running app;
    private static String base;
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SUPERVISOR = basic("supervisor", "Supervisor#2026");
    private static final String VIEWER = basic("viewer", "Viewer#2026");

    @BeforeClass
    public static void start() throws Exception {
        TestDatabase.reset();
        app = App.start(TestDatabase.config(), Clock.systemDefaultZone());
        base = "http://127.0.0.1:" + app.web().port();
    }

    @AfterClass
    public static void stop() {
        app.stop();
    }

    private static String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path, String auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return send(b);
    }

    private static HttpResponse<String> postJson(String path, String auth, String body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", auth)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static HttpResponse<String> postForm(String path, String cookie, String form) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return send(b);
    }

    /** Signs in like a browser: GET /login for the token and its cookie, then POST the form with both. */
    private static HttpResponse<String> signIn(String form) throws Exception {
        HttpResponse<String> page = get("/login", null);
        String loginCookie = page.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertTrue("sign-in form carries a token", m.find());
        String token = URLEncoder.encode(m.group(1), StandardCharsets.UTF_8);
        return postForm("/login", loginCookie, form + "&_csrf=" + token);
    }

    private static JsonNode firstPendingJob(String skill) throws Exception {
        JsonNode jobs = JSON.readTree(get("/api/v1/jobs?skill=" + skill, SUPERVISOR).body());
        assertTrue("demo data has a pending " + skill + " job", jobs.size() > 0);
        return jobs.get(0);
    }

    @Test
    public void healthIsPublicAndReportsDatabase() throws Exception {
        HttpResponse<String> r = get("/health", null);
        assertEquals(200, r.statusCode());
        assertEquals("UP", JSON.readTree(r.body()).get("database").asText());
    }

    @Test
    public void apiRequiresAuthentication() throws Exception {
        HttpResponse<String> r = get("/api/v1/jobs", null);
        assertEquals(401, r.statusCode());
        assertTrue(r.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic"));
        assertEquals(401, get("/api/v1/jobs", basic("supervisor", "wrong")).statusCode());
        HttpResponse<String> page = get("/board", null);
        assertEquals("pages redirect to sign-in", 303, page.statusCode());
        assertEquals("/login", page.headers().firstValue("Location").orElse(""));
    }

    @Test
    public void allocateFollowsTheContract() throws Exception {
        JsonNode job = firstPendingJob("DBA");
        String id = job.get("jobId").asText();
        long version = job.get("version").asLong();

        HttpResponse<String> wrongSkill = postJson("/api/v1/allocations", SUPERVISOR,
                "{\"jobId\":\"" + id + "\",\"workerId\":\"W-04\",\"jobVersion\":" + version + "}");
        assertEquals("rule violation", 422, wrongSkill.statusCode());

        HttpResponse<String> ok = postJson("/api/v1/allocations", SUPERVISOR,
                "{\"jobId\":\"" + id + "\",\"workerId\":\"W-03\",\"jobVersion\":" + version + "}");
        assertEquals(201, ok.statusCode());
        String allocationId = JSON.readTree(ok.body()).get("allocationId").asText();
        assertTrue(ok.headers().firstValue("Location").orElse("").endsWith(allocationId));

        HttpResponse<String> stale = postJson("/api/v1/allocations", SUPERVISOR,
                "{\"jobId\":\"" + id + "\",\"workerId\":\"W-03\",\"jobVersion\":" + version + "}");
        assertEquals("optimistic lock", 409, stale.statusCode());

        JsonNode allocations = JSON.readTree(get("/api/v1/allocations", VIEWER).body());
        assertTrue(allocations.toString().contains(allocationId));

        HttpResponse<String> del = send(HttpRequest.newBuilder(URI.create(base + "/api/v1/allocations/" + allocationId))
                .header("Authorization", SUPERVISOR).DELETE());
        assertEquals(204, del.statusCode());
        assertEquals("already released", 422, send(HttpRequest.newBuilder(URI.create(base + "/api/v1/allocations/"
                + allocationId))
                .header("Authorization", SUPERVISOR).DELETE()).statusCode());
    }

    @Test
    public void viewerCannotChangeData() throws Exception {
        JsonNode job = firstPendingJob("LINUX");
        HttpResponse<String> r = postJson("/api/v1/allocations", VIEWER, "{\"jobId\":\"" + job.get("jobId").asText()
                + "\",\"workerId\":\"W-02\",\"jobVersion\":" + job.get("version").asLong() + "}");
        assertEquals("broken function-level authorisation prevented (API5)", 403, r.statusCode());
        assertEquals(200, get("/api/v1/jobs", VIEWER).statusCode());
    }

    @Test
    public void rejectsMalformedInput() throws Exception {
        assertEquals(400, postJson("/api/v1/allocations", SUPERVISOR, "{not json").statusCode());
        assertEquals("unknown fields rejected (mass assignment, API3)", 400,
                postJson("/api/v1/allocations", SUPERVISOR,
                        "{\"jobId\":\"J\",\"workerId\":\"W\",\"jobVersion\":0,\"status\":\"DONE\"}").statusCode());
        assertEquals(400, postJson("/api/v1/allocations", SUPERVISOR, "{\"jobId\":\"J\"}").statusCode());
        assertEquals(400, get("/api/v1/jobs?date=not-a-date", SUPERVISOR).statusCode());
        assertEquals(404, postJson("/api/v1/allocations", SUPERVISOR,
                "{\"jobId\":\"NOPE\",\"workerId\":\"W-01\",\"jobVersion\":0}").statusCode());
        assertEquals(404, get("/api/v1/jobs/NOPE/eligible-workers", SUPERVISOR).statusCode());
        assertEquals(405, send(HttpRequest.newBuilder(URI.create(base + "/api/v1/jobs")).header("Authorization",
                SUPERVISOR)
                .PUT(HttpRequest.BodyPublishers.noBody())).statusCode());
        String big = "x".repeat(70_000);
        assertEquals(413, postJson("/api/v1/allocations", SUPERVISOR, big).statusCode());
    }

    @Test
    public void autoAllocateReportsEachJob() throws Exception {
        JsonNode job = firstPendingJob("JAVA");
        HttpResponse<String> r = postJson("/api/v1/allocations/auto", SUPERVISOR,
                "{\"jobIds\":[\"" + job.get("jobId").asText() + "\"],\"policy\":\"LEAST_LOADED\"}");
        assertEquals(200, r.statusCode());
        JsonNode result = JSON.readTree(r.body()).get(0);
        assertTrue(result.get("allocated").asBoolean());
        assertEquals("only Erin has JAVA+SECURITY", "W-05", result.get("workerId").asText());
    }

    @Test
    public void securityHeadersOnEveryResponse() throws Exception {
        for (HttpResponse<String> r : java.util.List.of(get("/login", null), get("/api/v1/jobs", VIEWER), get("/nope",
                null))) {
            assertTrue(r.headers().firstValue("Content-Security-Policy").orElse("").contains("script-src 'none'"));
            assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(""));
            assertEquals("DENY", r.headers().firstValue("X-Frame-Options").orElse(""));
            assertEquals("no-referrer", r.headers().firstValue("Referrer-Policy").orElse(""));
        }
    }

    @Test
    public void sessionCookieIsHardenedAndCsrfIsEnforced() throws Exception {
        assertEquals("login CSRF: a form without its token is refused", 403,
                postForm("/login", null, "username=supervisor&password=Supervisor%232026").statusCode());
        HttpResponse<String> bad = signIn("username=supervisor&password=nope");
        assertEquals(401, bad.statusCode());
        String failedCookie = bad.headers().firstValue("Set-Cookie").orElse("");
        assertFalse("no session for a failed sign-in", failedCookie.startsWith("JA_SESSION"));

        HttpResponse<String> login = signIn("username=supervisor&password=Supervisor%232026");
        assertEquals(303, login.statusCode());
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        String cookie = setCookie.split(";")[0];

        String board = send(HttpRequest.newBuilder(URI.create(base + "/board")).header("Cookie", cookie).GET()).body();
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(board);
        assertTrue(m.find());
        String csrf = m.group(1);

        JsonNode job = firstPendingJob("NETWORK");
        String form = "jobId=" + job.get("jobId").asText() + "&workerId=W-04&jobVersion=" + job.get("version").asLong();
        assertEquals("missing CSRF token", 403, postForm("/board/allocate", cookie, form).statusCode());
        assertEquals("wrong CSRF token", 403, postForm("/board/allocate", cookie, form + "&_csrf=forged").statusCode());
        HttpResponse<String> ok = postForm("/board/allocate", cookie, form + "&_csrf=" + URLEncoder.encode(csrf,
                StandardCharsets.UTF_8));
        assertEquals(303, ok.statusCode());
        assertTrue(ok.headers().firstValue("Location").orElse("").contains("msg=Allocated"));

        HttpResponse<String> logout = postForm("/logout", cookie, "_csrf=" + URLEncoder.encode(csrf,
                StandardCharsets.UTF_8));
        assertEquals(303, logout.statusCode());
        assertEquals("session invalidated", 303,
                send(HttpRequest.newBuilder(URI.create(base + "/board")).header("Cookie", cookie).GET()).statusCode());
    }

    @Test
    public void reflectedInputIsEscaped() throws Exception {
        HttpResponse<String> login = signIn("username=viewer&password=Viewer%232026");
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        String payload = URLEncoder.encode("<script>alert(1)</script>", StandardCharsets.UTF_8);
        String page = send(HttpRequest.newBuilder(URI.create(base + "/board?err=" + payload + "&skill=" + payload))
                .header("Cookie", cookie).GET()).body();
        assertFalse("XSS payload must not appear unescaped (A03)", page.contains("<script>alert(1)</script>"));
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse("viewers get no Allocate buttons", page.contains("action=\"/board/allocate\""));
    }

    @Test
    public void staticFilesAreAllowListed() throws Exception {
        assertEquals(200, get("/static/app.css", null).statusCode());
        assertEquals(404, get("/static/..%2Fdb%2Fmigration%2FV1__schema.sql", null).statusCode());
        assertEquals(404, get("/static/V1__schema.sql", null).statusCode());
        assertNotNull(app.service());
    }
}
