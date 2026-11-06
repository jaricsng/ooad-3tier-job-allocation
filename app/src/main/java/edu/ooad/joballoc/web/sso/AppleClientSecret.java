package edu.ooad.joballoc.web.sso;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Apple's "client secret" is not a fixed string: it is a short-lived JWT signed with ES256 by the
 * private key (.p8) downloaded from the Apple developer account. A new one is made for each token request.
 * See https://developer.apple.com/documentation/accountorganizationaldatasharing/creating-a-client-secret
 */
public final class AppleClientSecret {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final long LIFETIME_SECONDS = 300;

    private final String teamId;
    private final String clientId;
    private final String keyId;
    private final Path privateKeyFile;

    public AppleClientSecret(String teamId, String clientId, String keyId, Path privateKeyFile) {
        this.teamId = teamId;
        this.clientId = clientId;
        this.keyId = keyId;
        this.privateKeyFile = privateKeyFile;
    }

    /** A fresh client secret JWT, valid for five minutes. */
    public String create() {
        try {
            return sign(loadKey(), Instant.now());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot create the Apple client secret from " + privateKeyFile, e);
        }
    }

    String sign(PrivateKey key, Instant now) throws GeneralSecurityException {
        String header = b64("{\"alg\":\"ES256\",\"kid\":\"" + keyId + "\"}");
        String claims = b64("{\"iss\":\"" + teamId + "\",\"iat\":" + now.getEpochSecond() + ",\"exp\":"
                + (now.getEpochSecond() + LIFETIME_SECONDS) + ",\"aud\":\"" + AUDIENCE + "\",\"sub\":\"" + clientId
                + "\"}");
        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initSign(key);
        es256.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(es256.sign());
    }

    private PrivateKey loadKey() throws IOException, GeneralSecurityException {
        String pem = Files.readString(privateKeyFile).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
