package edu.ooad.joballoc.web.dto;

import edu.ooad.joballoc.domain.Allocation;

/** Result of an allocation. */
public record AllocationResultDTO(String allocationId, String jobId, String workerId, String status, String message) {
    public static AllocationResultDTO from(Allocation a, String message) {
        return new AllocationResultDTO(a.getAllocationId(), a.getJobId(), a.getWorkerId(), a.getStatus().name(),
                message);
    }
}
