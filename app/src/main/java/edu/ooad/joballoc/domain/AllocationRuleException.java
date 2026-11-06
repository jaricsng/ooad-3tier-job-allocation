package edu.ooad.joballoc.domain;

/** A business rule from the allocate() operation contract was violated (mapped to HTTP 422). */
public class AllocationRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AllocationRuleException(String message) {
        super(message);
    }
}
