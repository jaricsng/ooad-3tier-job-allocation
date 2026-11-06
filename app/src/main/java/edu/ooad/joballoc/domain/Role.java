package edu.ooad.joballoc.domain;

/** Application roles. A SUPERVISOR can change allocations; a VIEWER can only read. */
public enum Role {
    VIEWER, SUPERVISOR;

    /** True when this role includes the privileges of {@code required}. */
    public boolean includes(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
