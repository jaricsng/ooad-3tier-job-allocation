package edu.ooad.joballoc.business.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 password hashing (OWASP Password Storage Cheat Sheet: 210,000+ iterations,
 * random 16-byte salt). Format: {@code pbkdf2-sha256$<iterations>$<salt>$<hash>}.
 */
public final class PasswordHasher {

    /** Minimum length for new passwords (NIST SP 800-63B recommends at least 8; we ask for 12). */
    public static final int MIN_PASSWORD_LENGTH = 12;
    private static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGORITHM_ID = "pbkdf2-sha256";
    /** Stored format: algorithm$iterations$salt$hash. */
    private static final int HASH_PARTS = 4;
    private static final int EXIT_USAGE = 2;

    private PasswordHasher() { }

    /** CLI for the runbook: prints a hash to store in app_user.password_hash. Reads the password from stdin. */
    public static void main(String[] args) throws java.io.IOException {
        String pw = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (pw.length() < MIN_PASSWORD_LENGTH) {
            System.err.println("Use a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
            System.exit(EXIT_USAGE);
        }
        System.out.println(hash(pw));
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return format(ITERATIONS, salt, derive(password, salt, ITERATIONS));
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != HASH_PARTS || !parts[0].equals(ALGORITHM_ID)) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[HASH_PARTS - 1]);
        return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static String format(int iterations, byte[] salt, byte[] hash) {
        Base64.Encoder b64 = Base64.getEncoder();
        return ALGORITHM_ID + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }
}
