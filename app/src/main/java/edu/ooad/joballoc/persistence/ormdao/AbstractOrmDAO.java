package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.dao.DuplicateKeyException;
import edu.ooad.joballoc.persistence.dao.GenericDAO;
import edu.ooad.joballoc.persistence.dao.OptimisticLockException;
import edu.ooad.joballoc.persistence.dialect.SqlDialect;
import edu.ooad.joballoc.persistence.orm.EntityMetadata;
import edu.ooad.joballoc.persistence.orm.EntityMetadata.CollectionMapping;
import edu.ooad.joballoc.persistence.orm.EntityMetadata.FieldMapping;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TEMPLATE METHOD (GoF behavioural) for every ORM-backed DAO.
 *
 * <p>{@link #query(String, Object...)} fixes the algorithm (get connection, prepare, bind
 * parameters, execute, map rows, consult the identity map, load collections, translate errors).
 * Subclasses only add finder methods and may override the {@link #afterLoad(Object)} hook.
 *
 * <p>All SQL is parameterised and portable; the few product differences come from the
 * {@link SqlDialect} STRATEGY. Values are never concatenated into SQL strings.
 */
public abstract class AbstractOrmDAO<T, ID> implements GenericDAO<T, ID> {

    /** Largest IN list sent in one statement (Oracle's limit is 1000; stay well below every product's). */
    private static final int MAX_IN_LIST = 500;

    /** Parameter positions in {@link CollectionMapping#insertSql()}. */
    private static final int OWNER_PARAM = 1;
    private static final int VALUE_PARAM = 2;
    private static final int ORDINAL_PARAM = 3;

    protected final TransactionManager tx;
    protected final EntityMetadata<T> meta;
    protected final SqlDialect dialect;

    protected AbstractOrmDAO(TransactionManager tx, Class<T> type) {
        this.tx = tx;
        this.meta = EntityMetadata.of(type);
        this.dialect = tx.dialect();
    }

    @Override
    public Optional<T> findById(ID id) {
        TransactionManager.TxContext ctx = tx.context();
        if (ctx != null) {
            T cached = ctx.cached(meta.type(), id);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        List<T> rows = query("where " + meta.id().column() + " = ?", id);
        return rows.stream().findFirst();
    }

    @Override
    public List<T> findAll() {
        return query("order by " + meta.id().column());
    }

    @Override
    public List<T> findByIds(Collection<ID> ids) {
        List<ID> distinct = ids.stream().distinct().toList();
        List<T> result = new ArrayList<>();
        for (List<ID> chunk : chunks(distinct)) {
            result.addAll(query("where " + meta.id().column() + " in (" + EntityMetadata.placeholders(chunk.size())
                    + ") order by " + meta.id().column(), chunk.toArray()));
        }
        return result;
    }

    @Override
    public void save(T entity) {
        List<FieldMapping> cols = meta.columns();
        withStatement(meta.insertSql(), (con, ps) -> {
            for (int i = 0; i < cols.size(); i++) {
                meta.bind(ps, i + 1, entity, cols.get(i));
            }
            int rows = ps.executeUpdate();
            insertCollections(con, entity);
            return rows;
        });
        cache(entity);
    }

    @Override
    public void update(T entity) {
        long expectedVersion = meta.versionOf(entity);
        int rows = withStatement(meta.updateSql(), (con, ps) -> {
            int i = 1;
            for (FieldMapping c : meta.updatableColumns()) {
                meta.bind(ps, i++, entity, c);
            }
            meta.bind(ps, i++, entity, meta.id());
            if (meta.isVersioned()) {
                ps.setLong(i, expectedVersion);
            }
            int updated = ps.executeUpdate();
            if (updated > 0) {
                deleteCollections(con, meta.idOf(entity));
                insertCollections(con, entity);
            }
            return updated;
        });
        if (rows == 0) {
            throw new OptimisticLockException(meta.type().getSimpleName() + " " + meta.idOf(entity)
                    + " was changed or removed by someone else (expected version " + expectedVersion + ")");
        }
        meta.setVersion(entity, expectedVersion + 1);
    }

    @Override
    public void delete(ID id) {
        withStatement("delete from " + meta.table() + " where " + meta.id().column() + " = ?", (con, ps) -> {
            deleteCollections(con, id);
            EntityMetadata.bindValue(ps, 1, id, id.getClass());
            return ps.executeUpdate();
        });
    }

    /** The template method: subclasses pass only the WHERE/ORDER BY clause and its parameters. */
    protected final List<T> query(String clause, Object... params) {
        List<T> fresh = new ArrayList<>();
        List<T> result = withStatement(meta.selectSql(clause), (con, ps) -> {
            bindAll(ps, params);
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    T loaded = meta.fromRow(rs);
                    T mapped = identityMapped(loaded);
                    if (mapped == loaded) {
                        fresh.add(loaded);
                    }
                    rows.add(mapped);
                }
            }
            loadCollections(con, fresh);
            return rows;
        });
        fresh.forEach(this::afterLoad);
        return result;
    }

    /** Executes an INSERT/UPDATE/DELETE and returns the affected row count. */
    protected final int execute(String sql, Object... params) {
        return withStatement(sql, (con, ps) -> {
            bindAll(ps, params);
            return ps.executeUpdate();
        });
    }

    /** The JDBC steps every operation needs; only the middle step varies. */
    @FunctionalInterface
    private interface StatementWork<R> {
        R run(Connection con, PreparedStatement ps) throws SQLException;
    }

    /**
     * The one place that borrows the connection, prepares the statement, closes it and translates
     * {@link SQLException} (DRY: before, save/update/query/execute each repeated this block).
     */
    private <R> R withStatement(String sql, StatementWork<R> work) {
        return tx.withConnection(con -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                return work.run(con, ps);
            } catch (SQLException e) {
                throw translate(sql, e);
            }
        });
    }

    /** Hook for subclasses, called after an entity is loaded from the database (default: nothing). */
    protected void afterLoad(T entity) {
        // hook
    }

    /**
     * Fills every {@code @CollectionTable} field of the freshly loaded entities with ONE query per
     * collection (not one per entity): avoids the N+1 query problem.
     */
    private void loadCollections(Connection con, List<T> entities) throws SQLException {
        if (entities.isEmpty()) {
            return;
        }
        for (CollectionMapping c : meta.collections()) {
            Map<Object, List<String>> valuesByOwner = new HashMap<>();
            List<Object> ids = entities.stream().map(meta::idOf).toList();
            for (List<Object> chunk : chunks(ids)) {
                try (PreparedStatement ps = con.prepareStatement(c.selectSql(chunk.size()))) {
                    bindAll(ps, chunk.toArray());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            valuesByOwner.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                        }
                    }
                }
            }
            for (T entity : entities) {
                meta.setCollection(entity, c, valuesByOwner.getOrDefault(String.valueOf(meta.idOf(entity)), List.of()));
            }
        }
    }

    private void insertCollections(Connection con, T entity) throws SQLException {
        Object id = meta.idOf(entity);
        for (CollectionMapping c : meta.collections()) {
            try (PreparedStatement ps = con.prepareStatement(c.insertSql())) {
                int ordinal = 0;
                for (String value : meta.collectionOf(entity, c)) {
                    EntityMetadata.bindValue(ps, OWNER_PARAM, id, id.getClass());
                    ps.setString(VALUE_PARAM, value);
                    ps.setInt(ORDINAL_PARAM, ordinal++);
                    ps.addBatch();
                }
                if (ordinal > 0) {
                    ps.executeBatch();
                }
            }
        }
    }

    private void deleteCollections(Connection con, Object id) throws SQLException {
        for (CollectionMapping c : meta.collections()) {
            try (PreparedStatement ps = con.prepareStatement(c.deleteSql())) {
                EntityMetadata.bindValue(ps, 1, id, id.getClass());
                ps.executeUpdate();
            }
        }
    }

    private static <E> List<List<E>> chunks(List<E> values) {
        List<List<E>> result = new ArrayList<>();
        for (int from = 0; from < values.size(); from += MAX_IN_LIST) {
            result.add(values.subList(from, Math.min(values.size(), from + MAX_IN_LIST)));
        }
        return result;
    }

    private static void bindAll(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            EntityMetadata.bindValue(ps, i + 1, p, p == null ? Object.class : p.getClass());
        }
    }

    /** IDENTITY MAP: within a transaction, one row is always the same object. */
    private T identityMapped(T loaded) {
        TransactionManager.TxContext ctx = tx.context();
        if (ctx == null) {
            return loaded;
        }
        Object id = meta.idOf(loaded);
        T existing = ctx.cached(meta.type(), id);
        if (existing != null) {
            return existing;
        }
        ctx.cache(meta.type(), id, loaded);
        return loaded;
    }

    private void cache(T entity) {
        TransactionManager.TxContext ctx = tx.context();
        if (ctx != null) {
            ctx.cache(meta.type(), meta.idOf(entity), entity);
        }
    }

    /**
     * EXCEPTION TRANSLATION: vendor SQLException -> the DAO contract's DataAccessException hierarchy.
     * The dialect knows each product's code for "duplicate key"; nothing above this tier does.
     */
    private DataAccessException translate(String sql, SQLException e) {
        String verb = sql.strip().split("\\s+", 2)[0];
        String message = "Database error during " + verb + ": " + e.getMessage();
        return dialect.isUniqueViolation(e)
                ? new DuplicateKeyException(message, e)
                : new DataAccessException(message, e);
    }
}
