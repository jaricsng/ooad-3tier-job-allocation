package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.domain.event.DomainEvent;
import edu.ooad.joballoc.domain.event.JobAllocated;
import edu.ooad.joballoc.persistence.mapping.CollectionTable;
import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;
import edu.ooad.joballoc.persistence.mapping.Transient;
import edu.ooad.joballoc.persistence.mapping.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ENTITY: a person who can be allocated jobs.
 *
 * <p>GRASP Information Expert: the worker knows its skills and current load, so it decides
 * {@link #canTake(Job)}. GRASP Creator: the worker holds its allocations, so
 * {@link #assign(Job, String, Instant)} creates the {@link Allocation}. This keeps the domain
 * model rich rather than anemic.
 */
@Entity
@Table("worker")
public class Worker {

    @Id @Column("worker_id") private String workerId;
    @Column("name") private String name;
    @Column("daily_capacity_hours") private int dailyCapacityHours;
    @CollectionTable(table = "worker_skill", joinColumn = "worker_id", valueColumn = "skill_code")
    private Set<String> skills = new LinkedHashSet<>();
    @Version @Column("version") private long version;

    /** Active allocations, loaded by the DAO (an aggregate is assembled by its mapper). */
    @Transient private final List<Allocation> allocations = new ArrayList<>();
    /** Domain events raised by this aggregate, drained by the application service. */
    @Transient private final List<DomainEvent> pendingEvents = new ArrayList<>();

    /** Required by the ORM. */
    protected Worker() { }

    public Worker(String workerId, String name, int dailyCapacityHours, Set<String> skills) {
        if (dailyCapacityHours < 1 || dailyCapacityHours > Job.HOURS_PER_DAY) {
            throw new IllegalArgumentException("dailyCapacityHours must be between 1 and " + Job.HOURS_PER_DAY);
        }
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.name = Objects.requireNonNull(name, "name");
        this.dailyCapacityHours = dailyCapacityHours;
        this.skills = new LinkedHashSet<>(skills);
    }

    public boolean hasAllSkills(Set<String> required) {
        return skills.containsAll(required);
    }

    public int allocatedHours(LocalDate on) {
        return allocations.stream()
                .filter(a -> a.isActive() && a.getWorkDate().equals(on))
                .mapToInt(Allocation::getHours)
                .sum();
    }

    public int remainingCapacity(LocalDate on) {
        return dailyCapacityHours - allocatedHours(on);
    }

    public boolean canTake(Job job) {
        return reasonCannotTake(job) == null;
    }

    /** Explains why the job cannot be taken, or returns null when it can. */
    public String reasonCannotTake(Job job) {
        if (!job.isAllocatable()) {
            return "Job " + job.getJobId() + " is " + job.getStatus() + ", not PENDING";
        }
        if (!hasAllSkills(job.getRequiredSkills())) {
            Set<String> missing = new LinkedHashSet<>(job.getRequiredSkills());
            missing.removeAll(skills);
            return name + " lacks skill(s) " + String.join(", ", missing);
        }
        int remaining = remainingCapacity(job.getWorkDate());
        if (remaining < job.getEffortHours()) {
            return name + " has " + remaining + "h left on " + job.getWorkDate()
                    + " but the job needs " + job.getEffortHours() + "h";
        }
        return null;
    }

    /**
     * Allocates the job to this worker (operation contract allocate()).
     *
     * @throws AllocationRuleException when a pre-condition fails
     */
    public Allocation assign(Job job, String actor, Instant now) {
        String reason = reasonCannotTake(job);
        if (reason != null) {
            throw new AllocationRuleException(reason);
        }
        job.markAllocated();
        Allocation allocation = Allocation.create(job, this, actor, now);
        allocations.add(allocation);
        pendingEvents.add(new JobAllocated(job.getJobId(), workerId, allocation.getAllocationId(), actor, now));
        return allocation;
    }

    /** Used by the DAO when it assembles the aggregate. */
    public void attachAllocations(List<Allocation> loaded) {
        allocations.clear();
        allocations.addAll(loaded);
    }

    /** Returns and clears the events raised since the last call. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public String getWorkerId() { return workerId; }
    public String getName() { return name; }
    public int getDailyCapacityHours() { return dailyCapacityHours; }
    public Set<String> getSkills() { return Collections.unmodifiableSet(skills); }
    public long getVersion() { return version; }
    public List<Allocation> getAllocations() { return Collections.unmodifiableList(allocations); }

    @Override
    public String toString() {
        return "Worker[" + workerId + ", " + name + ", v" + version + "]";
    }
}
