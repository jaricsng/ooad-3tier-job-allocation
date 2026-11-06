package edu.ooad.joballoc.domain;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Set;

import static edu.ooad.joballoc.TestData.job;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The Job guards its own state transitions and invariants. */
public class JobTest {

    @Test
    public void newJobIsPendingAndAllocatable() {
        Job j = job("J1", 3, Priority.MEDIUM, "JAVA");
        assertEquals(JobStatus.PENDING, j.getStatus());
        assertTrue(j.isAllocatable());
    }

    @Test
    public void rejectsInvalidEffortAndMissingSkills() {
        LocalDate d = LocalDate.of(2026, 11, 5);
        assertThrows(IllegalArgumentException.class, () -> new Job("J1", "t", Set.of("X"), 0, d, d, Priority.LOW));
        assertThrows(IllegalArgumentException.class, () -> new Job("J1", "t", Set.of("X"), 25, d, d, Priority.LOW));
        assertThrows(IllegalArgumentException.class, () -> new Job("J1", "t", Set.of(), 2, d, d, Priority.LOW));
        assertThrows(IllegalArgumentException.class, () -> new Job(" ", "t", Set.of("X"), 2, d, d, Priority.LOW));
    }

    @Test
    public void invalidTransitionsAreRejected() {
        Job j = job("J1", 3, Priority.MEDIUM, "JAVA");
        assertThrows(AllocationRuleException.class, j::markPending);
        j.markAllocated();
        assertThrows(AllocationRuleException.class, j::markAllocated);
    }

    @Test
    public void roleHierarchy() {
        assertTrue(Role.SUPERVISOR.includes(Role.VIEWER));
        assertTrue(Role.VIEWER.includes(Role.VIEWER));
        assertFalse(Role.VIEWER.includes(Role.SUPERVISOR));
    }
}
