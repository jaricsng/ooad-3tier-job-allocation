package edu.ooad.joballoc.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ooad.joballoc.App;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration patterns over real HTTP and PostgreSQL: Problem Details, Correlation Identifier,
 * Idempotent Receiver, ETag / If-Match optimistic concurrency, and the Location target.
 */
public class ApiIntegrationPatternsIT {

    private static App.Running app;
    private static String base;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SUPERVISOR = "Basic " + Base64.getEncoder()
            .encodeToString("supervisor:Supervisor#2026".getBytes(StandardCharsets.UTF_8));

    /** A pending job and a worker who can take it. */
    private record Pick(String jobId, long version, String workerId) { }

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

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", SUPERVISOR);
    }

    private static HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder postJson(String path, String body) {
        return request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private static Pick pick() throws Exception {
        for (JsonNode job : JSON.readTree(send(request("/api/v1/jobs")).body())) {
            String jobId = job.get("jobId").asText();
            for (JsonNode c : JSON.readTree(send(request("/api/v1/jobs/" + jobId + "/eligible-workers")).body())) {
                if (c.get("eligible").asBoolean()) {
                    return new Pick(jobId, job.get("version").asLong(), c.get("workerId").asText());
                }
            }
        }
        throw new AssertionError("demo data has no allocatable job");
    }

    private static String body(Pick p, Long version) {
        return "{\"jobId\":\"" + p.jobId() + "\",\"workerId\":\"" + p.workerId() + "\""
                + (version == null ? "" : ",\"jobVersion\":" + version) + "}";
    }

    @Test
    public void errorsAreProblemDetailsWithTheCorrelationId() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/jobs/NOPE").header("X-Request-Id", "trace-12345678"));
        assertEquals(404, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals("trace-12345678", r.headers().firstValue("X-Request-Id").orElse(""));
        JsonNode problem = JSON.readTree(r.body());
        assertEquals("/problems/not-found", problem.get("type").asText());
        assertEquals(404, problem.get("status").asInt());
        assertEquals("/api/v1/jobs/NOPE", problem.get("instance").asText());
        assertEquals("trace-12345678", problem.get("requestId").asText());
    }

    @Test
    public void malformedCorrelationIdIsReplaced() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/jobs").header("X-Request-Id", "bad id; drop table"));
        String id = r.headers().firstValue("X-Request-Id").orElse("");
        assertNotEquals("bad id; drop table", id);
        assertEquals("a generated UUID", 36, id.length());
    }

    @Test
    public void etagSupportsRevalidationAndIfMatch() throws Exception {
        Pick p = pick();
        HttpResponse<String> job = send(request("/api/v1/jobs/" + p.jobId()));
        String etag = job.headers().firstValue("ETag").orElseThrow();
        assertEquals("\"" + p.version() + "\"", etag);
        assertEquals(304, send(request("/api/v1/jobs/" + p.jobId()).header("If-None-Match", etag)).statusCode());

        HttpResponse<String> stale = send(postJson("/api/v1/allocations", body(p, null))
                .header("If-Match", "\"" + (p.version() + 7) + "\""));
        assertEquals("stale If-Match is 412 Precondition Failed", 412, stale.statusCode());

        HttpResponse<String> ok = send(postJson("/api/v1/allocations", body(p, null)).header("If-Match", etag));
        assertEquals(201, ok.statusCode());
        String location = ok.headers().firstValue("Location").orElseThrow();
        HttpResponse<String> created = send(request(location));
        assertEquals("the Location header points to a real resource", 200, created.statusCode());
        assertEquals(p.jobId(), JSON.readTree(created.body()).get("jobId").asText());
    }

    @Test
    public void idempotencyKeyMakesRetriesSafe() throws Exception {
        Pick p = pick();
        String key = UUID.randomUUID().toString();
        HttpResponse<String> first = send(postJson("/api/v1/allocations", body(p, p.version()))
                .header("Idempotency-Key", key));
        assertEquals(201, first.statusCode());
        assertFalse(first.headers().firstValue("Idempotent-Replayed").isPresent());

        HttpResponse<String> retry = send(postJson("/api/v1/allocations", body(p, p.version()))
                .header("Idempotency-Key", key));
        assertEquals("retry gets the stored response, not a 409 or a second allocation", 201, retry.statusCode());
        assertEquals("true", retry.headers().firstValue("Idempotent-Replayed").orElse(""));
        assertEquals(JSON.readTree(first.body()).get("allocationId"), JSON.readTree(retry.body()).get("allocationId"));

        HttpResponse<String> misuse = send(postJson("/api/v1/allocations", body(p, p.version() + 1))
                .header("Idempotency-Key", key));
        assertEquals("same key, different body", 422, misuse.statusCode());

        HttpResponse<String> noKey = send(postJson("/api/v1/allocations", body(p, p.version())));
        assertEquals("without a key the retry is a real second attempt: stale version", 409, noKey.statusCode());
    }

    @Test
    public void theApiServesItsOwnContract() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/openapi.yaml"));
        assertEquals(200, r.statusCode());
        assertTrue(r.body().startsWith("openapi: 3.1.0"));
    }
}
