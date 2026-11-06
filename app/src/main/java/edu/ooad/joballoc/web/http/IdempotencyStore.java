package edu.ooad.joballoc.web.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the response to each idempotency key for {@link #ttl}, so a retried request gets the
 * same answer instead of being executed twice (IDEMPOTENT RECEIVER, Enterprise Integration Patterns).
 *
 * <p>In-memory and per process: right for this single-instance demo. With several instances behind a
 * load balancer the store must be shared (a database table keyed by scope, or Redis with a TTL).
 */
public final class IdempotencyStore {

    /** What {@link #begin} found. */
    public sealed interface Outcome permits Started, Replay, InFlight, Mismatch { }

    /** No earlier request: the caller executes it and then calls {@link #complete} or {@link #abandon}. */
    public record Started() implements Outcome { }

    /** Same key and same body as an earlier, finished request: return its response. */
    public record Replay(Response response) implements Outcome { }

    /** Same key, and the first request is still running. */
    public record InFlight() implements Outcome { }

    /** Same key reused with a different body: a client bug. */
    public record Mismatch() implements Outcome { }

    private record Entry(String fingerprint, Response response, Instant created) { }

    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public Outcome begin(String scope, String fingerprint) {
        evictExpired();
        Entry fresh = new Entry(fingerprint, null, clock.instant());
        Entry existing = entries.putIfAbsent(scope, fresh);
        if (existing == null) {
            return new Started();
        }
        if (!existing.fingerprint().equals(fingerprint)) {
            return new Mismatch();
        }
        return existing.response() == null ? new InFlight() : new Replay(existing.response().copy());
    }

    public void complete(String scope, Response response) {
        entries.computeIfPresent(scope, (k, e) -> new Entry(e.fingerprint(), response.copy(), e.created()));
    }

    /** The request failed: forget the key so the client may retry. */
    public void abandon(String scope) {
        entries.remove(scope);
    }

    int size() {
        return entries.size();
    }

    private void evictExpired() {
        Instant oldest = clock.instant().minus(ttl);
        entries.values().removeIf(e -> e.created().isBefore(oldest));
        if (entries.size() > MAX_ENTRIES) {
            entries.clear(); // safety valve against memory exhaustion; clients may see a re-execution
        }
    }
}
