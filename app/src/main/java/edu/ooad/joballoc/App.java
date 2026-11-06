package edu.ooad.joballoc;

import edu.ooad.joballoc.business.AllocationService;
import edu.ooad.joballoc.business.BusinessTier;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.config.AppConfig;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.orm.DemoData;
import edu.ooad.joballoc.storage.Storage;
import edu.ooad.joballoc.storage.StorageFactory;
import edu.ooad.joballoc.web.WebServer;
import edu.ooad.joballoc.web.sso.OidcProviders;
import edu.ooad.joballoc.web.sso.SsoService;

import java.net.BindException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.logging.Logger;

/**
 * COMPOSITION ROOT: the one place that creates and wires the three tiers.
 * Persistence (pool, transactions, DAOs) -> Business (via the BusinessTier factory) -> Presentation (web).
 * Adapters connect the persistence tier to ports owned by the business tier (Dependency Inversion).
 */
public final class App {

    /** Exit status when the HTTP port is taken (distinct from a crash, for scripts). */
    static final int EXIT_PORT_IN_USE = 3;

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    /** The running application (used by main and by integration tests). */
    public record Running(WebServer web, AllocationService service, Storage storage, DAOFactory daos) {
        public void stop() {
            web.stop();
            storage.close();
        }
    }

    /** The HTTP port is already taken: a clear message instead of a stack trace. */
    static final class PortInUseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PortInUseException(String bind, int port, BindException cause) {
            super("Port " + port + " on " + bind + " is already in use, probably by another copy of this app."
                    + " Stop it (Ctrl-C in its terminal; find it with: lsof -nP -iTCP:" + port + " -sTCP:LISTEN),"
                    + " or use another port, e.g. APP_PORT=" + (port + 1) + " in .env", cause);
        }
    }

    private App() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tFT%1$tT %4$s %3$s - %5$s%6$s%n");
        AppConfig config = AppConfig.fromEnv();
        LOG.info(() -> "Starting Job Allocation " + AppConfig.VERSION + " with " + config);
        Running app;
        try {
            app = start(config, Clock.systemDefaultZone());
        } catch (PortInUseException e) {
            LOG.severe(e.getMessage());
            System.exit(EXIT_PORT_IN_USE);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        LOG.info(() -> "Listening on http://" + config.bindAddress() + ":" + app.web().port() + "/");
    }

    public static Running start(AppConfig config, Clock clock) throws Exception {
        // Persistence tier: DB_URL picks the storage family (ABSTRACT FACTORY via a FACTORY METHOD)
        Storage storage = StorageFactory.create(config);
        LOG.info(() -> "Storage: " + storage.description());
        DAOFactory daos = storage.daos();
        if (config.demoData()) {
            LocalDate today = LocalDate.now(clock);
            storage.transactions().inTransaction(() -> DemoData.loadMissing(daos.jobDao(), today));
        }

        // Business tier (FACTORY); the ADAPTER plugs the transaction manager into the business tier's port
        BusinessTier business = BusinessTier.create(daos, storage.transactions(), clock);

        // Presentation tier
        // Single sign-on: providers are enabled by their client id (SSO_* settings)
        Map<String, String> sso = config.ssoSettings();
        SsoService ssoService = new SsoService(OidcProviders.fromSettings(sso), sso.get("SSO_BASE_URL"), clock);
        AuthService.Provisioning provisioning = "viewer".equals(sso.get("SSO_AUTO_PROVISION"))
                ? AuthService.Provisioning.VIEWER : AuthService.Provisioning.NONE;
        WebServer web;
        try {
            web = new WebServer(config.bindAddress(), config.port(), business,
                    storage.health(), clock,
                    config.cookieSecure(), AppConfig.VERSION, ssoService, provisioning);
        } catch (BindException e) {
            storage.close();
            throw new PortInUseException(config.bindAddress(), config.port(), e);
        }
        web.start();
        return new Running(web, business.service(), storage, daos);
    }
}
