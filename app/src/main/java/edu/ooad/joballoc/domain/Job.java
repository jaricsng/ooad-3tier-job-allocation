package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.persistence.mapping.CollectionTable;
import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;
import edu.ooad.joballoc.persistence.mapping.Version;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * ENTITY: a unit of work waiting to be allocated to a worker.
 *
 * <p>The job guards its own state transitions (PENDING to ALLOCATED and back), so no caller can
 * put it into an invalid state. {@code version} supports optimistic locking.
 */
@Entity
@Table("job")
public class Job {

    /** Upper bound for effort and daily capacity: a job or a worker's day never exceeds one calendar day. */
    public static final int HOURS_PER_DAY = 24;

    @Id @Column("job_id") private String jobId;
    @Column("title") private String title;
    @CollectionTable(table = "job_skill", joinColumn = "job_id", valueColumn = "skill_code")
    private Set<String> requiredSkills = new LinkedHashSet<>();
    @Column("effort_hours") private int effortHours;
    @Column("work_date") private LocalDate workDate;
    @Column("due_date") private LocalDate dueDate;
    @Column("priority") private Priority priority;
    @Column("status") private JobStatus status;
    @Version @Column("version") private long version;

    /** Required by the ORM. */
    protected Job() { }

    public Job(String jobId, String title, Set<String> requiredSkills, int effortHours,
               LocalDate workDate, LocalDate dueDate, Priority priority) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (effortHours < 1 || effortHours > HOURS_PER_DAY) {
            throw new IllegalArgumentException("effortHours must be between 1 and " + HOURS_PER_DAY);
        }
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            throw new IllegalArgumentException("a job needs at least one required skill");
        }
        this.jobId = jobId;
        this.title = Objects.requireNonNull(title, "title");
        this.requiredSkills = new LinkedHashSet<>(requiredSkills);
        this.effortHours = effortHours;
        this.workDate = Objects.requireNonNull(workDate, "workDate");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.status = JobStatus.PENDING;
    }

    /** Pre-condition of allocate(): only PENDING jobs can be allocated. */
    public boolean isAllocatable() {
        return status == JobStatus.PENDING;
    }

    /** Post-condition of allocate(): the job becomes ALLOCATED. */
    public void markAllocated() {
        if (!isAllocatable()) {
            throw new AllocationRuleException("Job " + jobId + " is " + status + ", not PENDING");
        }
        status = JobStatus.ALLOCATED;
    }

    /** Post-condition of unassign(): the job returns to PENDING. */
    public void markPending() {
        if (status != JobStatus.ALLOCATED) {
            throw new AllocationRuleException("Job " + jobId + " is " + status + ", not ALLOCATED");
        }
        status = JobStatus.PENDING;
    }

    public String getJobId() { return jobId; }
    public String getTitle() { return title; }
    public Set<String> getRequiredSkills() { return Collections.unmodifiableSet(requiredSkills); }
    public int getEffortHours() { return effortHours; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalDate getDueDate() { return dueDate; }
    public Priority getPriority() { return priority; }
    public JobStatus getStatus() { return status; }
    public long getVersion() { return version; }

    @Override
    public String toString() {
        return "Job[" + jobId + ", " + status + ", v" + version + "]";
    }
}
