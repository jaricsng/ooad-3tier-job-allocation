package edu.ooad.joballoc.config;

import edu.ooad.joballoc.persistence.dialect.DialectFactory;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;

import java.util.function.Predicate;

/**
 * CONFIGURATION AS CODE: the schema of every setting the application reads, in one place.
 *
 * <p>This enum is the single source for parsing and validation ({@link AppConfig#from}), for masking
 * secrets in logs, and for the committed template {@code config/app.env.example}
 * ({@link AppConfig#envTemplate()}; {@code ConfigurationTest} fails if the file drifts).
 * Which values are ALLOWED in a given environment (for example "production needs secure cookies")
 * is policy, kept separately as Rego in {@code policy/config.rego}.
 */
public enum Setting {

    APP_PROFILE("dev", "Deployment profile: dev or prod (prod is checked by policy/config.rego)",
            v -> v.equals("dev") || v.equals("prod"), "dev or prod"),
    APP_BIND("127.0.0.1", "Address the HTTP server listens on (0.0.0.0 = all interfaces)",
            v -> !v.isBlank() && !v.contains(" "), "an IP address or host name"),
    APP_PORT("8080", "HTTP port (0 = any free port, used by tests)", Setting::isPort, "0-65535"),
    COOKIE_SECURE("false", "Mark the session cookie Secure and send HSTS (required behind HTTPS)",
            Setting::isBoolean, "true or false"),
    APP_DEMO_DATA("true", "Load demo jobs, workers and users at start-up", Setting::isBoolean, "true or false"),
    DB_URL("jdbc:postgresql://localhost:5432/joballoc",
            "Where data is stored: a JDBC URL (the prefix selects PostgreSQL or MySQL) or memory: (no database)",
            v -> InMemoryDAOFactory.URL.equals(v) || DialectFactory.isSupported(v),
            "jdbc:postgresql://host:port/db, jdbc:mysql://host:port/db or memory:"),
    DB_USER("joballoc", "Database user", v -> !v.isBlank(), "a non-empty user name"),
    DB_PASSWORD("joballoc", "Database password (inject from a secret store outside dev)", v -> true, "any", true),
    DB_POOL_SIZE("10", "Maximum number of pooled database connections",
            v -> isIntIn(v, 1, Limits.MAX_POOL_SIZE), "1-" + Limits.MAX_POOL_SIZE),
    SSO_BASE_URL("http://127.0.0.1:8080", "Public URL users reach the app on; SSO redirect URIs are built from it",
            Setting::isHttpUrl, "http(s)://host[:port] without a trailing path"),
    SSO_AUTO_PROVISION("none", "Unknown verified SSO email: none (deny) or viewer (create a read-only account)",
            v -> v.equals("none") || v.equals("viewer"), "none or viewer"),
    SSO_GOOGLE_CLIENT_ID("", "Google OAuth client id (empty = Google sign-in off)", v -> true, "any"),
    SSO_GOOGLE_CLIENT_SECRET("", "Google OAuth client secret (inject from a secret store)", v -> true, "any", true),
    SSO_MICROSOFT_CLIENT_ID("", "Microsoft Entra application (client) id (empty = off)", v -> true, "any"),
    SSO_MICROSOFT_CLIENT_SECRET("", "Microsoft Entra client secret (inject from a secret store)", v -> true, "any",
            true),
    SSO_MICROSOFT_TENANT("organizations", "Entra tenant: a tenant id, organizations, or common",
            v -> v.matches("[A-Za-z0-9.-]+"), "a tenant id or domain, organizations or common"),
    SSO_APPLE_CLIENT_ID("", "Sign in with Apple Services ID (empty = off)", v -> true, "any"),
    SSO_APPLE_TEAM_ID("", "Apple developer Team ID", v -> v.isEmpty() || v.matches("[A-Z0-9]{10}"),
            "10 characters A-Z0-9"),
    SSO_APPLE_KEY_ID("", "Key ID of the Sign in with Apple private key", v -> v.isEmpty() || v.matches("[A-Z0-9]{10}"),
            "10 characters A-Z0-9"),
    SSO_APPLE_PRIVATE_KEY_FILE("", "Path to the Apple .p8 private key file (never commit the file)", v -> true,
            "a file path"),
    SSO_OIDC_NAME("Single sign-on", "Button label of the generic OpenID Connect provider", v -> !v.isBlank(),
            "a label"),
    SSO_OIDC_ISSUER("", "Issuer URL of a generic OpenID Connect provider (Okta, Keycloak...; empty = off)",
            v -> v.isEmpty() || isHttpUrl(v), "an https issuer URL"),
    SSO_OIDC_CLIENT_ID("", "Client id at the generic OpenID Connect provider", v -> true, "any"),
    SSO_OIDC_CLIENT_SECRET("", "Client secret at the generic OpenID Connect provider", v -> true, "any", true);

    /** Numeric bounds used by the validators (enum constants cannot refer to the enum's own static fields). */
    private static final class Limits {
        static final int MAX_POOL_SIZE = 100;
        static final int MAX_PORT = 65_535;

        private Limits() { }
    }

    private final String defaultValue;
    private final String description;
    private final Predicate<String> valid;
    private final String expected;
    private final boolean secret;

    Setting(String defaultValue, String description, Predicate<String> valid, String expected) {
        this(defaultValue, description, valid, expected, false);
    }

    Setting(String defaultValue, String description, Predicate<String> valid, String expected, boolean secret) {
        this.defaultValue = defaultValue;
        this.description = description;
        this.valid = valid;
        this.expected = expected;
        this.secret = secret;
    }

    public String defaultValue() { return defaultValue; }
    public String description() { return description; }
    public String expected() { return expected; }
    public boolean isSecret() { return secret; }

    public boolean isValid(String value) {
        return value != null && valid.test(value.trim());
    }

    private static boolean isHttpUrl(String v) {
        return v.matches("https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/[A-Za-z0-9._~\\-/]*)?") && !v.endsWith("/");
    }

    private static boolean isBoolean(String v) {
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false");
    }

    private static boolean isPort(String v) {
        return isIntIn(v, 0, Limits.MAX_PORT);
    }

    private static boolean isIntIn(String v, int min, int max) {
        try {
            int n = Integer.parseInt(v);
            return n >= min && n <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
