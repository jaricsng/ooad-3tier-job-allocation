package edu.ooad.joballoc.persistence.dao;

/**
 * Root of the persistence tier's exception hierarchy (part of the DAO contract, like Spring's
 * DataAccessException). Every DAO implementation, JDBC or in-memory, reports failures with these
 * types, so callers never see {@code java.sql.SQLException} or vendor error codes.
 */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
