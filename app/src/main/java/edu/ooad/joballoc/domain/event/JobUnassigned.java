package edu.ooad.joballoc.domain.event;

import java.time.Instant;

/** Raised when an allocation is released and its job returns to PENDING. */
public record JobUnassigned(String jobId, String workerId, String allocationId, String actor, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "JOB_UNASSIGNED";
    }

    @Override
    public String entityId() {
        return jobId;
    }

    @Override
    public String detail() {
        return "job " + jobId + " released from worker " + workerId + " (allocation " + allocationId + ")";
    }
}
