package edu.ooad.joballoc.web.dto;

import edu.ooad.joballoc.business.Candidate;

import java.util.List;

/** One row of the Eligible Workers panel. */
public record WorkerCandidateDTO(String workerId, String name, List<String> skills, int allocatedHours,
                                 int capacityHours, int remainingHours, boolean eligible, String reason) {
    public static WorkerCandidateDTO from(Candidate c) {
        return new WorkerCandidateDTO(c.worker().getWorkerId(), c.worker().getName(),
                List.copyOf(c.worker().getSkills()),
                c.allocatedHours(), c.worker().getDailyCapacityHours(), c.remainingHours(), c.eligible(), c.reason());
    }
}
