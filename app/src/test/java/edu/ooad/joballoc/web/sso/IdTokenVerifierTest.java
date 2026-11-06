package edu.ooad.joballoc.web.sso;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The ID token checks of OIDC Core 3.1.3.7, and the attacks each one stops. */
public class IdTokenVerifierTest {

    private static final String CLIENT = FakeIdentityProvider.CLIENT_ID;

    private static JsonNode jwk(FakeIdentityProvider idp) throws Exception {
        return IdTokenVerifier.parse(idp.jwksJson()).path("keys").get(0);
    }

    private static JsonNode verify(FakeIdentityProvider idp, String token, String nonce) throws Exception {
        JsonNode key = jwk(idp);
        return new IdTokenVerifier(Clock.systemUTC()).verify(token, kid -> "k1".equals(kid) ? key : null,
                c -> idp.issuer().equals(c.path("iss").asText()), CLIENT, nonce);
    }

    @Test
    public void aValidTokenIsAccepted() throws Exception {
        try (FakeIdentityProvider idp = new FakeIdentityProvider()) {
            idp.next();
            JsonNode claims = verify(idp, idp.idToken("n-1"), "n-1");
            assertEquals("supervisor@example.com", claims.path("email").asText());
            assertTrue(IdTokenVerifier.isTrue(claims, "email_verified"));
        }
    }

    @Test
    public void everyTamperingIsRejected() throws Exception {
        try (FakeIdentityProvider idp = new FakeIdentityProvider()) {
            idp.next().signedWithOtherKey();
            assertRejected(idp, idp.idToken("n"), "n", "bad signature");
            idp.next().audience("someone-else");
            assertRejected(idp, idp.idToken("n"), "n", "another client");
            idp.next().expiresIn(-3600);
            assertRejected(idp, idp.idToken("n"), "n", "expired");
            idp.next();
            assertRejected(idp, idp.idToken("n"), "other-nonce", "nonce");
            idp.next();
            String token = idp.idToken("n");
            String[] p = token.split("\\.");
            String original = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
            String forged = original.replace("supervisor@example.com", "attacker@example.com");
            String forgedClaims = FakeIdentityProvider.b64(forged.getBytes(StandardCharsets.UTF_8));
            assertRejected(idp, p[0] + "." + forgedClaims + "." + p[2], "n", "bad signature");
        }
    }

    @Test
    public void algorithmNoneAndHmacAreRefused() throws Exception {
        try (FakeIdentityProvider idp = new FakeIdentityProvider()) {
            idp.next();
            String claims = idp.idToken("n").split("\\.")[1];
            String none = FakeIdentityProvider.b64("{\"alg\":\"none\",\"kid\":\"k1\"}"
                    .getBytes(StandardCharsets.UTF_8));
            assertRejected(idp, none + "." + claims + ".", "n", "not a signed JWT");
            assertRejected(idp, none + "." + claims + ".x", "n", "algorithm not allowed");
            String hs = FakeIdentityProvider.b64("{\"alg\":\"HS256\",\"kid\":\"k1\"}".getBytes(StandardCharsets.UTF_8));
            assertRejected(idp, hs + "." + claims + ".x", "n", "algorithm not allowed");
        }
    }

    @Test
    public void es256TokensFromAppleStyleKeysVerify() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = gen.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        JsonNode jwk = IdTokenVerifier.parse("{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\"e1\",\"x\":\""
                + FakeIdentityProvider.b64(fixed32(pub.getW().getAffineX().toByteArray())) + "\",\"y\":\""
                + FakeIdentityProvider.b64(fixed32(pub.getW().getAffineY().toByteArray())) + "\"}");
        long now = Instant.now().getEpochSecond();
        String token = FakeIdentityProvider.sign("{\"alg\":\"ES256\",\"kid\":\"e1\"}",
                "{\"iss\":\"https://appleid.apple.com\","
                + "\"aud\":\"" + CLIENT + "\",\"sub\":\"a\",\"iat\":" + now + ",\"exp\":" + (now + 60)
                + ",\"nonce\":\"n\",\"email\":\"x@privaterelay.appleid.com\",\"email_verified\":\"true\"}",
                kp.getPrivate());
        JsonNode claims = new IdTokenVerifier(Clock.systemUTC()).verify(token, kid -> jwk,
                c -> "https://appleid.apple.com".equals(c.path("iss").asText()), CLIENT, "n");
        assertTrue("Apple sends email_verified as a string", IdTokenVerifier.isTrue(claims, "email_verified"));
    }

    @Test
    public void appleClientSecretIsAVerifiableEs256Jwt() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = gen.generateKeyPair();
        String jwt = new AppleClientSecret("TEAMID1234", "edu.example.jobs", "KEYID12345", null)
                .sign(kp.getPrivate(), Instant.now());
        String[] p = jwt.split("\\.");
        java.security.Signature s = java.security.Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(kp.getPublic());
        s.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(Base64.getUrlDecoder().decode(p[2])));
        String claims = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
        assertTrue(claims.contains("\"iss\":\"TEAMID1234\"") && claims.contains("\"aud\":\"https://appleid.apple.com\""));
    }

    @Test
    public void microsoftMultiTenantIssuerIsMatchedByTenantId() throws Exception {
        String template = "https://login.microsoftonline.com/{tenantid}/v2.0";
        JsonNode ok = IdTokenVerifier.parse("{\"iss\":\"https://login.microsoftonline.com/t-1/v2.0\",\"tid\":\"t-1\"}");
        JsonNode spoof = IdTokenVerifier.parse("{\"iss\":\"https://login.microsoftonline.com/t-1/v2.0\",\"tid\":\"t-2\"}");
        assertTrue(SsoService.issuerMatches(template, ok));
        assertFalse(SsoService.issuerMatches(template, spoof));
    }

    @Test
    public void providerEndpointsMustBeHttpsExceptLoopback() throws Exception {
        assertEquals("https://accounts.google.com/x", OidcClient.safeUrl("https://accounts.google.com/x"));
        assertEquals("http://127.0.0.1:9/x", OidcClient.safeUrl("http://127.0.0.1:9/x"));
        assertThrows(java.io.IOException.class, () -> OidcClient.safeUrl("http://evil.example/x"));
    }

    private static void assertRejected(FakeIdentityProvider idp, String token, String nonce, String why) {
        IdTokenVerifier.InvalidTokenException e = assertThrows(IdTokenVerifier.InvalidTokenException.class,
                () -> verify(idp, token, nonce));
        assertTrue("expected '" + why + "' but got: " + e.getMessage(), e.getMessage().contains(why));
    }

    private static byte[] fixed32(byte[] v) {
        return v.length == 32 ? v : v.length > 32 ? Arrays.copyOfRange(v, v.length - 32, v.length)
                : concat(new byte[32 - v.length], v);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
