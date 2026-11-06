package edu.ooad.joballoc.web.http;

import java.util.Arrays;

/**
 * The HTTP status codes this application uses, with the RFC 9457 problem type and title of each
 * error status. Before: bare numbers (409, 422, …) in a dozen places and two parallel {@code switch}
 * statements in ProblemDTO ("primitive obsession"); a typo compiled silently.
 */
public enum HttpStatus {
    OK(200, null, "OK"),
    CREATED(201, null, "Created"),
    NO_CONTENT(204, null, "No content"),
    SEE_OTHER(303, null, "See other"),
    NOT_MODIFIED(304, null, "Not modified"),
    BAD_REQUEST(400, "bad-request", "Bad request"),
    UNAUTHORIZED(401, "unauthenticated", "Sign-in required"),
    FORBIDDEN(403, "forbidden", "Forbidden"),
    NOT_FOUND(404, "not-found", "Not found"),
    METHOD_NOT_ALLOWED(405, "method-not-allowed", "Method not allowed"),
    CONFLICT(409, "conflict", "Conflict with the current state"),
    PRECONDITION_FAILED(412, "precondition-failed", "Precondition failed"),
    PAYLOAD_TOO_LARGE(413, "payload-too-large", "Request body too large"),
    UNPROCESSABLE_CONTENT(422, "business-rule-violated", "Business rule violated"),
    INTERNAL_SERVER_ERROR(500, "internal-error", "Internal server error"),
    SERVICE_UNAVAILABLE(503, "service-unavailable", "Service unavailable");

    private final int code;
    private final String problemSlug;
    private final String title;

    HttpStatus(int code, String problemSlug, String title) {
        this.code = code;
        this.problemSlug = problemSlug;
        this.title = title;
    }

    public int code() {
        return code;
    }

    public String title() {
        return title;
    }

    /** Problem type URI reference, e.g. {@code /problems/conflict}. */
    public String problemType() {
        return "/problems/" + (problemSlug == null ? "internal-error" : problemSlug);
    }

    /** True for 1xx, 204 and 304, which must not carry a body; 303 here is always an empty redirect. */
    public boolean hasNoBody() {
        return this == NO_CONTENT || this == NOT_MODIFIED || this == SEE_OTHER;
    }

    public static HttpStatus of(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst().orElse(INTERNAL_SERVER_ERROR);
    }
}
