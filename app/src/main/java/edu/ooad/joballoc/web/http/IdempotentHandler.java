package edu.ooad.joballoc.web.http;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * DECORATOR (GoF structural) around a {@link Handler}: adds idempotency to a non-idempotent POST
 * without changing the controller. Clients send an {@code Idempotency-Key} header (IETF draft
 * "The Idempotency-Key HTTP Header Field"); a retry with the same key and body returns the stored
 * response with {@code Idempotent-Replayed: true} instead of allocating the job twice.
 *
 * <ul>
 *   <li>no key: the request runs normally (the key is recommended, not required)</li>
 *   <li>same key, request still running: 409</li>
 *   <li>same key, different body: 422</li>
 *   <li>failure (an exception): the key is released, so the client may retry</li>
 * </ul>
 * Keys are scoped by user, method and path, so two users cannot collide.
 */
public final class IdempotentHandler implements Handler {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final Handler inner;
    private final IdempotencyStore store;

    private IdempotentHandler(Handler inner, IdempotencyStore store) {
        this.inner = inner;
        this.store = store;
    }

    public static Handler wrap(Handler inner, IdempotencyStore store) {
        return new IdempotentHandler(inner, store);
    }

    @Override
    public Response handle(Request req) throws Exception {
        String key = req.header(HEADER);
        if (key == null) {
            return inner.handle(req);
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new BadRequestException(HEADER
                    + " must be 8-128 characters of letters, digits, '.', '_', ':' or '-'");
        }
        String user = req.actor() == null ? "anonymous" : req.actor().username();
        String scope = user + " " + req.method() + " " + req.path() + " " + key;
        IdempotencyStore.Outcome outcome = store.begin(scope, sha256(req.body()));
        return switch (outcome) {
            case IdempotencyStore.Replay r -> r.response().setHeader("Idempotent-Replayed", "true");
            case IdempotencyStore.InFlight f ->
                    throw new ConflictException("A request with this " + HEADER + " is still being processed");
            case IdempotencyStore.Mismatch m ->
                    throw new UnprocessableException(HEADER + " was already used with a different request body");
            case IdempotencyStore.Started s -> executeOnce(req, scope);
        };
    }

    private Response executeOnce(Request req, String scope) throws Exception {
        try {
            Response response = inner.handle(req);
            store.complete(scope, response);
            return response;
        } catch (Exception e) {
            store.abandon(scope);
            throw e;
        }
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** 409: the same key is in use by a request that has not finished. */
    public static final class ConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ConflictException(String message) {
            super(message);
        }
    }

    /** 422: the same key with a different body. */
    public static final class UnprocessableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnprocessableException(String message) {
            super(message);
        }
    }
}
