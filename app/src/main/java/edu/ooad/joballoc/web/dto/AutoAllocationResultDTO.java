package edu.ooad.joballoc.web.dto;

import edu.ooad.joballoc.business.AutoAllocationResult;

/**
 * One line of the POST /api/v1/allocations/auto response. Before: the API serialised the business
 * record directly, so renaming a field in the business tier silently changed the public API contract.
 */
public record AutoAllocationResultDTO(String jobId, boolean allocated, String workerId, String allocationId,
                                      String message) {
    public static AutoAllocationResultDTO from(AutoAllocationResult r) {
        return new AutoAllocationResultDTO(r.jobId(), r.allocated(), r.workerId(), r.allocationId(), r.message());
    }
}
