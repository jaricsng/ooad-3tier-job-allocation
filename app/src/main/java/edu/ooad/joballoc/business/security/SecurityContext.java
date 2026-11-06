package edu.ooad.joballoc.business.security;

import java.util.Optional;
import java.util.function.Supplier;

/** Holds the current {@link Actor} for the thread handling a request (like Spring's SecurityContextHolder). */
public final class SecurityContext {

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    private SecurityContext() { }

    public static Optional<Actor> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Actor require() {
        return current().orElseThrow(() -> new UnauthenticatedException("Sign-in required"));
    }

    /** Runs work as the given actor and always clears the context afterwards. */
    public static <R> R runAs(Actor actor, Supplier<R> work) {
        Actor previous = CURRENT.get();
        CURRENT.set(actor);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
