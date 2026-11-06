package edu.ooad.joballoc.persistence.dialect;

import java.sql.SQLException;
import java.util.Properties;

/** MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16; tested with 8.0 and 8.4 LTS). */
public final class MySqlDialect implements SqlDialect {

    /** MySQL error ER_DUP_ENTRY; its SQLSTATE 23000 is shared with other constraint errors. */
    private static final int DUPLICATE_ENTRY = 1062;

    @Override
    public String productName() {
        return "MySQL";
    }

    @Override
    public String migrationFolder() {
        return "mysql";
    }

    /**
     * Store and read instants in UTC, whatever the server or JVM time zone, so that
     * {@code allocated_at} means the same moment on every machine. Multi-statement queries stay
     * off (the driver default): they would widen the impact of any SQL injection.
     */
    @Override
    public Properties connectionProperties() {
        Properties p = new Properties();
        p.setProperty("connectionTimeZone", "UTC");
        p.setProperty("forceConnectionTimeZoneToSession", "true");
        p.setProperty("allowMultiQueries", "false");
        return p;
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
        return e.getErrorCode() == DUPLICATE_ENTRY;
    }
}
