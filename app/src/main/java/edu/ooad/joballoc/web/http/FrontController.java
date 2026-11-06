package edu.ooad.joballoc.web.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.business.security.ForbiddenException;
import edu.ooad.joballoc.business.security.SecurityContext;
import edu.ooad.joballoc.business.security.UnauthenticatedException;
import edu.ooad.joballoc.web.dto.ProblemDTO;
import edu.ooad.joballoc.web.view.Html;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FRONT CONTROLLER (PoEAA): every HTTP request enters here. It applies cross-cutting concerns in
 * one place, then dispatches to a page or API controller:
 * <ol>
 *   <li>security headers on every response (CSP, nosniff, frame denial, no-store)</li>
 *   <li>authentication (session cookie, or HTTP Basic for API clients)</li>
 *   <li>CSRF check for state-changing requests that rely on the session cookie</li>
 *   <li>binding the {@link Actor} to the {@link SecurityContext} for the business tier</li>
 *   <li>mapping exceptions to HTTP status codes without leaking internals</li>
 * </ol>
 */
public class FrontController implements HttpHandler {

    public static final String CSRF_FIELD = "_csrf";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final Logger LOG = Logger.getLogger(FrontController.class.getName());
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String BASIC_PREFIX = "Basic ";

    private final Router router;
    private final SessionManager sessions;
    private final AuthService auth;
    private final boolean hsts;

    public FrontController(Router router, SessionManager sessions, AuthService auth, boolean hsts) {
        this.router = router;
        this.sessions = sessions;
        this.auth = auth;
        this.hsts = hsts;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        Request request = null;
        Response response;
        try {
            request = HttpExchangeAdapter.toRequest(exchange);
            response = dispatch(request);
        } catch (Request.PayloadTooLargeException e) {
            response = Response.problem(ProblemDTO.of(HttpStatus.PAYLOAD_TOO_LARGE, "The request body exceeds "
                    + Request.MAX_BODY_BYTES + " bytes", exchange.getRequestURI().getPath(), null));
        }
        if (request != null) {
            response.setHeader(Request.REQUEST_ID_HEADER, request.requestId());
            accessLog(request, response, start);
        }
        HttpExchangeAdapter.write(exchange, withSecurityHeaders(response));
    }

    /** One line per request, keyed by the correlation ID (never the query string, which may hold personal data). */
    private static void accessLog(Request request, Response response, long startNanos) {
        long ms = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        LOG.info(() -> "requestId=" + request.requestId() + " " + request.method() + " " + request.path()
                + " status=" + response.status() + " ms=" + ms);
    }

    Response dispatch(Request request) {
        Optional<Router.Match> match = router.match(request.method(), request.path());
        if (match.isEmpty()) {
            boolean exists = router.hasPath(request.path());
            HttpStatus status = exists ? HttpStatus.METHOD_NOT_ALLOWED : HttpStatus.NOT_FOUND;
            return request.path().startsWith("/api/")
                    ? Response.problem(ProblemDTO.of(status, exists ? "Use another HTTP method" : "No such resource",
                            request.path(), request.requestId()))
                    : Response.text(status, exists ? "Method not allowed" : "Not found");
        }
        Router.Route route = match.get().route();
        boolean api = route.access() == Router.Access.API;
        try {
            Auth auth = authenticate(request, route);
            request.bind(match.get().vars(), auth.session(), auth.actor());
            return auth.actor() == null
                    ? route.handler().handle(request)
                    : SecurityContext.runAs(auth.actor(), () -> invoke(route.handler(), request));
        } catch (Exception e) {
            return error(e, api, request);
        }
    }

    /** Who is calling, and how they proved it. */
    private record Auth(Session session, Actor actor, boolean basic) { }

    /** Session cookie first; HTTP Basic for API routes; then the access and CSRF rules of the route. */
    private Auth authenticate(Request request, Router.Route route) {
        boolean api = route.access() == Router.Access.API;
        Session session = sessions.find(request.cookie(SessionManager.COOKIE)).orElse(null);
        Actor actor = session != null ? session.actor() : null;
        boolean basic = false;
        if (actor == null && api) {
            actor = basicAuth(request).orElse(null);
            basic = actor != null;
        }
        boolean isPublic = route.access() == Router.Access.PUBLIC;
        if (!isPublic && actor == null) {
            throw new UnauthenticatedException("Sign-in required");
        }
        if (UNSAFE.contains(request.method()) && session != null && !basic && !isPublic) {
            checkCsrf(request, session);
        }
        return new Auth(session, actor, basic);
    }

    private static Response invoke(Handler h, Request r) {
        try {
            return h.handle(r);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<Actor> basicAuth(Request request) {
        String header = request.header("Authorization");
        if (header == null || !header.startsWith(BASIC_PREFIX)) {
            return Optional.empty();
        }
        try {
            String encoded = header.substring(BASIC_PREFIX.length()).trim();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return Optional.empty();
            }
            return auth.authenticate(decoded.substring(0, colon), decoded.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void checkCsrf(Request request, Session session) {
        String supplied = request.header(CSRF_HEADER);
        if (supplied == null) {
            supplied = request.param(CSRF_FIELD);
        }
        boolean ok = supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), session.csrfToken().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new ForbiddenException("Missing or invalid CSRF token");
        }
    }

    /**
     * Maps exceptions to status codes (table in {@link ErrorMapper}). Unexpected errors get a
     * reference ID, never a stack trace.
     */
    private Response error(Exception e, boolean api, Request request) {
        HttpStatus status = ErrorMapper.statusOf(e);
        String message = e.getMessage();
        if (status == HttpStatus.UNAUTHORIZED && !api) {
            return Response.redirect("/login");
        }
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            LOG.log(Level.SEVERE, "Unhandled error requestId=" + request.requestId(), e);
            message = "Unexpected error (reference " + request.requestId() + "). See the server log.";
        } else if (status == HttpStatus.FORBIDDEN) {
            LOG.warning(() -> "Forbidden requestId=" + request.requestId() + ": " + e.getMessage());
        }
        if (!api) {
            return Response.html(ErrorPage.render(status, message)).status(status);
        }
        Response problem = Response.problem(ProblemDTO.of(status, message, request.path(), request.requestId()));
        return status == HttpStatus.UNAUTHORIZED
                ? problem.header("WWW-Authenticate", "Basic realm=\"joballoc\", charset=\"UTF-8\"")
                : problem;
    }

    /** Security headers added to every response, in one place (OWASP Secure Headers). */
    private Response withSecurityHeaders(Response response) {
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'none'; style-src 'self'; "
                + "img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (!response.hasHeader("Cache-Control")) {
            response.setHeader("Cache-Control", "no-store");
        }
        if (hsts) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        return response;
    }

    /** Error page in the shared page layout (before: a second hand-written copy of the HTML skeleton). */
    static final class ErrorPage {
        private ErrorPage() { }

        static String render(HttpStatus status, String message) {
            return Html.page("Error " + status.code(), "", "<main class=\"container py-5\"><div class=\"row"
                    + " justify-content-center\"><div class=\"col-12 col-md-8 col-lg-6\"><div class=\"card shadow-sm"
                    + " border-0\"><div class=\"card-body p-4\"><h1 class=\"h4 text-app\">Error " + status.code()
                    + " · " + Html.esc(status.title()) + "</h1><div class=\"alert alert-danger mt-3\">"
                    + Html.esc(message) + "</div><a class=\"btn btn-outline-primary\" href=\"/board\">Back to the"
                    + " board</a></div></div></div></div></main>");
        }
    }
}
