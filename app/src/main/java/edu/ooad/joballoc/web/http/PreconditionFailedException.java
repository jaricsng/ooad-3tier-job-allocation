package edu.ooad.joballoc.web.http;

/** HTTP 412: the client's If-Match ETag no longer matches the resource (optimistic concurrency over HTTP). */
public class PreconditionFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PreconditionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
