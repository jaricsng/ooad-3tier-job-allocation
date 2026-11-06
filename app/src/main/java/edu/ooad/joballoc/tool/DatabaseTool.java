package edu.ooad.joballoc.tool;

import edu.ooad.joballoc.config.AppConfig;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;
import edu.ooad.joballoc.persistence.orm.ConnectionPool;
import edu.ooad.joballoc.persistence.orm.Migrator;
import edu.ooad.joballoc.persistence.orm.Migrator.ScriptStatus;
import edu.ooad.joballoc.storage.InMemoryStorage;
import edu.ooad.joballoc.storage.RelationalStorage;
import edu.ooad.joballoc.storage.Storage;
import edu.ooad.joballoc.storage.StorageTransfer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operations commands for rollouts (see docs/runbook.md, "Release and rollout"). Reads the same
 * configuration as the app (DB_URL, DB_USER, DB_PASSWORD).
 * <pre>
 *   status   applied / pending / changed scripts; exit 1 if the history is invalid   (make db-status)
 *   migrate  apply pending scripts without starting the app                        (make db-migrate)
 *   copy     copy all data to TARGET_DB_URL (a database, or memory: as a dry run)  (make db-copy)
 * </pre>
 */
public final class DatabaseTool {

    private static final int EXIT_PROBLEM = 1;
    private static final int EXIT_USAGE = 2;
    private static final int TOOL_POOL_SIZE = 2;

    private DatabaseTool() { }

    public static void main(String[] args) {
        String command = args.length == 1 ? args[0] : "";
        AppConfig config = AppConfig.fromEnv();
        int exit = switch (command) {
            case "status" -> status(config);
            case "migrate" -> migrate(config);
            case "copy" -> copy(config);
            default -> {
                System.err.println("usage: DatabaseTool status|migrate|copy");
                yield EXIT_USAGE;
            }
        };
        ConnectionPool.shutdown();
        System.exit(exit);
    }

    private static boolean isMemory(String url) {
        return InMemoryDAOFactory.URL.equals(url);
    }

    private static int status(AppConfig config) {
        if (isMemory(config.dbUrl())) {
            System.out.println("DB_URL=memory: has no schema; nothing to migrate.");
            return 0;
        }
        ConnectionPool pool = ConnectionPool.initialize(config.dbUrl(), config.dbUser(), config.dbPassword(),
                TOOL_POOL_SIZE);
        List<ScriptStatus> status = Migrator.status(pool);
        System.out.println("Database: " + pool.dialect().productName() + " (" + config.dbUrl() + ")");
        status.forEach(s -> System.out.printf("  V%-3d %-42s %-8s %s%n", s.version(), s.script(), s.state(),
                s.appliedAt() == null ? "" : s.appliedAt()));
        boolean invalid = status.stream().anyMatch(s -> s.state() == ScriptStatus.State.CHANGED
                || s.state() == ScriptStatus.State.UNKNOWN);
        long pending = status.stream().filter(s -> s.state() == ScriptStatus.State.PENDING).count();
        System.out.println(invalid ? "INVALID: history does not match this build (see runbook, rollback)"
                : pending == 0 ? "Up to date." : pending + " pending: run make db-migrate");
        return invalid ? EXIT_PROBLEM : 0;
    }

    private static int migrate(AppConfig config) {
        if (isMemory(config.dbUrl())) {
            System.out.println("DB_URL=memory: has no schema; nothing to migrate.");
            return 0;
        }
        ConnectionPool pool = ConnectionPool.initialize(config.dbUrl(), config.dbUser(), config.dbPassword(),
                TOOL_POOL_SIZE);
        int applied = Migrator.migrate(pool);
        System.out.println("Applied " + applied + " migration(s).");
        return status(config);
    }

    private static int copy(AppConfig source) {
        Map<String, String> env = new HashMap<>(System.getenv());
        String targetUrl = env.get("TARGET_DB_URL");
        if (targetUrl == null || targetUrl.isBlank()) {
            System.err.println("Set TARGET_DB_URL (and TARGET_DB_USER, TARGET_DB_PASSWORD), or memory: for a dry run");
            return EXIT_USAGE;
        }
        if (isMemory(source.dbUrl())) {
            System.err.println("DB_URL=memory: holds no lasting data to copy");
            return EXIT_USAGE;
        }
        ConnectionPool sourcePool = ConnectionPool.initialize(source.dbUrl(), source.dbUser(), source.dbPassword(),
                TOOL_POOL_SIZE);
        if (Migrator.status(sourcePool).stream().anyMatch(s -> s.state() != ScriptStatus.State.APPLIED)) {
            System.err.println("The source database is not exactly at this build's schema: run make db-status");
            return EXIT_PROBLEM;
        }
        try (Storage from = new RelationalStorage(sourcePool);
                Storage to = isMemory(targetUrl) ? new InMemoryStorage()
                        : new RelationalStorage(ConnectionPool.standalone(targetUrl,
                                env.getOrDefault("TARGET_DB_USER", source.dbUser()),
                                env.getOrDefault("TARGET_DB_PASSWORD", ""), TOOL_POOL_SIZE))) {
            System.out.println("Copying " + from.description() + " -> " + to.description());
            System.out.print(StorageTransfer.copy(from, to));
            System.out.println(isMemory(targetUrl) ? "Dry run complete: every row loads; nothing was written."
                    : "Copy complete in one transaction. Verify, then point DB_URL at the target.");
            return 0;
        }
    }
}
