package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.policy.AllocationPolicy;
import edu.ooad.joballoc.business.policy.FirstFitPolicy;
import edu.ooad.joballoc.business.policy.PolicyFactory;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.domain.Worker;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static edu.ooad.joballoc.TestData.job;
import static edu.ooad.joballoc.TestData.worker;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** STRATEGY: each policy ranks the same candidates differently; the factory resolves them by name. */
public class PolicyTest {

    private final PolicyFactory factory = new PolicyFactory();

    private List<String> ids(AllocationPolicy p, Job j, List<Worker> ws) {
        return p.rank(j, ws).stream().map(Worker::getWorkerId).toList();
    }

    @Test
    public void policiesRankDifferently() {
        Worker specialist = worker("W-B", 8, "LINUX");
        Worker generalist = worker("W-A", 8, "LINUX", "GO", "DBA");
        Worker busy = worker("W-C", 8, "LINUX");
        busy.assign(job("X", 6, Priority.LOW, "LINUX"), "t", Instant.EPOCH);
        Job j = job("J", 2, Priority.HIGH, "LINUX");
        List<Worker> all = List.of(specialist, generalist, busy);

        assertEquals(List.of("W-B", "W-C", "W-A"), ids(factory.forName("SKILL_MATCH"), j, all));
        assertEquals(List.of("W-A", "W-B", "W-C"), ids(factory.forName("LEAST_LOADED"), j, all));
        assertEquals(List.of("W-A", "W-B", "W-C"), ids(factory.forName("first_fit"), j, all));
    }

    @Test
    public void factoryDefaultsAndRejectsUnknown() {
        assertEquals(PolicyFactory.DEFAULT, factory.forName(null).name());
        assertEquals(List.of("SKILL_MATCH", "LEAST_LOADED", "FIRST_FIT"), factory.names());
        assertThrows(IllegalArgumentException.class, () -> factory.forName("RANDOM"));
    }

    @Test
    public void factorySharesStatelessPoliciesAndRejectsDuplicateNames() {
        assertSame("one shared instance per policy", factory.forName("LEAST_LOADED"), factory.forName("least_loaded"));
        assertThrows(IllegalArgumentException.class, () -> factory.register(new FirstFitPolicy()));
    }
}
