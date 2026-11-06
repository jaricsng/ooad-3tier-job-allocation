package edu.ooad.joballoc.business;

import edu.ooad.joballoc.TestData;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.business.security.ForbiddenException;
import edu.ooad.joballoc.business.security.SecurityContext;
import edu.ooad.joballoc.business.security.UnauthenticatedException;
import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.AllocationRuleException;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.JobStatus;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static edu.ooad.joballoc.TestData.DAY;
import static edu.ooad.joballoc.TestData.job;
import static edu.ooad.joballoc.TestData.worker;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Business-tier tests through the real facade + proxy, with in-memory DAOs from the ABSTRACT
 * FACTORY: fast, no database. Each test checks one clause of an operation contract.
 */
public class AllocationServiceTest {

    private static final Actor SUPERVISOR = new Actor("sam", Role.SUPERVISOR, "Sam");
    private static final Actor VIEWER = new Actor("vera", Role.VIEWER, "Vera");

    private InMemoryDAOFactory daos;
    private AllocationService service;
    private final AtomicInteger transactions = new AtomicInteger();

    @Before
    public void setUp() {
        daos = new InMemoryDAOFactory();
        daos.workerDao().save(worker("W-01", 8, "LINUX", "GO"));
        daos.workerDao().save(worker("W-02", 8, "LINUX"));
        daos.workerDao().save(worker("W-03", 6, "DBA", "LINUX"));
        daos.jobDao().save(job("J-1", 4, Priority.HIGH, "LINUX"));
        daos.jobDao().save(job("J-2", 2, Priority.CRITICAL, "DBA"));
        daos.jobDao().save(job("J-3", 6, Priority.LOW, "SECURITY"));
        // Same FACTORY as production; only the transaction port differs (counts calls instead of using JDBC).
        service = BusinessTier.create(daos, new TransactionRunner() {
            @Override
            public <R> R inTransaction(Supplier<R> work) {
                transactions.incrementAndGet();
                return work.get();
            }
        }, TestData.CLOCK).service();
    }

    private <R> R as(Actor actor, Supplier<R> work) {
        return SecurityContext.runAs(actor, work);
    }

    @Test
    public void allocateSatisfiesPostConditions() {
        Allocation a = as(SUPERVISOR, () -> service.allocate("J-1", "W-02", 0));

        Job j = daos.jobDao().findById("J-1").orElseThrow();
        assertEquals(JobStatus.ALLOCATED, j.getStatus());
        assertEquals("job version incremented", 1, j.getVersion());
        assertTrue(daos.allocationDao().findActiveByJob("J-1").isPresent());
        assertEquals("sam", a.getAllocatedBy());
        assertEquals(1, daos.auditLog().size());
        assertEquals("JOB_ALLOCATED", daos.auditLog().get(0).getEventType());
        assertEquals("proxy opened a transaction", 1, transactions.get());
    }

    @Test
    public void staleVersionIsAConflict() {
        assertThrows(ConcurrencyConflictException.class, () -> as(SUPERVISOR, () -> service.allocate("J-1", "W-02",
                7)));
    }

    @Test
    public void concurrentUpdateDetectedByOptimisticLock() {
        daos.simulateConcurrentJobUpdate("J-1");
        assertThrows(ConcurrencyConflictException.class, () -> as(SUPERVISOR, () -> service.allocate("J-1", "W-02",
                0)));
    }

    @Test
    public void missingSkillViolatesTheContract() {
        AllocationRuleException e = assertThrows(AllocationRuleException.class,
                () -> as(SUPERVISOR, () -> service.allocate("J-2", "W-01", 0)));
        assertTrue(e.getMessage().contains("DBA"));
        assertEquals("no state change", JobStatus.PENDING, daos.jobDao().findById("J-2").orElseThrow().getStatus());
    }

    @Test
    public void unknownIdsAreNotFound() {
        assertThrows(NotFoundException.class, () -> as(SUPERVISOR, () -> service.allocate("NOPE", "W-01", 0)));
        assertThrows(NotFoundException.class, () -> as(SUPERVISOR, () -> service.allocate("J-1", "W-99", 0)));
    }

    @Test
    public void viewerCanReadButNotAllocate() {
        List<Job> pending = as(VIEWER, () -> service.findPendingJobs(new JobCriteria(DAY, null, null)));
        assertEquals(3, pending.size());
        assertEquals("CRITICAL first", "J-2", pending.get(0).getJobId());
        assertThrows(ForbiddenException.class, () -> as(VIEWER, () -> service.allocate("J-1", "W-02", 0)));
    }

    @Test
    public void proxyChecksTheRoleBeforeOpeningATransaction() {
        assertThrows(ForbiddenException.class, () -> as(VIEWER, () -> service.allocate("J-1", "W-02", 0)));
        assertEquals("security interceptor runs first: no transaction for a forbidden call", 0, transactions.get());
    }

    @Test
    public void loadBoardIsOneTransactionWithEverythingThePageShows() {
        BoardData board = as(VIEWER, () -> service.loadBoard(new JobCriteria(DAY, null, null), "J-1"));
        assertEquals("one facade call = one transaction (before: six calls, four transactions)", 1, transactions.get());
        assertEquals(3, board.pending().size());
        assertEquals("J-1", board.selected().getJobId());
        assertEquals(3, board.candidates().size());
        assertEquals(3, board.policies().size());

        BoardData unknown = as(VIEWER, () -> service.loadBoard(new JobCriteria(DAY, null, null), "NOPE"));
        assertNull(unknown.selected());
        assertTrue(unknown.candidates().isEmpty());
    }

    @Test
    public void allocationsListLoadsJobsAndWorkersInBulk() {
        as(SUPERVISOR, () -> service.allocate("J-1", "W-02", 0));
        as(SUPERVISOR, () -> service.allocate("J-2", "W-03", 0));
        List<AllocationDetail> details = as(VIEWER, () -> service.findAllocations(DAY));
        assertEquals(2, details.size());
        details.forEach(d -> {
            assertNotNull(d.job());
            assertNotNull(d.worker());
        });
    }

    @Test
    public void anonymousCallsAreRejected() {
        assertThrows(UnauthenticatedException.class, () -> service.findPendingJobs(new JobCriteria(DAY, null, null)));
    }

    @Test
    public void eligibleWorkersAreRankedFirstWithReasonsForOthers() {
        List<Candidate> c = as(VIEWER, () -> service.findEligibleWorkers("J-1"));
        assertEquals(3, c.size());
        assertEquals("closest skill match first (SKILL_MATCH)", "W-02", c.get(0).worker().getWorkerId());
        assertTrue(c.get(0).eligible());
        long eligible = c.stream().filter(Candidate::eligible).count();
        assertEquals(3, eligible);
        List<Candidate> forSecurity = as(VIEWER, () -> service.findEligibleWorkers("J-3"));
        assertTrue(forSecurity.stream().noneMatch(Candidate::eligible));
        assertNotNull(forSecurity.get(0).reason());
    }

    @Test
    public void autoAllocateUsesPolicyAndReportsUnallocatable() {
        List<AutoAllocationResult> results = as(SUPERVISOR,
                () -> service.autoAllocate(List.of("J-1", "J-2", "J-3"), "LEAST_LOADED"));
        assertEquals(3, results.size());
        assertEquals("most urgent processed first", "J-2", results.get(0).jobId());
        assertEquals("W-03", results.get(0).workerId());
        assertTrue(results.get(1).allocated());
        assertFalse("nobody has SECURITY", results.get(2).allocated());
        assertEquals(2, daos.auditLog().size());
    }

    @Test
    public void unknownPolicyIsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> as(SUPERVISOR, () -> service.autoAllocate(List.of("J-1"),
                "RANDOM")));
    }

    @Test
    public void unassignRestoresPendingAndFreesCapacity() {
        Allocation a = as(SUPERVISOR, () -> service.allocate("J-1", "W-02", 0));
        as(SUPERVISOR, () -> {
            service.unassign(a.getAllocationId());
            return null;
        });
        assertEquals(JobStatus.PENDING, daos.jobDao().findById("J-1").orElseThrow().getStatus());
        assertTrue(as(VIEWER, () -> service.findAllocations(DAY)).isEmpty());
        assertEquals("JOB_UNASSIGNED", daos.auditLog().get(1).getEventType());
        assertThrows(AllocationRuleException.class, () -> as(SUPERVISOR, () -> {
            service.unassign(a.getAllocationId());
            return null;
        }));
    }
}
