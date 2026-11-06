package edu.ooad.joballoc.persistence.memory;

import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.AppUser;
import edu.ooad.joballoc.domain.AuditEvent;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.JobStatus;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.domain.Skill;
import edu.ooad.joballoc.domain.Worker;
import edu.ooad.joballoc.persistence.dao.AllocationDAO;
import edu.ooad.joballoc.persistence.dao.AuditDAO;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.dao.GenericDAO;
import edu.ooad.joballoc.persistence.dao.JobDAO;
import edu.ooad.joballoc.persistence.dao.OptimisticLockException;
import edu.ooad.joballoc.persistence.dao.SkillDAO;
import edu.ooad.joballoc.persistence.dao.UserDAO;
import edu.ooad.joballoc.persistence.dao.WorkerDAO;
import edu.ooad.joballoc.persistence.orm.EntityMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Concrete factory for in-memory DAOs (ABSTRACT FACTORY): the third storage option next to
 * PostgreSQL and MySQL ({@code DB_URL=memory:}), and the fast store for unit tests.
 *
 * <p>It honours the same contract as the relational DAOs:
 * <ul>
 *   <li>optimistic locking (versions are checked on update);</li>
 *   <li>transactions: {@link #inTransaction(Supplier)} holds one lock (serialisable isolation) and
 *       takes a MEMENTO of every store, restored if the work fails, so a failed allocation leaves
 *       nothing half-done;</li>
 *   <li>thread safety: each DAO handed out is a PROXY that holds the same reentrant lock per call,
 *       because the web server serves requests on many threads.</li>
 * </ul>
 * Data lives only as long as the process: a restart starts again from the reference data.
 */
public class InMemoryDAOFactory implements DAOFactory {

    /** Generic in-memory store that mimics versioned updates. */
    static class Store<T, ID> implements GenericDAO<T, ID> {
        Map<ID, T> rows = new LinkedHashMap<>();
        Map<ID, Long> versions = new LinkedHashMap<>();
        final EntityMetadata<T> meta;

        Store(Class<T> type) {
            this.meta = EntityMetadata.of(type);
        }

        @SuppressWarnings("unchecked")
        private ID id(T e) {
            return (ID) meta.idOf(e);
        }

        @Override public Optional<T> findById(ID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public List<T> findAll() { return new ArrayList<>(rows.values()); }

        @Override
        public void save(T entity) {
            rows.put(id(entity), entity);
            versions.put(id(entity), meta.versionOf(entity));
        }

        @Override
        public void update(T entity) {
            ID id = id(entity);
            Long stored = versions.get(id);
            long expected = meta.versionOf(entity);
            if (stored == null || (meta.isVersioned() && stored != expected)) {
                throw new OptimisticLockException(entity.getClass().getSimpleName() + " " + id + " is stale");
            }
            meta.setVersion(entity, expected + 1);
            versions.put(id, expected + 1);
            rows.put(id, entity);
        }

        @Override
        public void delete(ID id) {
            rows.remove(id);
            versions.remove(id);
        }

        /** MEMENTO: an independent copy of the rows and their versions. */
        StoreState<T, ID> snapshot() {
            Map<ID, T> copy = new LinkedHashMap<>();
            rows.forEach((id, row) -> copy.put(id, meta.copy(row)));
            return new StoreState<>(copy, new LinkedHashMap<>(versions));
        }

        void restore(StoreState<T, ID> state) {
            rows = state.rows();
            versions = state.versions();
        }

        /** Test helper: simulates another user updating the row (bumps the stored version). */
        public void simulateConcurrentUpdate(ID id) {
            versions.computeIfPresent(id, (k, v) -> v + 1);
        }
    }

    /** The saved state of one store. */
    record StoreState<T, ID>(Map<ID, T> rows, Map<ID, Long> versions) { }

    /** MEMENTO of the whole factory: every store and the audit log. */
    private record Snapshot(List<StoreState<?, ?>> stores, List<AuditEvent> audit) { }

    /** The {@code DB_URL} value that selects this store at run time. */
    public static final String URL = "memory:";

    /** Folder with the reference data (same rows as the SQL migrations' V2 seed). */
    private static final String SEED = "/db/memory/";
    private static final int SKILL_FIELDS = 2;
    private static final int WORKER_FIELDS = 4;
    private static final int USER_FIELDS = 5;

    final Store<Job, String> jobStore = new Store<>(Job.class);
    final Store<Worker, String> workerStore = new Store<>(Worker.class);
    final Store<Allocation, String> allocationStore = new Store<>(Allocation.class);
    final List<AuditEvent> auditLog = new ArrayList<>();
    final Store<Skill, String> skillStore = new Store<>(Skill.class);
    final Store<AppUser, String> userStore = new Store<>(AppUser.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    private final AllocationDAO allocations = guarded(AllocationDAO.class, new MemAllocationDAO());
    private final JobDAO jobs = guarded(JobDAO.class, new MemJobDAO());
    private final WorkerDAO workers = guarded(WorkerDAO.class, new MemWorkerDAO());
    private final AuditDAO audit = guarded(AuditDAO.class, new AuditDAO() {
        @Override public void append(AuditEvent e) { auditLog.add(e); }
        @Override public List<AuditEvent> findRecent(int limit) {
            List<AuditEvent> copy = new ArrayList<>(auditLog);
            copy.sort(Comparator.comparing(AuditEvent::getOccurredAt).reversed());
            return copy.subList(0, Math.min(limit, copy.size()));
        }

        @Override public List<AuditEvent> findAll() {
            List<AuditEvent> copy = new ArrayList<>(auditLog);
            copy.sort(Comparator.comparing(AuditEvent::getOccurredAt));
            return copy;
        }
    });
    private final SkillDAO skills = guarded(SkillDAO.class, new MemSkillDAO());
    private final UserDAO users = guarded(UserDAO.class, new MemUserDAO());

    @Override public JobDAO jobDao() { return jobs; }
    @Override public WorkerDAO workerDao() { return workers; }
    @Override public AllocationDAO allocationDao() { return allocations; }
    @Override public AuditDAO auditDao() { return audit; }
    @Override public SkillDAO skillDao() { return skills; }
    @Override public UserDAO userDao() { return users; }

    /**
     * Runs the work as one transaction: holds the lock (no other thread sees a half-done change)
     * and restores the snapshot if the work throws. Nested calls join the outer transaction.
     */
    public <R> R inTransaction(Supplier<R> work) {
        lock.lock();
        int level = depth.get();
        depth.set(level + 1);
        Snapshot before = level == 0 ? snapshot() : null;
        try {
            return work.get();
        } catch (RuntimeException | Error e) {
            if (before != null) {
                restore(before);
            }
            throw e;
        } finally {
            depth.set(level);
            lock.unlock();
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(List.of(jobStore.snapshot(), workerStore.snapshot(), allocationStore.snapshot(),
                skillStore.snapshot(), userStore.snapshot()), List.copyOf(auditLog));
    }

    @SuppressWarnings("unchecked")
    private void restore(Snapshot s) {
        List<Store<?, ?>> stores = List.of(jobStore, workerStore, allocationStore, skillStore, userStore);
        for (int i = 0; i < stores.size(); i++) {
            ((Store<Object, Object>) stores.get(i)).restore((StoreState<Object, Object>) s.stores().get(i));
        }
        auditLog.clear();
        auditLog.addAll(s.audit());
    }

    /**
     * PROXY: every call on the returned DAO holds the factory's lock, so the plain maps behind it are
     * safe to use from many threads. The lock is reentrant, so calls inside a transaction just join it.
     */
    private <D> D guarded(Class<D> type, D target) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            lock.lock();
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                lock.unlock();
            }
        });
        return type.cast(proxy);
    }

    /**
     * Loads the reference data (skills, workers and their skills, demo users) from
     * {@code db/memory/*.csv}: the same rows the SQL databases get from their V2 migration.
     */
    public InMemoryDAOFactory withReferenceData() {
        inTransaction(() -> {
            // Each loop reads the columns in file order (see the header line of each CSV file).
            for (Iterator<String> f : csv("skills.csv", SKILL_FIELDS)) {
                skills.save(new Skill(f.next(), f.next()));
            }
            for (Iterator<String> f : csv("workers.csv", WORKER_FIELDS)) {
                workers.save(new Worker(f.next(), f.next(), Integer.parseInt(f.next()),
                        new LinkedHashSet<>(List.of(f.next().split("\\|")))));
            }
            for (Iterator<String> f : csv("users.csv", USER_FIELDS)) {
                users.save(new AppUser(f.next(), f.next(), Role.valueOf(f.next()), f.next(), f.next()));
            }
            return null;
        });
        return this;
    }

    private static List<Iterator<String>> csv(String file, int fields) {
        try (InputStream in = InMemoryDAOFactory.class.getResourceAsStream(SEED + file)) {
            if (in == null) {
                throw new DataAccessException("Missing resource " + SEED + file);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<Iterator<String>> rows = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    String[] f = line.split(",", -1);
                    if (f.length != fields) {
                        throw new DataAccessException(SEED + file + ": expected " + fields + " fields in: " + line);
                    }
                    rows.add(Arrays.asList(f).iterator());
                }
            }
            return rows;
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + SEED + file, e);
        }
    }

    /** Test helper: another user changed this job. */
    public void simulateConcurrentJobUpdate(String jobId) {
        jobStore.simulateConcurrentUpdate(jobId);
    }

    /** Test helper: all audit events in insertion order. */
    public List<AuditEvent> auditLog() {
        return List.copyOf(auditLog);
    }

    private final class MemJobDAO extends AbstractDelegate<Job, String> implements JobDAO {
        MemJobDAO() { super(jobStore); }

        @Override
        public List<Job> findPending(JobCriteria c) {
            return jobStore.rows.values().stream()
                    .filter(j -> j.getStatus() == JobStatus.PENDING && j.getWorkDate().equals(c.date()))
                    .filter(j -> c.priority() == null || j.getPriority() == c.priority())
                    .filter(j -> c.skill() == null || j.getRequiredSkills().contains(c.skill()))
                    .sorted(Comparator.comparing(Job::getPriority).reversed()
                            .thenComparing(Job::getDueDate).thenComparing(Job::getJobId))
                    .toList();
        }
    }

    private final class MemWorkerDAO extends AbstractDelegate<Worker, String> implements WorkerDAO {
        MemWorkerDAO() { super(workerStore); }

        @Override
        public Optional<Worker> findWithAllocations(String workerId, LocalDate on) {
            Optional<Worker> w = findById(workerId);
            w.ifPresent(x -> x.attachAllocations(allocations.findActiveByWorker(workerId, on)));
            return w;
        }

        @Override
        public List<Worker> findAllWithAllocations(LocalDate on) {
            List<Worker> all = findAll();
            all.forEach(x -> x.attachAllocations(allocations.findActiveByWorker(x.getWorkerId(), on)));
            return all;
        }
    }

    private final class MemAllocationDAO extends AbstractDelegate<Allocation, String> implements AllocationDAO {
        MemAllocationDAO() { super(allocationStore); }

        @Override
        public List<Allocation> findActiveByWorker(String workerId, LocalDate on) {
            return allocationStore.rows.values().stream()
                    .filter(a -> a.isActive() && a.getWorkerId().equals(workerId) && a.getWorkDate().equals(on))
                    .toList();
        }

        @Override
        public List<Allocation> findActiveByDate(LocalDate on) {
            return allocationStore.rows.values().stream()
                    .filter(a -> a.isActive() && a.getWorkDate().equals(on)).toList();
        }

        @Override
        public Optional<Allocation> findActiveByJob(String jobId) {
            return allocationStore.rows.values().stream()
                    .filter(a -> a.isActive() && a.getJobId().equals(jobId)).findFirst();
        }
    }

    private final class MemSkillDAO extends AbstractDelegate<Skill, String> implements SkillDAO {
        MemSkillDAO() { super(skillStore); }
    }

    private final class MemUserDAO extends AbstractDelegate<AppUser, String> implements UserDAO {
        MemUserDAO() { super(userStore); }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            String key = email == null ? "" : email.toLowerCase(java.util.Locale.ROOT);
            return userStore.rows.values().stream().filter(u -> key.equals(u.getEmail())).findFirst();
        }
    }

    /** Forwards the generic operations to a store. */
    private abstract static class AbstractDelegate<T, ID> implements GenericDAO<T, ID> {
        private final Store<T, ID> store;

        AbstractDelegate(Store<T, ID> store) { this.store = store; }

        @Override public Optional<T> findById(ID id) { return store.findById(id); }
        @Override public List<T> findAll() { return store.findAll(); }
        @Override public void save(T e) { store.save(e); }
        @Override public void update(T e) { store.update(e); }
        @Override public void delete(ID id) { store.delete(id); }
    }
}
