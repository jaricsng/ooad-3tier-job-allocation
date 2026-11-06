package edu.ooad.joballoc.business;

/** The requested job, worker or allocation does not exist (HTTP 404). */
public class NotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
