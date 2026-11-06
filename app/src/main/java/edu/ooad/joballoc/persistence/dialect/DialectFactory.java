package edu.ooad.joballoc.persistence.dialect;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * FACTORY: chooses the {@link SqlDialect} from the JDBC URL, so configuration (DB_URL) is the only
 * thing that changes when the database product changes.
 *
 * <p>Adding a product (for example Oracle) means: one {@code SqlDialect} class, one entry here, one
 * folder of migration scripts, and running the integration tests against it.
 */
public final class DialectFactory {

    /** JDBC URL prefix -> dialect. Ordered so error messages list products predictably. */
    private static final Map<String, Supplier<SqlDialect>> BY_PREFIX = Map.of(
            "jdbc:postgresql://", PostgresDialect::new,
            "jdbc:mysql://", MySqlDialect::new);

    private DialectFactory() { }

    /** URL prefixes this build supports, in a stable order (used by configuration validation). */
    public static List<String> supportedPrefixes() {
        return BY_PREFIX.keySet().stream().sorted().toList();
    }

    /** True if {@link #forUrl(String)} would accept this URL. */
    public static boolean isSupported(String jdbcUrl) {
        return jdbcUrl != null && BY_PREFIX.keySet().stream().anyMatch(jdbcUrl::startsWith);
    }

    /** Returns the dialect for this URL, or fails fast listing what is supported. */
    public static SqlDialect forUrl(String jdbcUrl) {
        return BY_PREFIX.entrySet().stream()
                .filter(e -> jdbcUrl != null && jdbcUrl.startsWith(e.getKey()))
                .map(e -> e.getValue().get())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported database URL " + jdbcUrl
                        + "; supported prefixes: " + supportedPrefixes()));
    }
}
