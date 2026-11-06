package edu.ooad.joballoc.web.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.function.Function;

/**
 * Verifies an OpenID Connect ID token (a signed JWT) the way the OIDC Core spec, section 3.1.3.7,
 * requires, without a third-party JWT library:
 * <ol>
 *   <li>only RS256 and ES256 are accepted: "none" and HMAC algorithms are refused (algorithm confusion);</li>
 *   <li>the signature is checked with the provider's published key (JWKS) chosen by "kid";</li>
 *   <li>issuer, audience (our client id), expiry, issued-at and the nonce we sent must all match.</li>
 * </ol>
 */
public final class IdTokenVerifier {

    /** Thrown for any token that must not be trusted; the message is for logs, never for users. */
    public static final class InvalidTokenException extends Exception {
        private static final long serialVersionUID = 1L;

        public InvalidTokenException(String message) {
            super(message);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ALGORITHMS = Set.of("RS256", "ES256");
    private static final Duration SKEW = Duration.ofSeconds(60);
    private static final int JWT_PARTS = 3;

    private final Clock clock;

    public IdTokenVerifier(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param keys      JWKS lookup by key id (returns null if unknown)
     * @param issuerOk  whether the token's issuer is acceptable (exact match, or Microsoft's tenant template)
     */
    public JsonNode verify(String jwt, Function<String, JsonNode> keys, Function<JsonNode, Boolean> issuerOk,
            String clientId, String nonce) throws InvalidTokenException {
        String[] parts = jwt == null ? new String[0] : jwt.split("\\.");
        if (parts.length != JWT_PARTS) {
            throw new InvalidTokenException("not a signed JWT");
        }
        JsonNode header = decode(parts[0]);
        JsonNode claims = decode(parts[1]);
        String alg = header.path("alg").asText();
        if (!ALGORITHMS.contains(alg)) {
            throw new InvalidTokenException("algorithm not allowed: " + alg);
        }
        JsonNode jwk = keys.apply(header.path("kid").asText());
        if (jwk == null) {
            throw new InvalidTokenException("unknown signing key " + header.path("kid").asText());
        }
        verifySignature(alg, jwk, (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                Base64.getUrlDecoder().decode(parts[2]));
        if (!issuerOk.apply(claims)) {
            throw new InvalidTokenException("wrong issuer " + claims.path("iss").asText());
        }
        if (!audienceContains(claims.path("aud"), clientId)) {
            throw new InvalidTokenException("token is for another client");
        }
        long now = clock.instant().getEpochSecond();
        if (claims.path("exp").asLong(0) <= now - SKEW.toSeconds()) {
            throw new InvalidTokenException("token expired");
        }
        if (claims.path("iat").asLong(Long.MAX_VALUE) > now + SKEW.toSeconds()) {
            throw new InvalidTokenException("token issued in the future");
        }
        if (nonce == null || !nonce.equals(claims.path("nonce").asText(null))) {
            throw new InvalidTokenException("nonce mismatch (possible replay)");
        }
        return claims;
    }

    /** True if the claim is boolean true or the string "true" (Apple sends strings). */
    public static boolean isTrue(JsonNode claims, String name) {
        JsonNode v = claims.path(name);
        return v.isBoolean() ? v.asBoolean() : "true".equalsIgnoreCase(v.asText());
    }

    private static boolean audienceContains(JsonNode aud, String clientId) {
        if (aud.isArray()) {
            for (JsonNode a : aud) {
                if (clientId.equals(a.asText())) {
                    return true;
                }
            }
            return false;
        }
        return clientId.equals(aud.asText());
    }

    private static void verifySignature(String alg, JsonNode jwk, byte[] signed, byte[] signature)
            throws InvalidTokenException {
        try {
            Signature verifier = Signature.getInstance(
                    "RS256".equals(alg) ? "SHA256withRSA" : "SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey(alg, jwk));
            verifier.update(signed);
            if (!verifier.verify(signature)) {
                throw new InvalidTokenException("bad signature");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new InvalidTokenException("signature check failed: " + e.getMessage());
        }
    }

    static PublicKey publicKey(String alg, JsonNode jwk) throws GeneralSecurityException {
        if ("RS256".equals(alg) && "RSA".equals(jwk.path("kty").asText())) {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    unsigned(jwk.path("n").asText()), unsigned(jwk.path("e").asText())));
        }
        if ("ES256".equals(alg) && "EC".equals(jwk.path("kty").asText()) && "P-256".equals(jwk.path("crv").asText())) {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECPoint point = new ECPoint(unsigned(jwk.path("x").asText()), unsigned(jwk.path("y").asText()));
            return KeyFactory.getInstance("EC").generatePublic(
                    new ECPublicKeySpec(point, params.getParameterSpec(ECParameterSpec.class)));
        }
        throw new GeneralSecurityException("key type does not match " + alg);
    }

    private static BigInteger unsigned(String base64Url) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64Url));
    }

    private static JsonNode decode(String part) throws InvalidTokenException {
        try {
            return JSON.readTree(Base64.getUrlDecoder().decode(part));
        } catch (IOException | IllegalArgumentException e) {
            throw new InvalidTokenException("unreadable token part");
        }
    }

    /** For tests and the HTTP client: parse a JSON object. */
    static JsonNode parse(String json) throws IOException {
        return JSON.readTree(json);
    }

}
