package edu.ooad.joballoc.domain;

import java.time.LocalDate;

/**
 * QUERY OBJECT (PoEAA): filter for the Pending Jobs grid. {@code priority} and {@code skill} are
 * optional (null = any). It lives in the domain package because all three layers share it; before,
 * it sat in {@code persistence.dao}, which forced the controllers to import the persistence tier.
 */
public record JobCriteria(LocalDate date, Priority priority, String skill) {
    public JobCriteria {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (skill != null && skill.isBlank()) {
            skill = null;
        }
    }
}
