package edu.ooad.joballoc.it;

import edu.ooad.joballoc.domain.AuditEvent;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.JobStatus;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.domain.Worker;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.dao.DuplicateKeyException;
import edu.ooad.joballoc.persistence.dao.OptimisticLockException;
import edu.ooad.joballoc.persistence.orm.ConnectionPool;
import edu.ooad.joballoc.persistence.orm.Migrator;
import edu.ooad.joballoc.persistence.orm.Migrator.ScriptStatus;
import edu.ooad.joballoc.persistence.orm.TransactionManager;
import edu.ooad.joballoc.persistence.ormdao.OrmDAOFactory;
import edu.ooad.joballoc.storage.InMemoryStorage;
import edu.ooad.joballoc.storage.RelationalStorage;
import edu.ooad.joballoc.storage.StorageTransfer;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The ORM, DAOs and schema against a real database (PostgreSQL or MySQL, chosen by TEST_DB_URL). */
public class PersistenceIT {

    private static TransactionManager tx;
    private static DAOFactory daos;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 15);

    private static ConnectionPool pool;

    @BeforeClass
    public static void setUp() throws SQLException {
        Assume.assumeFalse("SQL-specific tests: skipped for TEST_DB_URL=memory:", TestDatabase.isMemory());
        TestDatabase.reset();
        pool = ConnectionPool.initialize(TestDatabase.url(), TestDatabase.user(),
                TestDatabase.password(), 4);
        assertEquals("every script applied", Migrator.SCRIPTS.size(), Migrator.migrate(pool));
        assertEquals("idempotent", 0, Migrator.migrate(pool));
        tx = new TransactionManager(pool);
        daos = new OrmDAOFactory(tx);
    }

    @AfterClass
    public static void tearDown() {
        ConnectionPool.shutdown();
    }

    @Test
    public void aJobThatRequiresNoSkillRoundTrips() {
        daos.jobDao().save(new Job("IT-0", "General task", Set.of(), 1, DAY, DAY, Priority.LOW));
        assertTrue(daos.jobDao().findById("IT-0").orElseThrow().getRequiredSkills().isEmpty());
    }

    @Test
    public void mapsAllColumnTypesAndCollectionTables() {
        Job j = new Job("IT-1", "Map me", Set.of("LINUX", "GO"), 3, DAY, DAY.plusDays(2), Priority.HIGH);
        daos.jobDao().save(j);
        Job loaded = daos.jobDao().findById("IT-1").orElseThrow();
        assertEquals(Set.of("LINUX", "GO"), loaded.getRequiredSkills());
        assertEquals(DAY, loaded.getWorkDate());
        assertEquals(Priority.HIGH, loaded.getPriority());
        assertEquals(JobStatus.PENDING, loaded.getStatus());
        assertEquals(0, loaded.getVersion());
        Worker w = daos.workerDao().findById("W-01").orElseThrow();
        assertEquals("seed data from V2", "Alice Tan", w.getName());
        assertTrue(w.getSkills().contains("GO"));
    }

    @Test
    public void optimisticLockDetectsLostUpdate() {
        daos.jobDao().save(new Job("IT-2", "Contended", Set.of("DBA"), 2, DAY, DAY, Priority.LOW));
        Job first = daos.jobDao().findById("IT-2").orElseThrow();
        Job second = daos.jobDao().findById("IT-2").orElseThrow();
        first.markAllocated();
        daos.jobDao().update(first);
        assertEquals(1, first.getVersion());
        second.markAllocated();
        assertThrows(OptimisticLockException.class, () -> daos.jobDao().update(second));
    }

    @Test
    public void identityMapReturnsSameObjectInsideATransaction() {
        tx.inTransaction(() -> {
            Worker a = daos.workerDao().findById("W-02").orElseThrow();
            Worker b = daos.workerDao().findAll().stream()
                    .filter(w -> w.getWorkerId().equals("W-02")).findFirst().orElseThrow();
            assertSame(a, b);
            return null;
        });
    }

    @Test
    public void transactionRollsBackAsAUnit() {
        assertThrows(IllegalStateException.class, () -> tx.inTransaction(() -> {
            daos.jobDao().save(new Job("IT-3", "Rolled back", Set.of("JAVA"), 1, DAY, DAY, Priority.LOW));
            throw new IllegalStateException("boom");
        }));
        assertTrue(daos.jobDao().findById("IT-3").isEmpty());
    }

    @Test
    public void findByIdsLoadsSeveralRowsInOneQuery() {
        LocalDate d = DAY.plusDays(20);
        daos.jobDao().save(new Job("IT-B1", "bulk 1", Set.of("LINUX"), 1, d, d, Priority.LOW));
        daos.jobDao().save(new Job("IT-B2", "bulk 2", Set.of("LINUX"), 1, d, d, Priority.HIGH));
        List<Job> found = daos.jobDao().findByIds(List.of("IT-B2", "IT-B1", "IT-MISSING", "IT-B1"));
        assertEquals("ordered by id, missing and duplicate ids ignored",
                List.of("IT-B1", "IT-B2"), found.stream().map(Job::getJobId).toList());
        assertTrue(daos.jobDao().findByIds(List.of()).isEmpty());
    }

    @Test
    public void findPendingFiltersAndOrdersByPriority() {
        LocalDate d = DAY.plusDays(10);
        daos.jobDao().save(new Job("IT-L", "low", Set.of("NETWORK"), 1, d, d, Priority.LOW));
        daos.jobDao().save(new Job("IT-C", "critical", Set.of("NETWORK", "SECURITY"), 1, d, d, Priority.CRITICAL));
        List<Job> all = daos.jobDao().findPending(new JobCriteria(d, null, null));
        assertEquals(List.of("IT-C", "IT-L"), all.stream().map(Job::getJobId).toList());
        List<Job> sec = daos.jobDao().findPending(new JobCriteria(d, null, "SECURITY"));
        assertEquals(List.of("IT-C"), sec.stream().map(Job::getJobId).toList());
        List<Job> injection = daos.jobDao().findPending(new JobCriteria(d, null, "x' or '1'='1"));
        assertTrue("parameterised SQL: injection text is just a value", injection.isEmpty());
    }

    @Test
    public void auditTrailIsAppendOnlyInTheDatabase() throws SQLException {
        daos.auditDao().append(new AuditEvent("TEST", "it", "IT-9", "detail", Instant.now()));
        assertEquals(1, daos.auditDao().findRecent(10).size());
        try (Connection c = DriverManager.getConnection(TestDatabase.url(), TestDatabase.user(),
                TestDatabase.password());
                Statement st = c.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () -> st.executeUpdate("delete from audit_event"));
            assertTrue(e.getMessage().contains("append-only"));
        }
    }

    @Test
    public void databaseAllowsOnlyOneActiveAllocationPerJob() throws SQLException {
        daos.jobDao().save(new Job("IT-U", "unique", Set.of("LINUX"), 1, DAY, DAY, Priority.LOW));
        try (Connection c = DriverManager.getConnection(TestDatabase.url(), TestDatabase.user(),
                TestDatabase.password());
                Statement st = c.createStatement()) {
            String insert = "insert into allocation (allocation_id, job_id, worker_id, work_date, hours, allocated_by,"
                    + " allocated_at, status, version) values ('%s','IT-U','W-01','2030-01-15',1,'it',"
                    + "current_timestamp,'ACTIVE',0)";
            st.executeUpdate(insert.formatted("a1"));
            assertThrows(SQLException.class, () -> st.executeUpdate(insert.formatted("a2")));
        }
        assertThrows(DuplicateKeyException.class,
                () -> daos.jobDao().save(new Job("IT-U", "duplicate id", Set.of("LINUX"), 1, DAY, DAY, Priority.LOW)));
    }

    private static Connection direct() throws SQLException {
        return DriverManager.getConnection(TestDatabase.url(), TestDatabase.user(), TestDatabase.password());
    }

    @Test
    public void migrationHistoryRecordsAChecksumForEveryScript() {
        assertTrue(Migrator.status(pool).stream().allMatch(st -> st.state() == ScriptStatus.State.APPLIED));
    }

    @Test
    public void anEditedAppliedScriptStopsTheMigration() throws SQLException {
        try (Connection c = direct(); Statement st = c.createStatement()) {
            String original;
            try (ResultSet rs = st.executeQuery("select checksum from schema_version where version = 1")) {
                rs.next();
                original = rs.getString(1);
            }
            st.executeUpdate("update schema_version set checksum = 'edited-after-apply' where version = 1");
            try {
                DataAccessException e = assertThrows(DataAccessException.class, () -> Migrator.migrate(pool));
                assertTrue(e.getMessage(), e.getMessage().contains("does not match"));
                assertEquals(ScriptStatus.State.CHANGED, Migrator.status(pool).get(0).state());
            } finally {
                st.executeUpdate("update schema_version set checksum = '" + original + "' where version = 1");
            }
        }
    }

    @Test
    public void aDatabaseNewerThanTheBuildStopsTheMigration() throws SQLException {
        try (Connection c = direct(); Statement st = c.createStatement()) {
            st.executeUpdate("insert into schema_version (version, script, checksum)"
                    + " values (99, 'V99__future.sql', 'x')");
            try {
                assertThrows(DataAccessException.class, () -> Migrator.migrate(pool));
                assertTrue(Migrator.status(pool).stream().anyMatch(s -> s.version() == 99
                        && s.state() == ScriptStatus.State.UNKNOWN));
            } finally {
                st.executeUpdate("delete from schema_version where version = 99");
            }
        }
        assertEquals("history is valid again", 0, Migrator.migrate(pool));
    }

    @Test
    public void everyRowCopiesToAnotherStorage() {
        daos.jobDao().save(new Job("IT-COPY", "copy me", Set.of("GO", "LINUX"), 2, DAY, DAY, Priority.HIGH));
        RelationalStorage source = new RelationalStorage(pool); // not closed: it shares the test's pool
        InMemoryStorage target = new InMemoryStorage();
        StorageTransfer.Report report = StorageTransfer.copy(source, target);
        int jobs = daos.jobDao().findAll().size();
        assertEquals(jobs, report.copied("job") + report.skipped("job"));
        assertEquals("reference data is already in the target", 0, report.copied("skill"));
        Job copied = target.daos().jobDao().findById("IT-COPY").orElseThrow();
        assertEquals("same skills in the same order", List.copyOf(daos.jobDao().findById("IT-COPY").orElseThrow()
                .getRequiredSkills()), List.copyOf(copied.getRequiredSkills()));
        assertFalse("a second copy finds everything present",
                StorageTransfer.copy(source, target).counts().values().stream().anyMatch(c -> c[0] > 0));
    }
}
