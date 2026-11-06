package edu.ooad.joballoc.web.sso;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal OpenID Connect provider for tests: discovery, an authorize endpoint that signs the user in
 * at once and redirects back with a code, a token endpoint that returns an RS256-signed ID token, and
 * JWKS. Tests choose who signs in (email, verified or not) and can make it misbehave.
 */
public final class FakeIdentityProvider implements AutoCloseable {

    /** Who the next sign-in is, and how the provider behaves (fluent setters for readable tests). */
    public static final class Next {
        private String subject = "sub-123";
        private String email = "supervisor@example.com";
        private boolean emailVerified = true;
        private String audience;
        private long expiresInSeconds = 300;
        private boolean signWithOtherKey;

        public Next email(String v) {
            email = v;
            return this;
        }

        public Next unverified() {
            emailVerified = false;
            return this;
        }

        public Next audience(String v) {
            audience = v;
            return this;
        }

        public Next expiresIn(long seconds) {
            expiresInSeconds = seconds;
            return this;
        }

        public Next signedWithOtherKey() {
            signWithOtherKey = true;
            return this;
        }
    }

    public static final String CLIENT_ID = "joballoc-test-client";
    public static final String CLIENT_SECRET = "test-secret";

    private final HttpServer server;
    private final KeyPair key;
    private final KeyPair otherKey;
    private final Map<String, String[]> codes = new ConcurrentHashMap<>();
    private volatile Next next = new Next();

    public FakeIdentityProvider() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.key = gen.generateKeyPair();
        this.otherKey = gen.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", ex -> send(ex, 200, "application/json", discovery()));
        server.createContext("/jwks", ex -> send(ex, 200, "application/json", jwks()));
        server.createContext("/authorize", this::authorize);
        server.createContext("/token", this::token);
        server.start();
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public Next next() {
        next = new Next();
        return next;
    }

    private String discovery() {
        return "{\"issuer\":\"" + issuer() + "\",\"authorization_endpoint\":\"" + issuer() + "/authorize\","
                + "\"token_endpoint\":\"" + issuer() + "/token\",\"jwks_uri\":\"" + issuer() + "/jwks\"}";
    }

    private String jwks() {
        RSAPublicKey pub = (RSAPublicKey) key.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
                + b64(pub.getModulus())
                + "\",\"e\":\"" + b64(pub.getPublicExponent()) + "\"}]}";
    }

    /** Signs the user in immediately and redirects back with a one-time code. */
    private void authorize(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        String code = UUID.randomUUID().toString();
        codes.put(code, new String[] {q.get("nonce"), q.get("code_challenge"), q.get("redirect_uri")});
        ex.getResponseHeaders().add("Location", q.get("redirect_uri") + "?code=" + code + "&state=" + q.get("state"));
        send(ex, 302, "text/plain", "");
    }

    /** Checks the client secret, the PKCE verifier and the redirect URI, then issues a signed ID token. */
    private void token(HttpExchange ex) throws IOException {
        Map<String, String> f = query(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String[] flow = codes.remove(f.get("code"));
        boolean pkceOk = flow != null
                && (flow[1] == null || flow[1].equals(SsoService.challenge(f.get("code_verifier"))));
        boolean clientOk = CLIENT_SECRET.equals(f.get("client_secret"));
        if (flow == null || !clientOk || !pkceOk || !flow[2].equals(f.get("redirect_uri"))) {
            send(ex, 400, "application/json", "{\"error\":\"invalid_grant\"}");
            return;
        }
        send(ex, 200, "application/json", "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"id_token\":\""
                + idToken(flow[0]) + "\"}");
    }

    public String idToken(String nonce) {
        Next n = next;
        long now = Instant.now().getEpochSecond();
        String claims = "{\"iss\":\"" + issuer() + "\",\"sub\":\"" + n.subject + "\",\"aud\":\""
                + (n.audience == null ? CLIENT_ID : n.audience) + "\",\"iat\":" + now + ",\"exp\":"
                + (now + n.expiresInSeconds) + ",\"nonce\":\"" + nonce + "\",\"email\":\"" + n.email
                + "\",\"email_verified\":" + n.emailVerified + ",\"name\":\"Test Person\"}";
        PrivateKey signer = n.signWithOtherKey ? otherKey.getPrivate() : key.getPrivate();
        return sign("{\"alg\":\"RS256\",\"kid\":\"k1\"}", claims, signer);
    }

    public static String sign(String header, String claims, PrivateKey privateKey) {
        try {
            String signed = b64(header.getBytes(StandardCharsets.UTF_8)) + "."
                    + b64(claims.getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance(privateKey.getAlgorithm().equals("EC") ? "SHA256withECDSAinP1363Format"
                    : "SHA256withRSA");
            s.initSign(privateKey);
            s.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + "." + b64(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String jwksJson() {
        return jwks();
    }

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String b64(BigInteger i) {
        byte[] b = i.toByteArray();
        if (b[0] == 0) {
            b = java.util.Arrays.copyOfRange(b, 1, b.length);
        }
        return b64(b);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) {
            return m;
        }
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }

    public URI uri(String path) {
        return URI.create(issuer() + path);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
