package edu.ooad.joballoc.web.dto;

import edu.ooad.joballoc.domain.Job;

import java.time.LocalDate;
import java.util.List;

/** What the Pending Jobs grid shows for one job; carries {@code version} for optimistic locking. */
public record JobSummaryDTO(String jobId, String title, List<String> requiredSkills, int effortHours,
                            LocalDate workDate, LocalDate dueDate, String priority, String status, long version) {
    public static JobSummaryDTO from(Job j) {
        return new JobSummaryDTO(j.getJobId(), j.getTitle(), List.copyOf(j.getRequiredSkills()), j.getEffortHours(),
                j.getWorkDate(), j.getDueDate(), j.getPriority().name(), j.getStatus().name(), j.getVersion());
    }
}
