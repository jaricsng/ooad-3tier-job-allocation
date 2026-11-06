package edu.ooad.joballoc.web.sso;

import com.fasterxml.jackson.databind.JsonNode;
import edu.ooad.joballoc.business.security.ExternalIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenID Connect authorization-code flow for the configured providers.
 * <ul>
 *   <li>start: a random state (CSRF), nonce (replay) and PKCE verifier are kept on the server for
 *       ten minutes; the browser gets the state in a cookie that binds the flow to it;</li>
 *   <li>callback: the state must match both the server record (single use) and the cookie, the code is
 *       exchanged over the back channel, and the ID token is fully verified before anything is trusted.</li>
 * </ul>
 * Errors never reveal why to the browser; the reason goes to the log.
 */
public final class SsoService {

    /** Where the browser goes to start, and the cookie that binds the flow to that browser. */
    public record Start(String redirectUrl, String state) { }

    /** Thrown when single sign-on cannot complete; the message is for the log only. */
    public static final class SsoException extends Exception {
        private static final long serialVersionUID = 1L;

        public SsoException(String message) {
            super(message);
        }
    }

    private record Pending(String provider, String nonce, String verifier, Instant created) { }

    private static final Duration FLOW_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_PENDING = 10_000;
    private static final int RANDOM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OidcProvider> providers = new LinkedHashMap<>();
    private final String baseUrl;
    private final OidcClient client;
    private final IdTokenVerifier verifier;
    private final Clock clock;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public SsoService(List<OidcProvider> providers, String baseUrl, Clock clock) {
        providers.forEach(p -> this.providers.put(p.id(), p));
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.clock = clock;
        this.client = new OidcClient(clock);
        this.verifier = new IdTokenVerifier(clock);
    }

    /** Enabled providers, in button order. */
    public List<OidcProvider> providers() {
        return List.copyOf(providers.values());
    }

    public Optional<OidcProvider> provider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public String redirectUri(OidcProvider p) {
        return baseUrl + "/sso/" + p.id() + "/callback";
    }

    public Start start(OidcProvider p) throws SsoException {
        expireOld();
        if (pending.size() >= MAX_PENDING) {
            throw new SsoException("too many sign-ins in progress");
        }
        String state = random();
        String nonce = random();
        String codeVerifier = p.pkce() ? random() : null;
        pending.put(state, new Pending(p.id(), nonce, codeVerifier, clock.instant()));
        Map<String, String> q = new LinkedHashMap<>();
        q.put("response_type", "code");
        q.put("client_id", p.clientId());
        q.put("redirect_uri", redirectUri(p));
        q.put("scope", p.scopes());
        q.put("state", state);
        q.put("nonce", nonce);
        if (p.formPost()) {
            q.put("response_mode", "form_post");
        }
        if (codeVerifier != null) {
            q.put("code_challenge", challenge(codeVerifier));
            q.put("code_challenge_method", "S256");
        }
        try {
            return new Start(client.metadata(p).authorizationEndpoint() + "?" + OidcClient.urlEncode(q), state);
        } catch (IOException e) {
            pending.remove(state);
            throw new SsoException("discovery failed for " + p.id() + ": " + e.getMessage());
        }
    }

    /** Completes the flow and returns the verified identity. */
    public ExternalIdentity callback(OidcProvider p, String state, String stateCookie, String code, String error)
            throws SsoException {
        if (error != null) {
            throw new SsoException(p.id() + " returned error " + error);
        }
        Pending flow = state == null ? null : pending.remove(state);
        if (flow == null || !flow.provider().equals(p.id())
                || flow.created().plus(FLOW_LIFETIME).isBefore(clock.instant())) {
            throw new SsoException("unknown, used or expired state");
        }
        if (stateCookie == null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                stateCookie.getBytes(StandardCharsets.UTF_8))) {
            throw new SsoException("state does not belong to this browser (login CSRF)");
        }
        if (code == null || code.isBlank()) {
            throw new SsoException("no authorization code");
        }
        try {
            JsonNode tokens = client.exchange(p, code, redirectUri(p), flow.verifier());
            OidcClient.Metadata m = client.metadata(p);
            JsonNode claims = verifier.verify(tokens.path("id_token").asText(null), kid -> client.key(p, kid),
                    c -> issuerMatches(m.issuer(), c), p.clientId(), flow.nonce());
            return new ExternalIdentity(p.id(), claims.path("sub").asText(), claims.path("email").asText(null),
                    IdTokenVerifier.isTrue(claims, p.trustedEmailClaim()), claims.path("name").asText(null));
        } catch (IOException e) {
            throw new SsoException("token exchange failed: " + e.getMessage());
        } catch (IdTokenVerifier.InvalidTokenException e) {
            throw new SsoException("ID token rejected: " + e.getMessage());
        }
    }

    /** Exact match, or Microsoft's multi-tenant template ".../{tenantid}/v2.0" filled with the token's tid. */
    static boolean issuerMatches(String expected, JsonNode claims) {
        String iss = claims.path("iss").asText();
        if (expected.contains("{tenantid}")) {
            return iss.equals(expected.replace("{tenantid}", claims.path("tid").asText("\u0000")));
        }
        return iss.equals(expected);
    }

    private void expireOld() {
        Instant cutoff = clock.instant().minus(FLOW_LIFETIME);
        pending.values().removeIf(p -> p.created().isBefore(cutoff));
    }

    private static String random() {
        byte[] b = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String challenge(String verifier) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
