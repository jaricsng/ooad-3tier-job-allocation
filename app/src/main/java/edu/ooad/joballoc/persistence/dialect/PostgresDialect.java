package edu.ooad.joballoc.persistence.dialect;

import java.sql.SQLException;
import java.util.Properties;

/** PostgreSQL 14+ (tested with 16). */
public final class PostgresDialect implements SqlDialect {

    /** SQLSTATE unique_violation. */
    private static final String UNIQUE_VIOLATION = "23505";

    @Override
    public String productName() {
        return "PostgreSQL";
    }

    @Override
    public String migrationFolder() {
        return "postgresql";
    }

    @Override
    public Properties connectionProperties() {
        return new Properties();
    }

    @Override
    public String firstRowsClause() {
        return "limit ?";
    }

    @Override
    public String validationQuery() {
        return "select 1";
    }

    @Override
    public boolean isUniqueViolation(SQLException e) {
        return UNIQUE_VIOLATION.equals(e.getSQLState());
    }
}
