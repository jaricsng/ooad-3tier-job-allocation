package edu.ooad.joballoc.business.policy;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Worker;

import java.util.Comparator;
import java.util.List;

/**
 * STRATEGY (GoF behavioural): ranks eligible workers for a job. The best candidate comes first.
 *
 * <p>Before: autoAllocate() contained a {@code switch} on the rule name and grew with every
 * department's request (breaks Open/Closed). After: each rule is a class; adding one never edits
 * the service.
 *
 * <p>Refactored (TEMPLATE METHOD via a default method): {@link #rank} is the fixed algorithm (copy,
 * sort, break ties by worker ID so results are deterministic); a policy supplies only
 * {@link #order(Job)}. Before, all three policies repeated the stream/sort/tie-break code.
 */
public interface AllocationPolicy {

    String name();

    /** The policy's preference: smaller sorts first. Ties are broken by worker ID in {@link #rank}. */
    Comparator<Worker> order(Job job);

    /** @param eligible workers that can already take the job; it is not modified */
    default List<Worker> rank(Job job, List<Worker> eligible) {
        return eligible.stream().sorted(order(job).thenComparing(Worker::getWorkerId)).toList();
    }
}
