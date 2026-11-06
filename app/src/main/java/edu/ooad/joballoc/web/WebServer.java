package edu.ooad.joballoc.web;

import com.sun.net.httpserver.HttpServer;
import edu.ooad.joballoc.business.BusinessTier;
import edu.ooad.joballoc.business.port.DatabaseHealth;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.web.controller.ApiController;
import edu.ooad.joballoc.web.controller.ApiDocsController;
import edu.ooad.joballoc.web.controller.BoardController;
import edu.ooad.joballoc.web.controller.HealthController;
import edu.ooad.joballoc.web.controller.LoginController;
import edu.ooad.joballoc.web.controller.SsoController;
import edu.ooad.joballoc.web.controller.StaticController;
import edu.ooad.joballoc.web.http.FrontController;
import edu.ooad.joballoc.web.http.IdempotencyStore;
import edu.ooad.joballoc.web.http.IdempotentHandler;
import edu.ooad.joballoc.web.http.Response;
import edu.ooad.joballoc.web.http.Router;
import edu.ooad.joballoc.web.http.Router.Access;
import edu.ooad.joballoc.web.http.SessionManager;
import edu.ooad.joballoc.web.sso.SsoService;
import edu.ooad.joballoc.web.view.LoginView;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Presentation tier entry point: registers routes (the UI Service) and starts the JDK HTTP server. */
public class WebServer {

    /** How long a stored response is replayed for the same Idempotency-Key. */
    private static final Duration IDEMPOTENCY_KEY_LIFETIME = Duration.ofHours(24);

    private final HttpServer server;
    private final Router router;
    private final ExecutorService executor;

    /**
     * @param business the business tier (facade, sign-in, metrics) built by {@link BusinessTier#create}
     * @param database the health port; the presentation tier never sees the persistence tier
     */
    public WebServer(String bind, int port, BusinessTier business, DatabaseHealth database, Clock clock,
                     boolean secureCookie, String version) throws IOException {
        this(bind, port, business, database, clock, secureCookie, version,
                new SsoService(List.of(), "http://127.0.0.1", clock), AuthService.Provisioning.NONE);
    }

    /** With single sign-on: the providers in {@code sso} get buttons on the sign-in page and /sso routes. */
    public WebServer(String bind, int port, BusinessTier business, DatabaseHealth database, Clock clock,
                     boolean secureCookie, String version, SsoService sso, AuthService.Provisioning provisioning)
            throws IOException {
        AuthService auth = business.auth();
        SessionManager sessions = new SessionManager(clock, secureCookie);
        LoginController login = new LoginController(auth, sessions, sso.providers().stream()
                .map(p -> new LoginView.SsoButton(p.id(), p.displayName())).toList());
        SsoController ssoController = new SsoController(sso, auth, sessions, provisioning, secureCookie);
        BoardController board = new BoardController(business.service(), clock);
        ApiController api = new ApiController(business.service(), clock);
        HealthController health = new HealthController(database, business.metrics(), version);
        StaticController statics = new StaticController();
        ApiDocsController docs = new ApiDocsController();
        IdempotencyStore idempotency = new IdempotencyStore(clock, IDEMPOTENCY_KEY_LIFETIME);

        this.router = new Router()
                .add("GET", "/", Access.PUBLIC, r -> Response.redirect("/board"))
                .add("GET", "/health", Access.PUBLIC, health::health)
                .add("GET", "/static/{file}", Access.PUBLIC, statics::serve)
                .add("GET", "/login", Access.PUBLIC, login::form)
                .add("POST", "/login", Access.PUBLIC, login::login)
                .add("POST", "/logout", Access.PAGE, login::logout)
                .add("GET", "/sso/{provider}", Access.PUBLIC, ssoController::start)
                .add("GET", "/sso/{provider}/callback", Access.PUBLIC, ssoController::callback)
                .add("POST", "/sso/{provider}/callback", Access.PUBLIC, ssoController::callback)
                .add("GET", "/board", Access.PAGE, board::board)
                .add("POST", "/board/allocate", Access.PAGE, board::allocate)
                .add("POST", "/board/auto-allocate", Access.PAGE, board::autoAllocate)
                .add("POST", "/board/unassign", Access.PAGE, board::unassign)
                .add("GET", "/api/v1/openapi.yaml", Access.API, docs::openapi)
                .add("GET", "/api/v1/jobs", Access.API, api::pendingJobs)
                .add("GET", "/api/v1/jobs/{id}", Access.API, api::job)
                .add("GET", "/api/v1/jobs/{id}/eligible-workers", Access.API, api::eligibleWorkers)
                .add("GET", "/api/v1/allocations", Access.API, api::allocations)
                .add("GET", "/api/v1/allocations/{id}", Access.API, api::allocation)
                // DECORATOR: retries with the same Idempotency-Key never allocate twice
                .add("POST", "/api/v1/allocations", Access.API, IdempotentHandler.wrap(api::allocate, idempotency))
                .add("POST", "/api/v1/allocations/auto", Access.API,
                        IdempotentHandler.wrap(api::autoAllocate, idempotency))
                .add("DELETE", "/api/v1/allocations/{id}", Access.API, api::unassign);

        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", new FrontController(router, sessions, auth, secureCookie));
    }

    /** The route table: read by the OpenAPI contract test and the access-policy export (policy as code). */
    public List<Router.Route> routes() {
        return router.routes();
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(1);
        executor.shutdown();
    }
}
