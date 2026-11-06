package edu.ooad.joballoc.web.dto;

import edu.ooad.joballoc.business.AllocationDetail;

import java.time.Instant;
import java.time.LocalDate;

/** One row of the Allocations list. */
public record AllocationDTO(String allocationId, String jobId, String jobTitle, String workerId, String workerName,
                            LocalDate workDate, int hours, String allocatedBy, Instant allocatedAt) {
    public static AllocationDTO from(AllocationDetail d) {
        var a = d.allocation();
        return new AllocationDTO(a.getAllocationId(), a.getJobId(), d.job().getTitle(), a.getWorkerId(),
                d.worker() == null ? null : d.worker().getName(), a.getWorkDate(), a.getHours(),
                a.getAllocatedBy(), a.getAllocatedAt());
    }
}
