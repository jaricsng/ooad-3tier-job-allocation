package edu.ooad.joballoc.persistence.dao;

/**
 * An UPDATE matched no row with the expected @Version: someone else changed it first
 * (Optimistic Offline Lock, PoEAA).
 */
public class OptimisticLockException extends DataAccessException {
    private static final long serialVersionUID = 1L;

    public OptimisticLockException(String message) {
        super(message);
    }
}
