package edu.ooad.joballoc.persistence.orm;

import edu.ooad.joballoc.persistence.dao.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Minimal schema-migration runner in the style of Flyway: applies versioned SQL scripts from
 * {@code db/migration/<product>/V<n>__<description>.sql} once, in order, and records them in
 * {@code schema_version}. The product folder comes from the {@code SqlDialect}, so each database
 * gets DDL in its own syntax while the version numbers stay aligned across products.
 *
 * <p>Applied scripts are never edited: a change is always a new, higher version (PostgreSQL V3
 * moves the skill arrays into collection tables rather than rewriting V1).
 *
 * <p>Each script is split into single statements here, so the JDBC driver never needs
 * multi-statement mode. Note: PostgreSQL runs a whole script in one transaction; MySQL commits
 * after every DDL statement, so a failed MySQL script can leave part of its changes behind.
 */
public final class Migrator {

    /** Migration scripts in the order they must run. Add new files here and to resources. */
    public static final List<String> SCRIPTS = List.of(
            "V1__schema.sql",
            "V2__seed_reference_data.sql",
            "V3__skills_to_collection_tables.sql",
            "V4__app_user_email.sql");

    private static final Logger LOG = Logger.getLogger(Migrator.class.getName());

    private Migrator() { }

    /** One script's state, for {@code make db-status} and the rollout checks. */
    public record ScriptStatus(int version, String script, State state, String appliedAt) {

        /** Where a script stands relative to the database. */
        public enum State {
            /** Applied and unchanged since. */
            APPLIED,
            /** Not applied yet: the next migrate runs it. */
            PENDING,
            /** Applied, but the file has been edited since: never edit an applied script. */
            CHANGED,
            /** In the database but unknown to this build: the database is newer than the code. */
            UNKNOWN
        }
    }

    /**
     * Applies pending scripts in order and returns how many ran. First VALIDATES the history and
     * fails fast, changing nothing, if an applied script was edited or the database is ahead of this
     * build (for example after rolling the code back but not the database).
     */
    public static int migrate(ConnectionPool pool) {
        Connection con = pool.acquire();
        try {
            prepareHistoryTable(con, pool.dialect().migrationFolder());
            List<ScriptStatus> problems = status(con, pool.dialect().migrationFolder()).stream()
                    .filter(st -> st.state() == ScriptStatus.State.CHANGED || st.state() == ScriptStatus.State.UNKNOWN)
                    .toList();
            if (!problems.isEmpty()) {
                throw new DataAccessException("Migration history does not match this build: " + problems
                        + ". Restore the original script, or deploy the build that matches the database.");
            }
            Set<Integer> applied = appliedVersions(con);
            int count = 0;
            for (String script : SCRIPTS) {
                int version = versionOf(script);
                if (!applied.contains(version)) {
                    apply(con, pool.dialect().migrationFolder(), script, version);
                    count++;
                }
            }
            return count;
        } catch (SQLException e) {
            throw new DataAccessException("Migration failed", e);
        } finally {
            pool.release(con);
        }
    }

    /** One row of schema_version. */
    private record Recorded(String script, String checksum, String appliedAt) { }

    /** Every known and recorded script with its state; read-only apart from creating the history table. */
    public static List<ScriptStatus> status(ConnectionPool pool) {
        Connection con = pool.acquire();
        try {
            prepareHistoryTable(con, pool.dialect().migrationFolder());
            return status(con, pool.dialect().migrationFolder());
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the migration history", e);
        } finally {
            pool.release(con);
        }
    }

    private static List<ScriptStatus> status(Connection con, String folder) throws SQLException {
        Map<Integer, Recorded> recorded = new TreeMap<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("select version, script, checksum, applied_at from schema_version")) {
            while (rs.next()) {
                recorded.put(rs.getInt("version"), new Recorded(rs.getString("script"), rs.getString("checksum"),
                        rs.getString("applied_at")));
            }
        }
        List<ScriptStatus> result = new ArrayList<>();
        Set<Integer> known = new HashSet<>();
        for (String script : SCRIPTS) {
            int version = versionOf(script);
            known.add(version);
            Recorded row = recorded.get(version);
            if (row == null) {
                result.add(new ScriptStatus(version, script, ScriptStatus.State.PENDING, null));
            } else {
                boolean same = checksum(load(folder + "/" + script)).equals(row.checksum());
                ScriptStatus.State state = same ? ScriptStatus.State.APPLIED : ScriptStatus.State.CHANGED;
                result.add(new ScriptStatus(version, script, state, row.appliedAt()));
            }
        }
        recorded.forEach((version, row) -> {
            if (!known.contains(version)) {
                result.add(new ScriptStatus(version, row.script(), ScriptStatus.State.UNKNOWN, row.appliedAt()));
            }
        });
        return result;
    }

    /**
     * Creates the history table, or evolves an older one: databases migrated before checksums
     * existed get the column and a one-off BASELINE (the current checksum of each applied script).
     */
    private static void prepareHistoryTable(Connection con, String folder) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute("create table if not exists schema_version ("
                    + "version int primary key, script varchar(200) not null, "
                    + "applied_at timestamp not null default current_timestamp, checksum varchar(64))");
            if (!hasColumn(con, "schema_version", "checksum")) {
                st.execute("alter table schema_version add checksum varchar(64)");
            }
        }
        try (PreparedStatement fill = con.prepareStatement(
                "update schema_version set checksum = ? where version = ? and checksum is null")) {
            for (String script : SCRIPTS) {
                fill.setString(1, checksum(load(folder + "/" + script)));
                fill.setInt(2, versionOf(script));
                if (fill.executeUpdate() > 0) {
                    LOG.info(() -> "Recorded baseline checksum for " + script);
                }
            }
        }
    }

    /** Portable column check through JDBC metadata (PostgreSQL folds names to lower case, MySQL keeps them). */
    private static boolean hasColumn(Connection con, String table, String column) throws SQLException {
        DatabaseMetaData md = con.getMetaData();
        for (String t : List.of(table, table.toUpperCase(Locale.ROOT))) {
            try (ResultSet rs = md.getColumns(con.getCatalog(), null, t, null)) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Set<Integer> appliedVersions(Connection con) throws SQLException {
        Set<Integer> applied = new HashSet<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("select version from schema_version")) {
            while (rs.next()) {
                applied.add(rs.getInt("version"));
            }
        }
        return applied;
    }

    private static void apply(Connection con, String folder, String script, int version) throws SQLException {
        String sql = load(folder + "/" + script);
        con.setAutoCommit(false);
        try (Statement run = con.createStatement();
                PreparedStatement rec = con.prepareStatement(
                        "insert into schema_version(checksum, script, version) values (?, ?, ?)")) {
            for (String statement : statements(sql)) {
                run.execute(statement);
            }
            int p = 0;
            rec.setString(++p, checksum(sql));
            rec.setString(++p, script);
            rec.setInt(++p, version);
            rec.executeUpdate();
            con.commit();
            LOG.info(() -> "Applied migration " + script);
        } catch (SQLException e) {
            con.rollback();
            throw new DataAccessException("Migration " + script + " failed", e);
        } finally {
            con.setAutoCommit(true);
        }
    }

    static int versionOf(String script) {
        return Integer.parseInt(script.substring(1, script.indexOf("__")));
    }

    /** SHA-256 of the script with line endings normalised, so a Windows checkout does not look "changed". */
    public static String checksum(String script) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(script.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

    /**
     * Splits a script into statements: a statement ends at a line ending in ';', except inside a
     * PostgreSQL dollar-quoted block ($$ ... $$), whose body contains semicolons of its own.
     * Lines that contain only a comment are dropped.
     */
    public static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDollarBlock = false;
        for (String line : script.split("\\R")) {
            String trimmed = line.strip();
            if (current.isEmpty() && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
                continue;
            }
            current.append(line).append('\n');
            if ((trimmed.split("\\$\\$", -1).length - 1) % 2 == 1) {
                inDollarBlock = !inDollarBlock;
            }
            if (!inDollarBlock && trimmed.endsWith(";")) {
                String statement = current.toString().strip();
                result.add(statement.substring(0, statement.length() - 1).strip());
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString().strip());
        }
        return result;
    }

    private static String load(String script) {
        String path = "/db/migration/" + script;
        try (InputStream in = Migrator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new DataAccessException("Missing migration resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + path, e);
        }
    }
}
