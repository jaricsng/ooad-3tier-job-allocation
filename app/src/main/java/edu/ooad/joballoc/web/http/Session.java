package edu.ooad.joballoc.web.http;

import edu.ooad.joballoc.business.security.Actor;

import java.time.Instant;

/** Server-side session: the signed-in actor and the CSRF token for its forms. */
public final class Session {
    private final String id;
    private final Actor actor;
    private final String csrfToken;
    private volatile Instant lastAccess;

    Session(String id, Actor actor, String csrfToken, Instant now) {
        this.id = id;
        this.actor = actor;
        this.csrfToken = csrfToken;
        this.lastAccess = now;
    }

    public String id() { return id; }
    public Actor actor() { return actor; }
    public String csrfToken() { return csrfToken; }
    Instant lastAccess() { return lastAccess; }
    void touch(Instant now) { lastAccess = now; }
}
