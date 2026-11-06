package edu.ooad.joballoc.web.api;

import edu.ooad.joballoc.business.BusinessTier;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;
import edu.ooad.joballoc.web.WebServer;
import edu.ooad.joballoc.web.dto.AllocateRequestDTO;
import edu.ooad.joballoc.web.dto.AllocationDTO;
import edu.ooad.joballoc.web.dto.AllocationResultDTO;
import edu.ooad.joballoc.web.dto.AutoAllocateRequestDTO;
import edu.ooad.joballoc.web.dto.AutoAllocationResultDTO;
import edu.ooad.joballoc.web.dto.JobSummaryDTO;
import edu.ooad.joballoc.web.dto.ProblemDTO;
import edu.ooad.joballoc.web.dto.WorkerCandidateDTO;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * CONTRACT TEST: the OpenAPI document and the code must agree, in both directions.
 * <ol>
 *   <li>every /api route in the router is documented, and every documented path exists</li>
 *   <li>every DTO record's fields equal its schema's properties</li>
 * </ol>
 * The document is read as plain text with a small reader for its block style (no YAML library is
 * vendored); Spectral ({@code make api-lint}) validates the document itself.
 */
public class OpenApiContractTest {

    private static final Path SPEC = Path.of("src/main/resources/api/openapi.yaml");
    private static final Pattern PATH_LINE = Pattern.compile("^  (/api/\\S+):$");
    private static final Pattern METHOD_LINE = Pattern.compile("^    (get|post|put|patch|delete):$");
    private static final Pattern SCHEMA_LINE = Pattern.compile("^    ([A-Za-z]+):$");
    private static final Pattern PROPERTY_LINE = Pattern.compile("^        ([A-Za-z]+):$");

    /** DTO record -> schema name in components/schemas. */
    private static final Map<Class<?>, String> DTO_SCHEMAS = new LinkedHashMap<>();

    static {
        DTO_SCHEMAS.put(JobSummaryDTO.class, "JobSummary");
        DTO_SCHEMAS.put(WorkerCandidateDTO.class, "WorkerCandidate");
        DTO_SCHEMAS.put(AllocateRequestDTO.class, "AllocateRequest");
        DTO_SCHEMAS.put(AllocationResultDTO.class, "AllocationResult");
        DTO_SCHEMAS.put(AutoAllocateRequestDTO.class, "AutoAllocateRequest");
        DTO_SCHEMAS.put(AutoAllocationResultDTO.class, "AutoAllocationResult");
        DTO_SCHEMAS.put(AllocationDTO.class, "Allocation");
        DTO_SCHEMAS.put(ProblemDTO.class, "Problem");
    }

    private static WebServer web;
    private static List<String> lines;

    @BeforeClass
    public static void setUp() throws IOException {
        lines = Files.readAllLines(SPEC, StandardCharsets.UTF_8);
        BusinessTier business = BusinessTier.create(new InMemoryDAOFactory(), TransactionRunner.NONE,
                Clock.systemUTC());
        web = new WebServer("127.0.0.1", 0, business, () -> true, Clock.systemUTC(), false, "contract-test");
    }

    @AfterClass
    public static void tearDown() {
        web.stop();
    }

    @Test
    public void everyApiRouteIsDocumentedAndEveryDocumentedOperationExists() {
        Set<String> implemented = web.routes().stream()
                .filter(r -> r.template().startsWith("/api/"))
                .map(r -> r.method() + " " + r.template())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals("routes in WebServer vs paths in openapi.yaml", documentedOperations(), implemented);
    }

    @Test
    public void everyDtoMatchesItsSchema() {
        DTO_SCHEMAS.forEach((dto, schema) -> {
            Set<String> fields = Arrays.stream(dto.getRecordComponents()).map(c -> c.getName())
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(dto.getSimpleName() + " vs schema " + schema, schemaProperties(schema), fields);
        });
    }

    private static Set<String> documentedOperations() {
        Set<String> ops = new TreeSet<>();
        String path = null;
        for (String line : lines) {
            Matcher p = PATH_LINE.matcher(line);
            if (p.matches()) {
                path = p.group(1);
                continue;
            }
            if (!line.startsWith("    ") && !line.isBlank()) {
                path = line.startsWith("  /") ? path : null;
            }
            Matcher m = METHOD_LINE.matcher(line);
            if (path != null && m.matches()) {
                ops.add(m.group(1).toUpperCase() + " " + path);
            }
        }
        return ops;
    }

    private static Set<String> schemaProperties(String schema) {
        int start = lines.indexOf("  schemas:");
        Set<String> props = new TreeSet<>();
        boolean inSchema = false;
        boolean inProperties = false;
        for (String line : lines.subList(start + 1, lines.size())) {
            Matcher s = SCHEMA_LINE.matcher(line);
            if (s.matches()) {
                inSchema = s.group(1).equals(schema);
                inProperties = false;
            } else if (inSchema && line.equals("      properties:")) {
                inProperties = true;
            } else if (inSchema && line.startsWith("      ") && !line.startsWith("       ")) {
                inProperties = false;
            } else if (inProperties) {
                Matcher p = PROPERTY_LINE.matcher(line);
                if (p.matches()) {
                    props.add(p.group(1));
                }
            }
        }
        return props;
    }
}
