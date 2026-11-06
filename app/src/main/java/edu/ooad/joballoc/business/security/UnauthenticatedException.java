package edu.ooad.joballoc.business.security;

/** No authenticated user (HTTP 401). */
public class UnauthenticatedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnauthenticatedException(String message) {
        super(message);
    }
}
