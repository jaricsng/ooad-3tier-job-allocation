package edu.ooad.joballoc.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Typed, validated settings read from environment variables (12-factor). The schema is the
 * {@link Setting} enum. Invalid values stop the application at start-up with every problem listed
 * (FAIL FAST); before, a typo such as {@code APP_PORT=80a} surfaced as a bare NumberFormatException,
 * and an unknown profile or a malformed URL was not noticed until the first request.
 * Secrets are never logged.
 */
public record AppConfig(String profile, String dbUrl, String dbUser, String dbPassword, int dbPoolSize, int port,
                        boolean cookieSecure, boolean demoData, String bindAddress, Map<Setting, String> values) {

    public static final String VERSION = "1.1.0";

    public static AppConfig fromEnv() {
        return from(System.getenv());
    }

    public static AppConfig from(Map<String, String> env) {
        Map<Setting, String> v = new EnumMap<>(Setting.class);
        List<String> problems = new ArrayList<>();
        for (Setting s : Setting.values()) {
            String raw = env.getOrDefault(s.name(), s.defaultValue()).trim();
            if (!s.isValid(raw)) {
                problems.add(s.name() + "=" + (s.isSecret() ? "***" : "'" + raw + "'") + " is invalid; expected "
                        + s.expected());
            }
            v.put(s, raw);
        }
        if (!problems.isEmpty()) {
            throw new ConfigurationException(problems);
        }
        return new AppConfig(v.get(Setting.APP_PROFILE), v.get(Setting.DB_URL), v.get(Setting.DB_USER),
                v.get(Setting.DB_PASSWORD), Integer.parseInt(v.get(Setting.DB_POOL_SIZE)),
                Integer.parseInt(v.get(Setting.APP_PORT)), Boolean.parseBoolean(v.get(Setting.COOKIE_SECURE)),
                Boolean.parseBoolean(v.get(Setting.APP_DEMO_DATA)), v.get(Setting.APP_BIND), Map.copyOf(v));
    }

    /** The single sign-on settings by name (SSO_*), handed as plain values to the web tier. */
    public Map<String, String> ssoSettings() {
        Map<String, String> sso = new java.util.TreeMap<>();
        values.forEach((k, val) -> {
            if (k.name().startsWith("SSO_")) {
                sso.put(k.name(), val);
            }
        });
        return sso;
    }

    /** The committed {@code config/app.env.example}, generated from the schema so the two cannot drift. */
    public static String envTemplate() {
        String header = """
                # Job Allocation: application settings (CONFIGURATION AS CODE).
                # GENERATED from edu.ooad.joballoc.config.Setting; ConfigurationTest fails if this file drifts.
                # Copy to .env (git-ignored) or export as environment variables. Values shown are the defaults.
                # Production rules (secure cookies, no demo data, no default password) are enforced by
                # policy/config.rego: make policy
                """;
        return header + Stream.of(Setting.values())
                .map(s -> "\n# " + s.description() + " [" + s.expected() + "]" + (s.isSecret() ? " SECRET" : "")
                        + "\n" + s.name() + "=" + s.defaultValue())
                .collect(Collectors.joining("\n"))
                + "\n\n# Host ports for the database containers"
                + " (read by compose.yaml and the Makefile, not by the app)\n"
                + "DB_PORT=5432\n"
                + "MYSQL_PORT=3306\n";
    }

    @Override
    public String toString() {
        return "AppConfig[profile=" + profile + ", dbUrl=" + dbUrl + ", dbUser=" + dbUser
                + ", dbPassword=***, dbPoolSize="
                + dbPoolSize + ", port=" + port + ", bind=" + bindAddress + ", cookieSecure=" + cookieSecure
                + ", demoData=" + demoData + ", sso=" + ssoSettings().entrySet().stream()
                        .filter(e -> e.getKey().endsWith("_CLIENT_ID") && !e.getValue().isEmpty())
                        .map(e -> e.getKey().replace("SSO_", "").replace("_CLIENT_ID", "").toLowerCase())
                        .toList() + "]";
    }
}
