package edu.ooad.joballoc.business;

/** Someone else changed the data first; the user must refresh and retry (HTTP 409). */
public class ConcurrencyConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConcurrencyConflictException(String message) {
        super(message);
    }

    public ConcurrencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
