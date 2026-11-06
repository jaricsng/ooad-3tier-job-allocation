package edu.ooad.joballoc.business.policy;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Worker;

import java.util.Comparator;

/** Prefers the closest skill match (fewest unused skills) so specialists stay free; then most capacity. */
public final class SkillMatchPolicy implements AllocationPolicy {

    @Override
    public String name() {
        return "SKILL_MATCH";
    }

    @Override
    public Comparator<Worker> order(Job job) {
        Comparator<Worker> fewestExtraSkills = Comparator.comparingInt(
                w -> w.getSkills().size() - job.getRequiredSkills().size());
        return fewestExtraSkills.thenComparing(Policies.mostRemainingCapacity(job));
    }
}
