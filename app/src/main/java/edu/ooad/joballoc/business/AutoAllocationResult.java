package edu.ooad.joballoc.business;

/** Outcome of Auto-Allocate for one job. {@code workerId}/{@code allocationId} are null when not allocated. */
public record AutoAllocationResult(String jobId, boolean allocated, String workerId, String allocationId,
        String message) {

    /** A job that was not allocated, with the reason. */
    public static AutoAllocationResult skipped(String jobId, String reason) {
        return new AutoAllocationResult(jobId, false, null, null, reason);
    }
}
