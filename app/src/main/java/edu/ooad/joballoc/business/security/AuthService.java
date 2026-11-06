package edu.ooad.joballoc.business.security;

import edu.ooad.joballoc.domain.AppUser;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.persistence.dao.UserDAO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies credentials. After {@value #MAX_FAILURES} failed attempts a username is locked for
 * {@link #LOCKOUT}, slowing down password guessing (OWASP ASVS V2.2). Error messages never reveal
 * whether the username exists.
 */
public class AuthService {

    static final int MAX_FAILURES = 5;
    /** Input limits (match the login form's maxlength) stop oversized inputs before hashing. */
    static final int MAX_USERNAME_LENGTH = 64;
    static final int MAX_PASSWORD_LENGTH = 200;
    static final Duration LOCKOUT = Duration.ofMinutes(5);
    /** Marker stored instead of a password hash for SSO-created accounts: no password can ever match it. */
    static final String SSO_ONLY_HASH_MARKER = "sso-only";
    private static final int SSO_HASH_CHARS = 40;

    private final UserDAO users;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant lockedUntil) { }

    public AuthService(UserDAO users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public Optional<Actor> authenticate(String username, String password) {
        if (username == null || password == null || username.length() > MAX_USERNAME_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            return Optional.empty();
        }
        String key = username.toLowerCase();
        Attempts a = attempts.get(key);
        Instant now = clock.instant();
        if (a != null && a.lockedUntil() != null && now.isBefore(a.lockedUntil())) {
            return Optional.empty();
        }
        Optional<AppUser> user = users.findById(username);
        boolean ok = user.isPresent() && PasswordHasher.verify(password, user.get().getPasswordHash());
        if (!ok) {
            int failures = a == null ? 1 : a.failures() + 1;
            attempts.put(key, new Attempts(failures, failures >= MAX_FAILURES ? now.plus(LOCKOUT) : null));
            return Optional.empty();
        }
        attempts.remove(key);
        AppUser u = user.get();
        return Optional.of(new Actor(u.getUsername(), u.getRole(), u.getDisplayName()));
    }

    /** What to do when a verified SSO identity matches no account. */
    public enum Provisioning {
        /** Deny: an administrator must create the account (with its email) first. Default. */
        NONE,
        /** Create a read-only VIEWER account. SSO never creates a SUPERVISOR. */
        VIEWER
    }

    /**
     * Signs in a person verified by an external identity provider (single sign-on).
     * <ul>
     *   <li>The provider must have VERIFIED the email; an unverified email is never trusted.</li>
     *   <li>The account is found by that email (accounts carry it since migration V4).</li>
     *   <li>No match: denied, or with {@link Provisioning#VIEWER} a read-only account is created.</li>
     * </ul>
     * Roles always come from this application, never from the identity provider.
     */
    public Optional<Actor> authenticateExternal(ExternalIdentity id, Provisioning provisioning) {
        if (id == null || !id.emailVerified() || id.email() == null || id.email().isBlank()) {
            return Optional.empty();
        }
        Optional<AppUser> user = users.findByEmail(id.email());
        if (user.isEmpty() && provisioning == Provisioning.VIEWER) {
            AppUser created = new AppUser(ssoUsername(id), SSO_ONLY_HASH_MARKER, Role.VIEWER,
                    id.name() == null || id.name().isBlank() ? id.email() : id.name(), id.email());
            users.save(created);
            user = Optional.of(created);
        }
        return user.map(u -> new Actor(u.getUsername(), u.getRole(), u.getDisplayName()));
    }

    /** "provider:subject", shortened with a hash if needed to fit the 64-character username column. */
    static String ssoUsername(ExternalIdentity id) {
        String name = id.provider() + ":" + id.subject();
        if (name.length() <= MAX_USERNAME_LENGTH) {
            return name;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(digest).substring(0, SSO_HASH_CHARS);
            return id.provider() + ":" + hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
