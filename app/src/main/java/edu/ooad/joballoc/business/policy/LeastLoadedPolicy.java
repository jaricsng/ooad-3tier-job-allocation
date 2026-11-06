package edu.ooad.joballoc.business.policy;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Worker;

import java.util.Comparator;

/** Balances workload: the worker with the most remaining hours on the job's date comes first. */
public final class LeastLoadedPolicy implements AllocationPolicy {

    @Override
    public String name() {
        return "LEAST_LOADED";
    }

    @Override
    public Comparator<Worker> order(Job job) {
        return Policies.mostRemainingCapacity(job);
    }
}
