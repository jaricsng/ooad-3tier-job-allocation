package edu.ooad.joballoc.business;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Skill;

import java.util.List;

/**
 * Everything the Job Allocation Board shows, read in ONE transaction (a consistent snapshot).
 *
 * @param selected the job whose candidates are shown, or null when none is selected or it does not exist
 */
public record BoardData(List<Job> pending, Job selected, List<Candidate> candidates,
                        List<AllocationDetail> allocations, List<Skill> skills, List<String> policies) { }
