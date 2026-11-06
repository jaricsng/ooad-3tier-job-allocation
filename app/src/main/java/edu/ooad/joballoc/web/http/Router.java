package edu.ooad.joballoc.web.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps "METHOD /path/{var}" to handlers; part of the FRONT CONTROLLER. */
public class Router {

    /** How a route is protected. */
    public enum Access { PUBLIC, PAGE, API }

    /** A registered route. */
    public record Route(String method, Pattern pattern, List<String> vars, Handler handler, Access access,
            String template) { }

    /** A route matched against a concrete path. */
    public record Match(Route route, Map<String, String> vars) { }

    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String template, Access access, Handler handler) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([a-zA-Z]+)}").matcher(template);
        StringBuilder regex = new StringBuilder();
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(template.substring(last, m.start()))).append("([^/]+)");
            vars.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        routes.add(new Route(method, Pattern.compile(regex.toString()), vars, handler, access, template));
        return this;
    }

    public Optional<Match> match(String method, String path) {
        for (Route r : routes) {
            Matcher m = r.pattern().matcher(path);
            if (r.method().equals(method) && m.matches()) {
                Map<String, String> vars = new LinkedHashMap<>();
                for (int i = 0; i < r.vars().size(); i++) {
                    vars.put(r.vars().get(i), m.group(i + 1));
                }
                return Optional.of(new Match(r, vars));
            }
        }
        return Optional.empty();
    }

    /** True if some route matches the path with a different method (HTTP 405). */
    public boolean hasPath(String path) {
        return routes.stream().anyMatch(r -> r.pattern().matcher(path).matches());
    }

    public List<Route> routes() {
        return List.copyOf(routes);
    }
}
