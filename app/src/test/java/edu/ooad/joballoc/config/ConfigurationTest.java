package edu.ooad.joballoc.config;

import edu.ooad.joballoc.persistence.dialect.DialectFactory;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** CONFIGURATION AS CODE: the schema validates, masks secrets and generates the committed template. */
public class ConfigurationTest {

    private static final Path TEMPLATE = Path.of("config/app.env.example");

    @Test
    public void defaultsAreValidForLocalDevelopment() {
        AppConfig c = AppConfig.from(Map.of());
        assertEquals("dev", c.profile());
        assertEquals("127.0.0.1", c.bindAddress());
        assertEquals(8080, c.port());
        assertFalse(c.cookieSecure());
    }

    @Test
    public void everyInvalidSettingIsReportedAtOnce() {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> AppConfig.from(Map.of(
                "APP_PORT", "80a", "DB_POOL_SIZE", "0", "DB_URL", "jdbc:oracle:thin:@x:1521/y",
                "COOKIE_SECURE", "yes", "APP_PROFILE", "staging", "DB_PASSWORD", "")));
        assertEquals(e.getMessage(), 5, e.problems().size());
        assertTrue(e.getMessage().contains("APP_PORT='80a' is invalid; expected 0-65535"));
    }

    @Test
    public void everySupportedDatabaseUrlIsAccepted() {
        for (String prefix : DialectFactory.supportedPrefixes()) {
            assertEquals(prefix + "h:1/db", AppConfig.from(Map.of("DB_URL", prefix + "h:1/db")).dbUrl());
        }
    }

    @Test
    public void secretsNeverAppearInLogsOrErrors() {
        AppConfig c = AppConfig.from(Map.of("DB_PASSWORD", "s3cret!"));
        assertFalse(c.toString().contains("s3cret!"));
        assertTrue(Setting.DB_PASSWORD.isSecret());
    }

    /** Golden file: regenerate with `make config-template` after changing the Setting enum. */
    @Test
    public void committedTemplateMatchesTheSchema() throws IOException {
        assertEquals("config/app.env.example is out of date; run: make config-template",
                AppConfig.envTemplate(), Files.readString(TEMPLATE, StandardCharsets.UTF_8));
    }

    /** The template itself must parse and validate (it is what new developers copy). */
    @Test
    public void committedTemplateIsAValidConfiguration() throws IOException {
        Map<String, String> env = new java.util.HashMap<>();
        for (String line : Files.readAllLines(TEMPLATE, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] kv = line.split("=", 2);
                env.put(kv[0], kv[1]);
            }
        }
        assertEquals("dev", AppConfig.from(env).profile());
    }
}
