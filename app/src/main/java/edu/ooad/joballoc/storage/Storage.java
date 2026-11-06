package edu.ooad.joballoc.storage;

import edu.ooad.joballoc.business.port.DatabaseHealth;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.persistence.dao.DAOFactory;

/**
 * ABSTRACT FACTORY one level above {@link DAOFactory}: one storage option supplies a whole family
 * of products that must match each other: the DAOs, the transactions that span them and the health
 * check. Mixing families (MySQL DAOs with in-memory transactions) would silently break atomicity,
 * which is exactly what an Abstract Factory prevents.
 *
 * <p>Families: {@link RelationalStorage} (PostgreSQL or MySQL; the SQL dialect is a STRATEGY inside it)
 * and {@link InMemoryStorage} (no database). {@link StorageFactory} picks one from {@code DB_URL}.
 */
public interface Storage extends AutoCloseable {

    /** For logs and /health, for example "PostgreSQL" or "in-memory (data is lost on restart)". */
    String description();

    DAOFactory daos();

    TransactionRunner transactions();

    DatabaseHealth health();

    /** Releases connections or memory; safe to call twice. */
    @Override
    void close();
}
