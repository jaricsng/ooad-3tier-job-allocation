package edu.ooad.joballoc.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/** {@code make config-template}: writes {@link AppConfig#envTemplate()} to the given file. */
public final class ConfigTemplate {

    private static final Logger LOG = Logger.getLogger(ConfigTemplate.class.getName());

    private ConfigTemplate() { }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "config/app.env.example");
        Files.writeString(out, AppConfig.envTemplate(), StandardCharsets.UTF_8);
        LOG.info(() -> "Wrote " + out);
    }
}
