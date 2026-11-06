package edu.ooad.joballoc.business.port;

import java.util.function.Supplier;

/**
 * PORT owned by the business tier: "run this unit of work atomically". The business tier depends on
 * this interface only; the persistence tier's TransactionManager is plugged in through an ADAPTER
 * ({@code edu.ooad.joballoc.adapter.TransactionManagerAdapter}) by the composition root.
 */
public interface TransactionRunner {

    /** For unit tests with in-memory DAOs: runs the work directly. */
    TransactionRunner NONE = new TransactionRunner() {
        @Override
        public <R> R inTransaction(Supplier<R> work) {
            return work.get();
        }
    };

    <R> R inTransaction(Supplier<R> work);
}
