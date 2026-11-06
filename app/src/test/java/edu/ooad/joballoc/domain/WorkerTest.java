package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.domain.event.DomainEvent;
import edu.ooad.joballoc.domain.event.JobAllocated;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static edu.ooad.joballoc.TestData.job;
import static edu.ooad.joballoc.TestData.worker;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Pre- and post-conditions of the allocate() contract, enforced by the domain (GRASP Information Expert). */
public class WorkerTest {

    private final Instant now = Instant.parse("2026-11-05T01:00:00Z");

    @Test
    public void canTakeWhenSkillsAndCapacityMatch() {
        Worker w = worker("W1", 8, "LINUX", "GO");
        Job j = job("J1", 4, Priority.HIGH, "LINUX");
        assertTrue(w.canTake(j));
        assertNull(w.reasonCannotTake(j));
    }

    @Test
    public void cannotTakeWithoutRequiredSkill() {
        Worker w = worker("W1", 8, "LINUX");
        Job j = job("J1", 2, Priority.LOW, "DBA");
        assertFalse(w.canTake(j));
        assertTrue(w.reasonCannotTake(j).contains("lacks skill(s) DBA"));
    }

    @Test
    public void anyWorkerWithCapacityCanTakeAJobThatRequiresNoSkill() {
        Job general = job("J1", 2, Priority.LOW);
        assertTrue(worker("W1", 8).canTake(general));
        assertTrue(worker("W2", 8, "LINUX").canTake(general));
    }

    @Test
    public void aWorkerWithoutSkillsCannotTakeAJobThatRequiresOne() {
        Worker trainee = worker("W1", 8);
        assertFalse(trainee.canTake(job("J1", 2, Priority.LOW, "LINUX")));
    }

    @Test
    public void cannotExceedDailyCapacity() {
        Worker w = worker("W1", 6, "LINUX");
        w.assign(job("J1", 4, Priority.LOW, "LINUX"), "sam", now);
        Job second = job("J2", 3, Priority.LOW, "LINUX");
        assertEquals(2, w.remainingCapacity(second.getWorkDate()));
        assertTrue(w.reasonCannotTake(second).contains("has 2h left"));
        assertThrows(AllocationRuleException.class, () -> w.assign(second, "sam", now));
    }

    @Test
    public void assignCreatesAllocationMarksJobAndRaisesEvent() {
        Worker w = worker("W1", 8, "LINUX");
        Job j = job("J1", 4, Priority.HIGH, "LINUX");
        Allocation a = w.assign(j, "sam", now);

        assertEquals(JobStatus.ALLOCATED, j.getStatus());
        assertEquals("J1", a.getJobId());
        assertEquals("W1", a.getWorkerId());
        assertEquals(4, a.getHours());
        assertTrue(a.isActive());
        assertEquals(4, w.allocatedHours(j.getWorkDate()));
        List<DomainEvent> events = w.pullEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof JobAllocated);
        assertTrue("events are drained", w.pullEvents().isEmpty());
    }

    @Test
    public void cannotAssignJobThatIsNotPending() {
        Worker w = worker("W1", 8, "LINUX");
        Job j = job("J1", 2, Priority.HIGH, "LINUX");
        w.assign(j, "sam", now);
        assertThrows(AllocationRuleException.class, () -> worker("W2", 8, "LINUX").assign(j, "sam", now));
    }

    @Test
    public void releasingAnAllocationFreesCapacity() {
        Worker w = worker("W1", 8, "LINUX");
        Job j = job("J1", 5, Priority.HIGH, "LINUX");
        Allocation a = w.assign(j, "sam", now);
        a.release();
        j.markPending();
        assertEquals(8, w.remainingCapacity(j.getWorkDate()));
        assertEquals(JobStatus.PENDING, j.getStatus());
        assertThrows(AllocationRuleException.class, a::release);
    }
}
