package edu.ooad.joballoc.persistence.dialect;

import java.sql.SQLException;
import java.util.Properties;

/**
 * STRATEGY (GoF behavioural): the few things that differ between database products.
 *
 * <p>Everything else in the persistence tier is portable SQL. Keeping the differences behind one
 * small interface means a new database is one new class plus its migration scripts; the DAOs, the
 * business tier and the web tier do not change. Hibernate's {@code Dialect} is the industrial
 * version of the same idea.
 *
 * <p>Pick an implementation with {@link DialectFactory#forUrl(String)}.
 */
public interface SqlDialect {

    /** Human-readable product name for logs and health output, for example "PostgreSQL". */
    String productName();

    /** Folder under {@code db/migration/} that holds this product's versioned scripts. */
    String migrationFolder();

    /** Extra JDBC connection properties this product needs (time zone, safety settings). */
    Properties connectionProperties();

    /**
     * Clause that keeps only the first {@code ?} rows of an ordered query. Appended after ORDER BY;
     * the row count is bound as the last parameter.
     */
    String firstRowsClause();

    /** Cheapest statement that proves the connection works (used by the health check). */
    String validationQuery();

    /**
     * True if the exception means a unique or primary-key constraint was violated. Products report
     * this with different SQLSTATE or vendor codes; callers only need the meaning.
     */
    boolean isUniqueViolation(SQLException e);
}
