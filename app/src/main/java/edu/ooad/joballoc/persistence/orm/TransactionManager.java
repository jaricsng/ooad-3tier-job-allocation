package edu.ooad.joballoc.persistence.orm;

import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.dialect.SqlDialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Transaction boundary plus a per-transaction IDENTITY MAP (Fowler, PoEAA).
 *
 * <p>Inside {@link #inTransaction(Supplier)} every DAO call on the same thread shares one JDBC
 * connection, so all changes commit or roll back together (the "unit of work" of a use case).
 * The identity map guarantees one in-memory object per database row within a transaction, so a
 * Worker loaded twice is the same object and sees its own new allocations.
 */
public class TransactionManager {

    private final ConnectionPool pool;
    private final ThreadLocal<TxContext> current = new ThreadLocal<>();

    /** State of the transaction bound to the current thread. */
    public static final class TxContext {
        private final Connection connection;
        private final Map<String, Object> identityMap = new HashMap<>();

        TxContext(Connection connection) {
            this.connection = connection;
        }

        public Connection connection() { return connection; }

        @SuppressWarnings("unchecked")
        public <T> T cached(Class<T> type, Object id) {
            return (T) identityMap.get(type.getName() + "#" + id);
        }

        public void cache(Class<?> type, Object id, Object entity) {
            identityMap.put(type.getName() + "#" + id, entity);
        }
    }

    public TransactionManager(ConnectionPool pool) {
        this.pool = pool;
    }

    /** The dialect of the pool's database; DAOs use it for the few product-specific bits of SQL. */
    public SqlDialect dialect() {
        return pool.dialect();
    }

    public boolean isActive() {
        return current.get() != null;
    }

    public TxContext context() {
        return current.get();
    }

    /** Runs work in a transaction; joins the caller's transaction if one is already active. */
    public <R> R inTransaction(Supplier<R> work) {
        if (isActive()) {
            return work.get();
        }
        Connection con = pool.acquire();
        current.set(new TxContext(con));
        try {
            con.setAutoCommit(false);
            R result = work.get();
            con.commit();
            return result;
        } catch (RuntimeException | Error e) {
            rollbackQuietly(con);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(con);
            throw new DataAccessException("Commit failed", e);
        } finally {
            current.remove();
            try {
                con.setAutoCommit(true);
            } catch (SQLException ignored) {
                // connection will be validated on next acquire
            }
            pool.release(con);
        }
    }

    /** Uses the transaction's connection if active, otherwise borrows one for this call only. */
    public <R> R withConnection(Function<Connection, R> work) {
        TxContext ctx = current.get();
        if (ctx != null) {
            return work.apply(ctx.connection());
        }
        Connection con = pool.acquire();
        try {
            return work.apply(con);
        } finally {
            pool.release(con);
        }
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
            // original exception is more useful
        }
    }
}
