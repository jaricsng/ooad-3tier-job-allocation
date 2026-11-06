package edu.ooad.joballoc.adapter;

import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;

import java.util.function.Supplier;

/**
 * ADAPTER: makes the in-memory store's transaction (lock + MEMENTO rollback) usable through the
 * business tier's {@link TransactionRunner} port. The twin of {@link TransactionManagerAdapter} for
 * the relational databases: the business tier cannot tell which one it got.
 */
public final class InMemoryTransactionAdapter implements TransactionRunner {

    private final InMemoryDAOFactory store;

    public InMemoryTransactionAdapter(InMemoryDAOFactory store) {
        this.store = store;
    }

    @Override
    public <R> R inTransaction(Supplier<R> work) {
        return store.inTransaction(work);
    }
}
