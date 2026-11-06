package edu.ooad.joballoc.persistence.dao;

/** An INSERT or UPDATE broke a unique constraint, e.g. a second active allocation for the same job. */
public class DuplicateKeyException extends DataAccessException {
    private static final long serialVersionUID = 1L;

    public DuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
