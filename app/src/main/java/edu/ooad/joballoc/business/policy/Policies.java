package edu.ooad.joballoc.business.policy;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Worker;

import java.util.Comparator;

/** Orderings shared by several policies (before: LEAST_LOADED and SKILL_MATCH each wrote this comparator). */
final class Policies {

    private Policies() { }

    static Comparator<Worker> mostRemainingCapacity(Job job) {
        return Comparator.comparingInt((Worker w) -> w.remainingCapacity(job.getWorkDate())).reversed();
    }
}
