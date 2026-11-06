package edu.ooad.joballoc.it;

import edu.ooad.joballoc.config.AppConfig;
import edu.ooad.joballoc.persistence.orm.ConnectionPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration tests use a separate database (default {@code joballoc_test}) that is wiped before
 * each test class. Override with TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD.
 */
public final class TestDatabase {

    private TestDatabase() { }

    public static String url() {
        return System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/joballoc_test");
    }

    public static String user() {
        return System.getenv().getOrDefault("TEST_DB_USER", "joballoc");
    }

    public static String password() {
        return System.getenv().getOrDefault("TEST_DB_PASSWORD", "joballoc");
    }

    /** True when the integration tests run on in-memory storage (TEST_DB_URL=memory:). */
    public static boolean isMemory() {
        return "memory:".equals(url());
    }

    /** True when the integration tests run against MySQL (TEST_DB_URL=jdbc:mysql://...). */
    public static boolean isMySql() {
        return url().startsWith("jdbc:mysql:");
    }

    /** Drops every object in the test schema, so migrations run from scratch. */
    public static void reset() throws SQLException {
        ConnectionPool.shutdown();
        if (isMemory()) {
            return; // every App.start gets a fresh in-memory store
        }
        try (Connection c = DriverManager.getConnection(url(), user(),
                password()); Statement st = c.createStatement()) {
            if (isMySql()) {
                // MySQL has no "drop schema cascade" for one schema; drop each table with FK checks off.
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("select table_name from information_schema.tables"
                        + " where table_schema = database()")) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
                st.execute("set foreign_key_checks = 0");
                for (String table : tables) {
                    st.execute("drop table if exists `" + table + "`");
                }
                st.execute("set foreign_key_checks = 1");
            } else {
                st.execute("drop schema if exists public cascade");
                st.execute("create schema public");
            }
        }
    }

    /** App configuration pointing at the test database, on a random free port. */
    public static AppConfig config() {
        return config(Map.of());
    }

    /** As {@link #config()}, with extra settings (for example a test single sign-on provider). */
    public static AppConfig config(Map<String, String> extra) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", url());
        env.put("DB_USER", user());
        env.put("DB_PASSWORD", password());
        env.put("APP_PORT", "0");
        env.put("APP_DEMO_DATA", "true");
        env.put("DB_POOL_SIZE", "5");
        env.putAll(extra);
        return AppConfig.from(env);
    }
}
