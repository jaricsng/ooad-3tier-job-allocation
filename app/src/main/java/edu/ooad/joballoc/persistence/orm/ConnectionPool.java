package edu.ooad.joballoc.persistence.orm;

import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.dialect.DialectFactory;
import edu.ooad.joballoc.persistence.dialect.SqlDialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SINGLETON (GoF creational): exactly one pool of database connections per process.
 *
 * <p>Before: every DAO call opened its own connection with {@code DriverManager}, so the database
 * ran out of connections under load. After: all DAOs borrow from this one bounded pool.
 *
 * <p>Initialisation is thread-safe (synchronised factory method + volatile field). Discussion
 * point: frameworks prefer a DI-managed singleton scope because a static accessor is global state
 * that is hard to replace in tests; {@link #shutdown()} exists only so tests can reset it.
 */
public final class ConnectionPool {

    private static final int ACQUIRE_TIMEOUT_SECONDS = 10;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private static volatile ConnectionPool instance;

    private final String url;
    private final String user;
    private final String password;
    private final SqlDialect dialect;
    private final int maxSize;
    private final BlockingQueue<Connection> idle;
    private final AtomicInteger created = new AtomicInteger();
    private final List<Connection> all = new ArrayList<>();

    private ConnectionPool(String url, String user, String password, int maxSize) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.dialect = DialectFactory.forUrl(url);
        this.maxSize = maxSize;
        this.idle = new ArrayBlockingQueue<>(maxSize);
    }

    /**
     * Creates the single instance on first call; later calls with the same settings return it.
     * A later call with DIFFERENT settings fails fast. Before, it silently returned the old pool, so a
     * test or a second configuration could run against the wrong database without any error.
     */
    public static synchronized ConnectionPool initialize(String url, String user, String password, int maxSize) {
        if (instance == null) {
            if (maxSize < 1) {
                throw new IllegalArgumentException("maxSize must be at least 1");
            }
            instance = new ConnectionPool(url, user, password, maxSize);
        } else if (!instance.url.equals(url) || !instance.user.equals(user) || instance.maxSize != maxSize) {
            throw new IllegalStateException("ConnectionPool already initialised for " + instance.url
                    + " (max " + instance.maxSize + "); call shutdown() first");
        }
        return instance;
    }

    /**
     * A SEPARATE pool that is not the singleton, for tools that need a second database at the same
     * time (copying data from PostgreSQL to MySQL). The application itself never uses this: it keeps
     * exactly one pool. Teaching point: a Singleton assumes "one database per process", and the day
     * that stops being true is the day the pattern needs an escape hatch (or dependency injection).
     */
    public static ConnectionPool standalone(String url, String user, String password, int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1");
        }
        return new ConnectionPool(url, user, password, maxSize);
    }

    /** True if this is the process-wide singleton, false for a {@link #standalone} pool. */
    public boolean isSingleton() {
        return this == instance;
    }

    /** Closes every connection of THIS pool (a standalone pool closes itself this way). */
    public void closeAll() {
        synchronized (all) {
            all.forEach(ConnectionPool::closeQuietly);
            all.clear();
        }
    }

    public static ConnectionPool getInstance() {
        ConnectionPool p = instance;
        if (p == null) {
            throw new IllegalStateException("ConnectionPool.initialize(...) has not been called");
        }
        return p;
    }

    /** The database product's differences, chosen once from the URL (STRATEGY). */
    public SqlDialect dialect() {
        return dialect;
    }

    /** Borrows a connection, creating one if the pool is below its maximum size. */
    public Connection acquire() {
        try {
            Connection c = idle.poll();
            if (c == null && reserveSlot()) {
                try {
                    c = open();
                } catch (SQLException e) {
                    created.decrementAndGet(); // give the slot back so a later attempt can retry
                    throw e;
                }
            }
            if (c == null) {
                c = idle.poll(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (c == null) {
                    throw new DataAccessException("Timed out waiting for a database connection");
                }
            }
            if (!c.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                // Before: the broken connection was dropped without close() and its replacement was not tracked.
                forget(c);
                c = open();
            }
            c.setAutoCommit(true);
            return c;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database connection", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Interrupted while waiting for a connection", e);
        }
    }

    /** Atomically claims capacity for one new connection (before: check-then-increment could overshoot maxSize). */
    private boolean reserveSlot() {
        int n;
        do {
            n = created.get();
            if (n >= maxSize) {
                return false;
            }
        } while (!created.compareAndSet(n, n + 1));
        return true;
    }

    private Connection open() throws SQLException {
        Properties props = dialect.connectionProperties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        Connection c = DriverManager.getConnection(url, props);
        synchronized (all) {
            all.add(c);
        }
        return c;
    }

    private void forget(Connection c) {
        synchronized (all) {
            all.remove(c);
        }
        closeQuietly(c);
    }

    public void release(Connection c) {
        if (c != null && !idle.offer(c)) {
            closeQuietly(c);
        }
    }

    public int size() {
        return created.get();
    }

    /** Closes every connection and clears the singleton (used by tests and on shutdown). */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.closeAll();
            instance = null;
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
            // closing an already broken connection
        }
    }
}
