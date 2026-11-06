package edu.ooad.joballoc.storage;

import edu.ooad.joballoc.adapter.InMemoryTransactionAdapter;
import edu.ooad.joballoc.business.port.DatabaseHealth;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;

/**
 * Storage family without a database ({@code DB_URL=memory:}): in-memory DAOs, lock-and-snapshot
 * transactions, and a health check that is always up. Starts from the same reference data as the
 * SQL databases. Good for a classroom demo with no Docker, for exploratory testing and for fast
 * end-to-end runs; never for real data (policy P8 forbids it in production).
 */
public final class InMemoryStorage implements Storage {

    private final InMemoryDAOFactory store = new InMemoryDAOFactory().withReferenceData();

    @Override
    public String description() {
        return "in-memory (data is lost on restart)";
    }

    @Override
    public DAOFactory daos() {
        return store;
    }

    @Override
    public TransactionRunner transactions() {
        return new InMemoryTransactionAdapter(store);
    }

    @Override
    public DatabaseHealth health() {
        return () -> true;
    }

    @Override
    public void close() {
        // nothing to release: the data goes with the object
    }
}
