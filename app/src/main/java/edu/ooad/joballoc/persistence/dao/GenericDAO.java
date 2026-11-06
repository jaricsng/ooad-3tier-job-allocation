package edu.ooad.joballoc.persistence.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * DATA ACCESS OBJECT (PoEAA): the business tier sees only this interface, never SQL or JDBC.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public interface GenericDAO<T, ID> {
    Optional<T> findById(ID id);

    List<T> findAll();

    /**
     * Loads several rows at once; ids that do not exist are skipped. The default loads one by one;
     * the ORM overrides it with a single {@code where id = any(?)} query (avoids the N+1 query problem).
     */
    default List<T> findByIds(Collection<ID> ids) {
        return ids.stream().distinct().map(this::findById).flatMap(Optional::stream).toList();
    }

    void save(T entity);

    /** Updates the entity; versioned entities fail with OptimisticLockException on a stale version. */
    void update(T entity);

    void delete(ID id);
}
