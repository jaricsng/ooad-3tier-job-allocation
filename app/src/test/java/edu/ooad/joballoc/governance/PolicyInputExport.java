package edu.ooad.joballoc.governance;

import edu.ooad.joballoc.business.AllocationService;
import edu.ooad.joballoc.business.BusinessTier;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.business.proxy.Transactional;
import edu.ooad.joballoc.business.security.RequiresRole;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;
import edu.ooad.joballoc.web.Json;
import edu.ooad.joballoc.web.WebServer;
import edu.ooad.joballoc.web.http.Router;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POLICY AS CODE input: exports the access-control facts of the running design as JSON, so
 * {@code policy/access.rego} can check them with Conftest (make policy).
 * <ul>
 *   <li>operations: every facade method with its {@code @RequiresRole} and {@code @Transactional}</li>
 *   <li>routes: every HTTP route with its access level (PUBLIC, PAGE, API)</li>
 * </ul>
 * The facts come from the real classes (reflection and the real route table), never from a hand-kept list.
 */
public final class PolicyInputExport {

    private PolicyInputExport() { }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/policy-input/access.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.write(export()));
        System.out.println("Wrote " + out);
    }

    public static Map<String, Object> export() throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("operations", Arrays.stream(AllocationService.class.getMethods())
                .sorted(Comparator.comparing(Method::getName))
                .map(m -> {
                    Map<String, Object> op = new LinkedHashMap<>();
                    RequiresRole role = m.getAnnotation(RequiresRole.class);
                    op.put("name", m.getName());
                    op.put("role", role == null ? null : role.value().name());
                    op.put("transactional", m.isAnnotationPresent(Transactional.class));
                    return op;
                }).toList());
        BusinessTier business = BusinessTier.create(new InMemoryDAOFactory(), TransactionRunner.NONE,
                Clock.systemUTC());
        WebServer web = new WebServer("127.0.0.1", 0, business, () -> true, Clock.systemUTC(), false, "export");
        List<Router.Route> routes = web.routes();
        doc.put("routes", routes.stream().map(r -> Map.of("method", r.method(), "path", r.template(),
                "access", r.access().name())).toList());
        return doc;
    }
}
