package edu.ooad.joballoc.storage;

import edu.ooad.joballoc.TestData;
import edu.ooad.joballoc.config.AppConfig;
import edu.ooad.joballoc.domain.AuditEvent;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.orm.Migrator;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The in-memory storage family: factory choice, reference data, transactions, thread safety, copying. */
public class InMemoryStorageTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 15);

    private static Job job(String id) {
        return new Job(id, "Job " + id, Set.of("LINUX"), 1, DAY, DAY, Priority.LOW);
    }

    @Test
    public void memoryUrlSelectsTheInMemoryFamily() {
        try (Storage s = StorageFactory.create(AppConfig.from(Map.of("DB_URL", "memory:")))) {
            assertTrue(s instanceof InMemoryStorage);
            assertTrue(s.health().isUp());
        }
    }

    @Test
    public void startsWithTheSameReferenceDataAsTheSqlDatabases() throws IOException {
        String seed = Files.readString(Path.of("src/main/resources/db/migration/mysql/V2__seed_reference_data.sql"));
        DAOFactory daos = new InMemoryStorage().daos();
        assertEquals(ids(seed, "\\('([A-Z]+)',\\s+'[A-Z][a-z]"), daos.skillDao().findAll().stream()
                .map(s -> s.getCode()).toList());
        assertEquals(ids(seed, "\\('(W-\\d+)',\\s+'[A-Z][a-z]"), daos.workerDao().findAll().stream()
                .map(w -> w.getWorkerId()).toList());
        assertEquals(List.of("LINUX", "GO"), List.copyOf(daos.workerDao().findById("W-01").orElseThrow().getSkills()));
        String hash = daos.userDao().findById("supervisor").orElseThrow().getPasswordHash();
        assertTrue("same password hash as the SQL seed", seed.contains(hash));
    }

    private static List<String> ids(String sql, String regex) {
        Matcher m = Pattern.compile(regex).matcher(sql);
        return m.results().map(r -> r.group(1)).toList();
    }

    @Test
    public void aJobThatRequiresNoSkillRoundTrips() {
        DAOFactory daos = new InMemoryStorage().daos();
        daos.jobDao().save(new Job("M-0", "General task", Set.of(), 1, TestData.DAY, TestData.DAY, Priority.LOW));
        assertTrue(daos.jobDao().findById("M-0").orElseThrow().getRequiredSkills().isEmpty());
    }

    @Test
    public void aFailedTransactionLeavesNothingBehind() {
        InMemoryStorage s = new InMemoryStorage();
        DAOFactory daos = s.daos();
        s.transactions().inTransaction(() -> {
            daos.jobDao().save(job("M-1"));
            return null;
        });
        RuntimeException boom = new IllegalStateException("fails half-way");
        assertThrows(IllegalStateException.class, () -> s.transactions().inTransaction(() -> {
            Job j = daos.jobDao().findById("M-1").orElseThrow();
            j.markAllocated();
            daos.jobDao().update(j);
            daos.jobDao().save(job("M-2"));
            daos.auditDao().append(new AuditEvent("TEST", "t", "M-1", "x", Instant.now()));
            throw boom;
        }));
        Job after = daos.jobDao().findById("M-1").orElseThrow();
        assertEquals("update rolled back", 0, after.getVersion());
        assertEquals("state rolled back", "PENDING", after.getStatus().name());
        assertTrue("insert rolled back", daos.jobDao().findById("M-2").isEmpty());
        assertTrue("audit rolled back", daos.auditDao().findAll().isEmpty());
    }

    @Test
    public void manyThreadsCanWriteAtOnce() throws Exception {
        InMemoryStorage s = new InMemoryStorage();
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> done = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int base = t * perThread;
                done.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        int n = base + i;
                        s.transactions().inTransaction(() -> {
                            s.daos().jobDao().save(job("T-" + n));
                            return null;
                        });
                    }
                }));
            }
            for (Future<?> f : done) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(threads * perThread, s.daos().jobDao().findAll().size());
    }

    @Test
    public void copiesEverythingBetweenStoragesAndIsIdempotent() {
        InMemoryStorage from = new InMemoryStorage();
        from.transactions().inTransaction(() -> {
            from.daos().jobDao().save(job("C-1"));
            from.daos().auditDao().append(new AuditEvent("TEST", "t", "C-1", "x", Instant.now()));
            return null;
        });
        InMemoryStorage to = new InMemoryStorage();
        StorageTransfer.Report first = StorageTransfer.copy(from, to);
        assertEquals(1, first.copied("job"));
        assertEquals(1, first.copied("audit_event"));
        assertEquals("reference data was already there", 6, first.skipped("skill"));
        StorageTransfer.Report second = StorageTransfer.copy(from, to);
        assertEquals(0, second.copied("job"));
        assertEquals(1, second.skipped("audit_event"));
    }

    @Test
    public void checksumIgnoresWindowsLineEndings() {
        assertEquals(Migrator.checksum("a;\nb;\n"), Migrator.checksum("a;\r\nb;\r\n"));
        assertNotEquals(Migrator.checksum("a;\n"), Migrator.checksum("b;\n"));
    }
}
