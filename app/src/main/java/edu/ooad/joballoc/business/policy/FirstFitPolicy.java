package edu.ooad.joballoc.business.policy;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Worker;

import java.util.Comparator;

/** Baseline: the first eligible worker by ID. Useful in class to compare against smarter policies. */
public final class FirstFitPolicy implements AllocationPolicy {

    @Override
    public String name() {
        return "FIRST_FIT";
    }

    @Override
    public Comparator<Worker> order(Job job) {
        return (a, b) -> 0; // no preference: the tie-break by worker ID decides
    }
}
