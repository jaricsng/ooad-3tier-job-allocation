package edu.ooad.joballoc.storage;

import edu.ooad.joballoc.adapter.JdbcDatabaseHealthAdapter;
import edu.ooad.joballoc.adapter.TransactionManagerAdapter;
import edu.ooad.joballoc.business.port.DatabaseHealth;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.orm.ConnectionPool;
import edu.ooad.joballoc.persistence.orm.Migrator;
import edu.ooad.joballoc.persistence.orm.TransactionManager;
import edu.ooad.joballoc.persistence.ormdao.OrmDAOFactory;

/**
 * Storage family for a relational database (PostgreSQL or MySQL): connection pool, schema migrations,
 * ORM DAOs, JDBC transactions and a JDBC health check. The product-specific SQL comes from the
 * {@code SqlDialect} that the pool picked from the URL.
 */
public final class RelationalStorage implements Storage {

    private final ConnectionPool pool;
    private final TransactionManager tx;
    private final DAOFactory daos;

    /** Connects with the application's single pool, applies pending migrations and builds the DAOs. */
    public RelationalStorage(String url, String user, String password, int poolSize) {
        this(ConnectionPool.initialize(url, user, password, poolSize));
    }

    /** Uses the given pool (the singleton, or a standalone pool for tools such as the data copy). */
    public RelationalStorage(ConnectionPool pool) {
        this.pool = pool;
        Migrator.migrate(pool);
        this.tx = new TransactionManager(pool);
        this.daos = new OrmDAOFactory(tx);
    }

    @Override
    public String description() {
        return pool.dialect().productName();
    }

    @Override
    public DAOFactory daos() {
        return daos;
    }

    @Override
    public TransactionRunner transactions() {
        return new TransactionManagerAdapter(tx);
    }

    @Override
    public DatabaseHealth health() {
        return new JdbcDatabaseHealthAdapter(tx);
    }

    /** The pool, for migration status and other tooling. */
    public ConnectionPool pool() {
        return pool;
    }

    @Override
    public void close() {
        if (pool.isSingleton()) {
            ConnectionPool.shutdown();
        } else {
            pool.closeAll();
        }
    }
}
