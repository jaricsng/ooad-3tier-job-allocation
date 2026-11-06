package edu.ooad.joballoc.domain.event;

import java.time.Instant;

/** Raised by {@code Worker.assign} when a job is allocated. */
public record JobAllocated(String jobId, String workerId, String allocationId, String actor, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "JOB_ALLOCATED";
    }

    @Override
    public String entityId() {
        return jobId;
    }

    @Override
    public String detail() {
        return "job " + jobId + " -> worker " + workerId + " (allocation " + allocationId + ")";
    }
}
