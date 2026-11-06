package edu.ooad.joballoc.arch;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * Architecture fitness function: the dependency arrows in the UML diagrams are enforced by a
 * test, so code and diagrams cannot drift apart. Layers may only depend downward.
 */
public class ArchitectureTest {

    private static final Path SRC = Path.of("src/main/java/edu/ooad/joballoc");
    private static final Pattern IMPORT =
            Pattern.compile("^import (?:static )?edu\\.ooad\\.joballoc\\.([a-z]+(?:\\.[a-z]+)?)", Pattern.MULTILINE);

    private record Rule(String layer, List<String> forbidden) { }

    private static final List<Rule> RULES = List.of(
            // Domain may use only the mapping annotations (like javax.persistence) from persistence.
            new Rule("domain", List.of("business", "web", "config", "adapter", "persistence.dao", "persistence.orm",
                    "persistence.ormdao", "persistence.memory", "persistence.dialect", "storage", "tool")),
            new Rule("persistence", List.of("business", "web", "config", "adapter", "storage", "tool")),
            // Business sees only the DAO contract (interfaces + exceptions), never the ORM or JDBC.
            new Rule("business", List.of("web", "config", "adapter", "persistence.orm", "persistence.ormdao",
                    "persistence.memory", "persistence.mapping", "persistence.dialect", "storage", "tool")),
            // Presentation reaches the data only through the facade and the business tier's ports.
            new Rule("web", List.of("persistence", "adapter", "config", "storage", "tool")),
            // Adapters connect persistence to business ports; they never know about HTTP.
            new Rule("adapter", List.of("web", "storage", "tool")),
            // The storage families are assembled outside the tiers; only App and tools choose one.
            new Rule("storage", List.of("web", "tool")),
            // Operations tools work on storage and configuration, never through the web tier.
            new Rule("tool", List.of("web")));

    @Test
    public void layersDependOnlyDownward() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Rule rule : RULES) {
            if (!Files.isDirectory(SRC.resolve(rule.layer()))) {
                continue;
            }
            try (Stream<Path> files = Files.walk(SRC.resolve(rule.layer()))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = IMPORT.matcher(Files.readString(f));
                    while (m.find()) {
                        String target = m.group(1);
                        for (String bad : rule.forbidden()) {
                            if (target.equals(bad) || target.startsWith(bad + ".")) {
                                violations.add(SRC.relativize(f) + " imports " + target);
                            }
                        }
                    }
                }
            }
        }
        assertTrue("Layering violations:\n" + String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void presentationGoesThroughTheFacade() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC.resolve("web"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String s = Files.readString(f);
                boolean daoImpl = s.contains("persistence.ormdao") || s.contains("persistence.memory");
                boolean jdbc = s.contains("import java.sql.");
                if (daoImpl || jdbc) {
                    violations.add(SRC.relativize(f).toString());
                }
            }
        }
        assertTrue("Web tier must use the AllocationService facade, not DAOs or JDBC: " + violations,
                violations.isEmpty());
    }

    /** The JDK server API is confined to the ADAPTER and the two classes that start and enter the server. */
    @Test
    public void onlyTheAdapterTouchesTheJdkHttpServer() throws IOException {
        List<String> allowed = List.of("HttpExchangeAdapter.java", "FrontController.java", "WebServer.java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(f).contains("import com.sun.net.httpserver.")
                        && !allowed.contains(f.getFileName().toString())) {
                    violations.add(SRC.relativize(f).toString());
                }
            }
        }
        assertTrue("com.sun.net.httpserver used outside the adapter: " + violations, violations.isEmpty());
    }

    /** The business tier must not know vendor SQL error codes (exception translation happens in persistence). */
    @Test
    public void businessTierHasNoJdbc() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC.resolve("business"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(f).contains("import java.sql.")) {
                    violations.add(SRC.relativize(f).toString());
                }
            }
        }
        assertTrue("JDBC in the business tier: " + violations, violations.isEmpty());
    }
}
