package edu.ooad.joballoc.persistence.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a {@code Set<String>} field to its own table with one row per value (modelled on Jakarta
 * Persistence {@code @ElementCollection} + {@code @CollectionTable}).
 *
 * <p>Portable across databases, unlike PostgreSQL arrays, and it lets the database enforce a
 * foreign key on every value. An {@code ordinal} column keeps the order the values were added in.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CollectionTable {

    /** Name of the collection table, for example {@code worker_skill}. */
    String table();

    /** Column that refers back to the owning row's id, for example {@code worker_id}. */
    String joinColumn();

    /** Column that holds each value, for example {@code skill_code}. */
    String valueColumn();
}
