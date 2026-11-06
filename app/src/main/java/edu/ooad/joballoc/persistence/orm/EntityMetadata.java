package edu.ooad.joballoc.persistence.orm;

import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.mapping.CollectionTable;
import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;
import edu.ooad.joballoc.persistence.mapping.Transient;
import edu.ooad.joballoc.persistence.mapping.Version;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Reflection-based mapping between an {@code @Entity} class and its table: the "metadata mapping"
 * pattern (Fowler, PoEAA) that real ORMs such as Hibernate are built on.
 *
 * <p>Supported field types: String, int/Integer, long/Long, boolean/Boolean, LocalDate, Instant,
 * and enums (stored as text). A {@code Set<String>} annotated {@link CollectionTable} lives in its own
 * table (one row per value), which every relational database supports.
 */
public final class EntityMetadata<T> {

    private static final Map<Class<?>, EntityMetadata<?>> CACHE = new ConcurrentHashMap<>();

    /**
     * Java type -> how to read it (table-driven; before: an 8-branch if/else chain).
     * Boxed types return null for SQL NULL; primitives keep the JDBC default.
     */
    private static final Map<Class<?>, ColumnReader> READERS = Map.ofEntries(
            Map.entry(String.class, ResultSet::getString),
            Map.entry(int.class, ResultSet::getInt),
            Map.entry(Integer.class, (rs, c) -> nullable(rs, rs.getInt(c))),
            Map.entry(long.class, ResultSet::getLong),
            Map.entry(Long.class, (rs, c) -> nullable(rs, rs.getLong(c))),
            Map.entry(boolean.class, ResultSet::getBoolean),
            Map.entry(Boolean.class, (rs, c) -> nullable(rs, rs.getBoolean(c))),
            Map.entry(LocalDate.class, (rs, c) -> rs.getObject(c, LocalDate.class)),
            Map.entry(Instant.class, (rs, c) -> {
                Timestamp ts = rs.getTimestamp(c);
                return ts == null ? null : ts.toInstant();
            }));

    private final Class<T> type;
    private final String table;
    private final Constructor<T> constructor;
    private final List<FieldMapping> columns;
    private final FieldMapping id;
    private final FieldMapping version;
    private final List<FieldMapping> updatable;
    private final List<CollectionMapping> collections;
    private final String selectSql;
    private final String insertSql;
    private final String updateSql;

    /** A single persistent field and the column it maps to. */
    public record FieldMapping(Field field, String column) { }

    /** A {@code Set<String>} field stored in its own table: one row per value, in insertion order. */
    public record CollectionMapping(Field field, String table, String joinColumn, String valueColumn) {

        /** All values of the given owners, oldest first; the owner id is column 1, the value column 2. */
        public String selectSql(int ownerCount) {
            return "select " + joinColumn + ", " + valueColumn + " from " + table + " where " + joinColumn
                    + " in (" + placeholders(ownerCount) + ") order by " + joinColumn + ", ordinal";
        }

        /** Parameters: owner id, value, ordinal. */
        public String insertSql() {
            return "insert into " + table + " (" + joinColumn + ", " + valueColumn + ", ordinal) values (?, ?, ?)";
        }

        /** Parameter: owner id. */
        public String deleteSql() {
            return "delete from " + table + " where " + joinColumn + " = ?";
        }
    }

    private EntityMetadata(Class<T> type) {
        if (!type.isAnnotationPresent(Entity.class)) {
            throw new DataAccessException(type.getName() + " is not annotated with @Entity");
        }
        this.type = type;
        Table t = type.getAnnotation(Table.class);
        this.table = t != null ? t.value() : type.getSimpleName().toLowerCase();
        try {
            this.constructor = type.getDeclaredConstructor();
            this.constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new DataAccessException(type.getName() + " needs a no-argument constructor", e);
        }
        List<FieldMapping> cols = scanColumns(type);
        FieldMapping idField = find(cols, Id.class);
        FieldMapping versionField = find(cols, Version.class);
        if (idField == null) {
            throw new DataAccessException(type.getName() + " has no @Id field");
        }
        this.columns = Collections.unmodifiableList(cols);
        this.collections = scanCollections(type);
        this.id = idField;
        this.version = versionField;
        // SQL is generated once per entity type (METADATA MAPPING), not on every call.
        FieldMapping idCol = idField;
        FieldMapping versionCol = versionField;
        this.updatable = cols.stream().filter(c -> c != idCol && c != versionCol).toList();
        String columnList = columnList();
        this.selectSql = "select " + columnList + " from " + table + " ";
        this.insertSql = "insert into " + table + " (" + columnList + ") values ("
                + cols.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";
        StringBuilder update = new StringBuilder("update ").append(table).append(" set ")
                .append(updatable.stream().map(c -> c.column() + " = ?").collect(Collectors.joining(", ")));
        if (versionCol != null) {
            update.append(", ").append(versionCol.column()).append(" = ").append(versionCol.column()).append(" + 1");
        }
        update.append(" where ").append(idCol.column()).append(" = ?");
        if (versionCol != null) {
            update.append(" and ").append(versionCol.column()).append(" = ?");
        }
        this.updateSql = update.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T> EntityMetadata<T> of(Class<T> type) {
        return (EntityMetadata<T>) CACHE.computeIfAbsent(type, EntityMetadata::new);
    }

    /** Persistent fields: annotated with @Column, not static, not @Transient. */
    private static List<FieldMapping> scanColumns(Class<?> type) {
        List<FieldMapping> cols = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c == null || Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Transient.class)) {
                continue;
            }
            f.setAccessible(true);
            cols.add(new FieldMapping(f, c.value()));
        }
        return cols;
    }

    /** {@code @CollectionTable} fields: Set of String only, kept out of the entity's own table. */
    private static List<CollectionMapping> scanCollections(Class<?> type) {
        List<CollectionMapping> result = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            CollectionTable ct = f.getAnnotation(CollectionTable.class);
            if (ct == null) {
                continue;
            }
            if (!Set.class.isAssignableFrom(f.getType())) {
                throw new DataAccessException(
                        type.getName() + "." + f.getName() + ": @CollectionTable needs a Set<String>");
            }
            f.setAccessible(true);
            result.add(new CollectionMapping(f, ct.table(), ct.joinColumn(), ct.valueColumn()));
        }
        return Collections.unmodifiableList(result);
    }

    /** "?, ?, ?" for an IN list of the given size (at least one). */
    public static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(Math.max(1, count), "?"));
    }

    private static FieldMapping find(List<FieldMapping> cols, Class<? extends java.lang.annotation.Annotation> marker) {
        return cols.stream().filter(m -> m.field().isAnnotationPresent(marker)).findFirst().orElse(null);
    }

    /** {@code select <all columns> from <table> } followed by the caller's WHERE/ORDER BY clause. */
    public String selectSql(String clause) {
        return selectSql + clause;
    }

    /** Parameter order: {@link #columns()}. */
    public String insertSql() { return insertSql; }

    /** Parameter order: {@link #updatableColumns()}, then the id, then the expected version (if versioned). */
    public String updateSql() { return updateSql; }

    /** Columns written by UPDATE: all except the id and the version. */
    public List<FieldMapping> updatableColumns() { return updatable; }

    public Class<T> type() { return type; }
    public String table() { return table; }
    public List<FieldMapping> columns() { return columns; }
    public FieldMapping id() { return id; }
    public FieldMapping version() { return version; }
    public boolean isVersioned() { return version != null; }
    public List<CollectionMapping> collections() { return collections; }

    public String columnList() {
        return columns.stream().map(FieldMapping::column).collect(Collectors.joining(", "));
    }

    public Object idOf(T entity) {
        return get(entity, id);
    }

    public long versionOf(T entity) {
        return version == null ? 0L : ((Number) get(entity, version)).longValue();
    }

    /** The current values of a collection field (never null). */
    @SuppressWarnings("unchecked")
    public Set<String> collectionOf(T entity, CollectionMapping c) {
        try {
            Object value = c.field().get(entity);
            return value == null ? Set.of() : (Set<String>) value;
        } catch (IllegalAccessException e) {
            throw new DataAccessException("Cannot read " + c.field().getName(), e);
        }
    }

    /** Replaces a collection field with the values loaded from its table. */
    public void setCollection(T entity, CollectionMapping c, List<String> values) {
        try {
            c.field().set(entity, new LinkedHashSet<>(values));
        } catch (IllegalAccessException e) {
            throw new DataAccessException("Cannot write " + c.field().getName(), e);
        }
    }

    public void setVersion(T entity, long value) {
        if (version != null) {
            set(entity, version, version.field().getType() == Long.class ? Long.valueOf(value) : value);
        }
    }

    /**
     * A copy holding the same persistent state: every mapped column and collection (collections are
     * copied, not shared). Transient fields are not copied; DAOs re-attach them on load. Used by the
     * in-memory store to take snapshots for rollback (MEMENTO).
     */
    public T copy(T entity) {
        try {
            T copy = constructor.newInstance();
            for (FieldMapping m : columns) {
                set(copy, m, get(entity, m));
            }
            for (CollectionMapping c : collections) {
                setCollection(copy, c, List.copyOf(collectionOf(entity, c)));
            }
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException("Cannot copy " + type.getName(), e);
        }
    }

    /** Builds an entity instance from the current row of a result set. */
    public T fromRow(ResultSet rs) throws SQLException {
        try {
            T entity = constructor.newInstance();
            for (FieldMapping m : columns) {
                set(entity, m, readColumn(rs, m));
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException("Cannot instantiate " + type.getName(), e);
        }
    }

    /** Binds a field value as a JDBC parameter (always parameterised: no SQL string building). */
    public void bind(PreparedStatement ps, int index, T entity, FieldMapping m) throws SQLException {
        bindValue(ps, index, get(entity, m), m.field().getType());
    }

    public static void bindValue(PreparedStatement ps, int index, Object value, Class<?> type)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, sqlTypeOf(type));
        } else if (value instanceof Enum<?> e) {
            ps.setString(index, e.name());
        } else if (value instanceof LocalDate d) {
            ps.setObject(index, d);
        } else if (value instanceof Instant i) {
            ps.setTimestamp(index, Timestamp.from(i));
        } else {
            ps.setObject(index, value);
        }
    }

    /** JDBC type for a NULL of the given Java type; strict drivers (MySQL) reject Types.OTHER. */
    private static int sqlTypeOf(Class<?> type) {
        if (type == Integer.class || type == int.class) {
            return Types.INTEGER;
        }
        if (type == Long.class || type == long.class) {
            return Types.BIGINT;
        }
        if (type == Boolean.class || type == boolean.class) {
            return Types.BOOLEAN;
        }
        if (type == LocalDate.class) {
            return Types.DATE;
        }
        if (type == Instant.class) {
            return Types.TIMESTAMP;
        }
        return Types.VARCHAR;
    }

    /** Reads one column as a given Java type. */
    @FunctionalInterface
    private interface ColumnReader {
        Object read(ResultSet rs, String column) throws SQLException;
    }

    private static Object nullable(ResultSet rs, Object value) throws SQLException {
        return rs.wasNull() ? null : value;
    }

    private Object readColumn(ResultSet rs, FieldMapping m) throws SQLException {
        Class<?> t = m.field().getType();
        String col = m.column();
        ColumnReader reader = READERS.get(t);
        if (reader != null) {
            return reader.read(rs, col);
        }
        if (t.isEnum()) {
            String s = rs.getString(col);
            return s == null ? null : enumValue(t, s);
        }
        throw new DataAccessException("Unsupported field type " + t.getName() + " for column " + col);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> t, String s) {
        return Enum.valueOf((Class<? extends Enum>) t, s);
    }

    private Object get(T entity, FieldMapping m) {
        try {
            return m.field().get(entity);
        } catch (IllegalAccessException e) {
            throw new DataAccessException("Cannot read " + m.field().getName(), e);
        }
    }

    private void set(T entity, FieldMapping m, Object value) {
        try {
            m.field().set(entity, value);
        } catch (IllegalAccessException e) {
            throw new DataAccessException("Cannot write " + m.field().getName(), e);
        }
    }
}
