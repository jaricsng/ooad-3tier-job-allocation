package edu.ooad.joballoc.web.http;

import edu.ooad.joballoc.business.security.Actor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store. Session IDs are 256-bit random values; a new ID is issued at sign-in
 * (prevents session fixation) and sessions expire after {@link #IDLE_TIMEOUT} of inactivity.
 */
public class SessionManager {

    public static final String COOKIE = "JA_SESSION";
    /** Cookie of the sign-in form's CSRF token (double-submit), scoped to /login only. */
    public static final String LOGIN_COOKIE = "JA_LOGIN";
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /** 256-bit session IDs and CSRF tokens. */
    private static final int TOKEN_BYTES = 32;
    private static final long LOGIN_TOKEN_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean secureCookie;

    public SessionManager(Clock clock, boolean secureCookie) {
        this.clock = clock;
        this.secureCookie = secureCookie;
    }

    public Session create(Actor actor) {
        Session s = new Session(token(), actor, token(), clock.instant());
        sessions.put(s.id(), s);
        return s;
    }

    public Optional<Session> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Session s = sessions.get(id);
        Instant now = clock.instant();
        if (s == null) {
            return Optional.empty();
        }
        if (s.lastAccess().plus(IDLE_TIMEOUT).isBefore(now)) {
            sessions.remove(id);
            return Optional.empty();
        }
        s.touch(now);
        return Optional.of(s);
    }

    public void invalidate(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    public String cookieFor(Session s) {
        return cookie(s.id(), IDLE_TIMEOUT.toSeconds());
    }

    /** A fresh random token for one sign-in form (login CSRF protection, OWASP WSTG-SESS-05). */
    public String newLoginToken() {
        return token();
    }

    /** Short-lived, HttpOnly, SameSite=Strict cookie holding the sign-in form's token. */
    public String loginCookie(String token) {
        return LOGIN_COOKIE + "=" + token + "; Path=/login; HttpOnly; SameSite=Strict; Max-Age=" + LOGIN_TOKEN_SECONDS
                + (secureCookie ? "; Secure" : "");
    }

    /** Constant-time check that the form's token equals the cookie's (both must be present). */
    public static boolean matchesToken(String fromCookie, String fromForm) {
        return fromCookie != null && fromForm != null && !fromCookie.isEmpty() && MessageDigest.isEqual(
                fromCookie.getBytes(StandardCharsets.UTF_8), fromForm.getBytes(StandardCharsets.UTF_8));
    }

    public String expiredCookie() {
        return cookie("", 0);
    }

    /** The cookie's security attributes are defined once (before: repeated in both methods). */
    private String cookie(String value, long maxAgeSeconds) {
        return COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAgeSeconds
                + (secureCookie ? "; Secure" : "");
    }

    private static String token() {
        byte[] b = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
