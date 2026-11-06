package edu.ooad.joballoc.web.sso;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The HTTP conversations with an identity provider: discovery (/.well-known/openid-configuration),
 * its signing keys (JWKS, cached and refreshed when an unknown key id appears, at most once a minute)
 * and the back-channel code-for-token exchange. Only HTTPS endpoints are accepted, except loopback
 * (tests and local identity providers).
 */
public final class OidcClient {

    /** The endpoints a provider publishes in its discovery document. */
    public record Metadata(String issuer, String authorizationEndpoint, String tokenEndpoint, String jwksUri) { }

    private record Keys(JsonNode jwks, Instant fetched) { }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration KEY_REFRESH_MIN = Duration.ofMinutes(1);
    private static final int HTTP_OK = 200;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Clock clock;
    private final Map<String, Metadata> metadata = new ConcurrentHashMap<>();
    private final Map<String, Keys> keys = new ConcurrentHashMap<>();

    public OidcClient(Clock clock) {
        this.clock = clock;
    }

    public Metadata metadata(OidcProvider p) throws IOException {
        Metadata m = metadata.get(p.id());
        if (m == null) {
            JsonNode doc = getJson(p.issuer().replaceAll("/$", "") + "/.well-known/openid-configuration");
            m = new Metadata(doc.path("issuer").asText(), safeUrl(doc.path("authorization_endpoint").asText()),
                    safeUrl(doc.path("token_endpoint").asText()), safeUrl(doc.path("jwks_uri").asText()));
            metadata.put(p.id(), m);
        }
        return m;
    }

    /** The JWK with this key id; refetches the key set once if the id is unknown (key rotation). */
    public JsonNode key(OidcProvider p, String kid) {
        try {
            Keys k = keys.get(p.id());
            JsonNode found = k == null ? null : find(k.jwks(), kid);
            if (found == null && (k == null || k.fetched().plus(KEY_REFRESH_MIN).isBefore(clock.instant()))) {
                k = new Keys(getJson(metadata(p).jwksUri()), clock.instant());
                keys.put(p.id(), k);
                found = find(k.jwks(), kid);
            }
            return found;
        } catch (IOException e) {
            return null;
        }
    }

    /** Exchanges the authorization code for tokens at the token endpoint (client_secret_post). */
    public JsonNode exchange(OidcProvider p, String code, String redirectUri, String codeVerifier) throws IOException {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", p.clientId());
        String secret = p.clientSecret().get();
        if (secret != null) {
            form.put("client_secret", secret);
        }
        if (codeVerifier != null) {
            form.put("code_verifier", codeVerifier);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(metadata(p).tokenEndpoint())).timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(urlEncode(form))).build();
        return send(req);
    }

    static String urlEncode(Map<String, String> form) {
        return form.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    private JsonNode getJson(String url) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(safeUrl(url))).timeout(TIMEOUT)
                .header("Accept", "application/json").GET().build());
    }

    private JsonNode send(HttpRequest req) throws IOException {
        try {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != HTTP_OK) {
                throw new IOException(req.uri().getHost() + " answered " + r.statusCode());
            }
            return IdTokenVerifier.parse(r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static JsonNode find(JsonNode jwks, String kid) {
        for (JsonNode k : jwks.path("keys")) {
            if (kid.equals(k.path("kid").asText())) {
                return k;
            }
        }
        return null;
    }

    /** Provider endpoints must be HTTPS; plain HTTP only to loopback (local test identity providers). */
    static String safeUrl(String url) throws IOException {
        URI u = URI.create(url);
        boolean loopback = "127.0.0.1".equals(u.getHost()) || "localhost".equals(u.getHost());
        if (!"https".equals(u.getScheme()) && !("http".equals(u.getScheme()) && loopback)) {
            throw new IOException("identity provider endpoint must use HTTPS: " + url);
        }
        return url;
    }
}
