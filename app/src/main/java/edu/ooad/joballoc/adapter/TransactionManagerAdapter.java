package edu.ooad.joballoc.adapter;

import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.util.function.Supplier;

/**
 * ADAPTER (GoF structural, object adapter): makes the persistence tier's {@link TransactionManager}
 * (the adaptee) usable where the business tier expects its own {@link TransactionRunner} port (the target).
 *
 * <p>Why an adapter: the business tier must not import {@code persistence.orm}, and the persistence
 * tier must not import {@code business} (layers depend downward only). The adapter lives outside both
 * layers, next to the composition root, and is the only class that knows both sides
 * (Dependency Inversion). Before: the same adaptation was an anonymous class inside {@code App},
 * duplicated again in the unit test.
 */
public final class TransactionManagerAdapter implements TransactionRunner {

    private final TransactionManager tx;

    public TransactionManagerAdapter(TransactionManager tx) {
        this.tx = tx;
    }

    @Override
    public <R> R inTransaction(Supplier<R> work) {
        return tx.inTransaction(work);
    }
}
