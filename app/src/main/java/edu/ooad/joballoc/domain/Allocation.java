package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;
import edu.ooad.joballoc.persistence.mapping.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** ENTITY: the assignment of one job to one worker for the job's work date. */
@Entity
@Table("allocation")
public class Allocation {

    @Id @Column("allocation_id") private String allocationId;
    @Column("job_id") private String jobId;
    @Column("worker_id") private String workerId;
    @Column("work_date") private LocalDate workDate;
    @Column("hours") private int hours;
    @Column("allocated_by") private String allocatedBy;
    @Column("allocated_at") private Instant allocatedAt;
    @Column("status") private AllocationStatus status;
    @Version @Column("version") private long version;

    /** Required by the ORM. */
    protected Allocation() { }

    /** Created only by {@link Worker#assign} (GRASP Creator). */
    static Allocation create(Job job, Worker worker, String actor, Instant now) {
        Allocation a = new Allocation();
        a.allocationId = UUID.randomUUID().toString();
        a.jobId = job.getJobId();
        a.workerId = worker.getWorkerId();
        a.workDate = job.getWorkDate();
        a.hours = job.getEffortHours();
        a.allocatedBy = actor;
        a.allocatedAt = now;
        a.status = AllocationStatus.ACTIVE;
        return a;
    }

    public boolean isActive() {
        return status == AllocationStatus.ACTIVE;
    }

    /** Post-condition of unassign(): the allocation is RELEASED and frees the worker's capacity. */
    public void release() {
        if (!isActive()) {
            throw new AllocationRuleException("Allocation " + allocationId + " is already " + status);
        }
        status = AllocationStatus.RELEASED;
    }

    public String getAllocationId() { return allocationId; }
    public String getJobId() { return jobId; }
    public String getWorkerId() { return workerId; }
    public LocalDate getWorkDate() { return workDate; }
    public int getHours() { return hours; }
    public String getAllocatedBy() { return allocatedBy; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public AllocationStatus getStatus() { return status; }
    public long getVersion() { return version; }
}
