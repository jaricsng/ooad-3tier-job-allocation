package edu.ooad.joballoc.storage;

import edu.ooad.joballoc.config.AppConfig;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;

/**
 * FACTORY METHOD for the storage family: {@code DB_URL} alone decides.
 * <ul>
 *   <li>{@code memory:} gives {@link InMemoryStorage};</li>
 *   <li>{@code jdbc:postgresql://...} or {@code jdbc:mysql://...} gives {@link RelationalStorage},
 *       whose connection pool then picks the SQL dialect (a second, finer choice).</li>
 * </ul>
 * Only the composition root ({@code App}) and tools call this; no tier knows which family it got.
 */
public final class StorageFactory {

    private StorageFactory() { }

    public static Storage create(AppConfig config) {
        if (InMemoryDAOFactory.URL.equals(config.dbUrl())) {
            return new InMemoryStorage();
        }
        return new RelationalStorage(config.dbUrl(), config.dbUser(), config.dbPassword(), config.dbPoolSize());
    }
}
