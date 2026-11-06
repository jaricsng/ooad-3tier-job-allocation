package edu.ooad.joballoc.arch;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * NAMING AS CODE: a class's name must say what role it plays (docs/coding-conventions.md §2).
 * Checkstyle checks the *form* of every identifier (camelCase, constants, abbreviations); this test
 * checks the *meaning*: the role suffix per package, exception and interface names, and boolean
 * methods that read as questions. It loads every compiled production class from target/classes.
 */
public class NamingConventionTest {

    private static final String ROOT = "edu.ooad.joballoc";
    private static List<Class<?>> classes;

    /** Package -> rule for its public top-level types: the role suffix that names what they are. */
    private static final Map<String, Predicate<String>> SUFFIX_BY_PACKAGE = Map.ofEntries(
            Map.entry("web.controller", n -> n.endsWith("Controller")),
            Map.entry("web.dto", n -> n.endsWith("DTO")),
            Map.entry("web.view", n -> n.endsWith("View") || n.equals("Html")),
            Map.entry("business.event", n -> n.endsWith("Listener") || n.equals("DomainEventPublisher")),
            Map.entry("business.policy", n -> n.endsWith("Policy") || n.endsWith("Factory")),
            Map.entry("business.proxy", n -> n.endsWith("Interceptor") || n.equals("Transactional")),
            Map.entry("persistence.dao", n -> n.endsWith("DAO") || n.endsWith("DAOFactory") || n.endsWith("Exception")),
            Map.entry("persistence.ormdao", n -> n.matches("(Abstract)?Orm[A-Za-z]*(DAO|DAOFactory)")),
            Map.entry("persistence.dialect", n -> n.endsWith("Dialect") || n.equals("DialectFactory")),
            Map.entry("adapter", n -> n.endsWith("Adapter")),
            Map.entry("storage", n -> n.endsWith("Storage") || n.startsWith("Storage")),
            Map.entry("tool", n -> n.endsWith("Tool")));

    /** Prefixes that make a boolean method read as a question: if (worker.canTake(job)). */
    private static final List<String> BOOLEAN_PREFIXES =
            List.of("is", "has", "can", "should", "includes", "contains", "matches", "verify");

    /** Command methods that report success with a boolean; each exception states why. */
    private static final Set<String> BOOLEAN_EXCEPTIONS = Set.of(); // none today; add with a reason

    @BeforeClass
    public static void loadClasses() throws IOException {
        Path root = Path.of("target/classes");
        try (Stream<Path> files = Files.walk(root.resolve(ROOT.replace('.', '/')))) {
            classes = files.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> root.relativize(p).toString().replace('/', '.').replace('\\', '.')
                            .replaceAll("\\.class$", ""))
                    .map(NamingConventionTest::load)
                    .filter(c -> !c.isSynthetic() && !c.isAnonymousClass())
                    .toList();
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, NamingConventionTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pkg(Class<?> c) {
        return c.getPackageName().equals(ROOT) ? "" : c.getPackageName().substring(ROOT.length() + 1);
    }

    @Test
    public void publicTypesCarryTheRoleSuffixOfTheirPackage() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : classes) {
            Predicate<String> rule = SUFFIX_BY_PACKAGE.get(pkg(c));
            boolean topLevelPublic = c.getEnclosingClass() == null && Modifier.isPublic(c.getModifiers());
            if (rule != null && topLevelPublic && !rule.test(c.getSimpleName())) {
                violations.add(pkg(c) + "." + c.getSimpleName());
            }
        }
        assertTrue("Names that do not state their role: " + violations, violations.isEmpty());
    }

    @Test
    public void exceptionsEndWithExceptionAndNothingElseDoes() {
        List<String> violations = classes.stream()
                .filter(c -> Throwable.class.isAssignableFrom(c) != c.getSimpleName().endsWith("Exception"))
                .map(Class::getName).toList();
        assertTrue("Exception naming: " + violations, violations.isEmpty());
    }

    @Test
    public void interfacesHaveNoIPrefixAndAbstractClassesSayAbstract() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : classes) {
            String n = c.getSimpleName();
            if (c.isInterface() && n.matches("I[A-Z].*")) {
                violations.add(n + ": no I prefix (name the role, e.g. AllocationService)");
            }
            if (!c.isInterface() && !c.isEnum() && Modifier.isAbstract(c.getModifiers()) && !n.startsWith("Abstract")) {
                violations.add(n + ": abstract classes start with Abstract");
            }
            if (n.matches(".*(Impl|Manager|Helper|Util|Data|Info)\\d+") || n.matches(".*\\d$")) {
                violations.add(n + ": no numbered or meaningless names");
            }
        }
        assertTrue("Type naming: " + violations, violations.isEmpty());
    }

    @Test
    public void booleanMethodsReadAsQuestions() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : classes) {
            Set<String> recordComponents = c.isRecord()
                    ? Arrays.stream(c.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet())
                    : Set.of();
            for (Method m : c.getDeclaredMethods()) {
                String id = c.getSimpleName() + "." + m.getName();
                boolean candidate = m.getReturnType() == boolean.class && Modifier.isPublic(m.getModifiers())
                        && !m.isSynthetic() && !m.getName().equals("equals")
                        && !recordComponents.contains(m.getName()) && !BOOLEAN_EXCEPTIONS.contains(id);
                if (candidate && BOOLEAN_PREFIXES.stream().noneMatch(m.getName()::startsWith)) {
                    violations.add(id + "()");
                }
            }
        }
        assertTrue("Boolean methods should start with " + BOOLEAN_PREFIXES + ": " + violations,
                violations.isEmpty());
    }
}
