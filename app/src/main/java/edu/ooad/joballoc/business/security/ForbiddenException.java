package edu.ooad.joballoc.business.security;

/** The actor's role does not allow this operation (HTTP 403). */
public class ForbiddenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
