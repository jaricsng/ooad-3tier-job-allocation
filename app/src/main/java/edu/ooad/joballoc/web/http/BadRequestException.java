package edu.ooad.joballoc.web.http;

/** Malformed input from the client (HTTP 400). */
public class BadRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }
}
