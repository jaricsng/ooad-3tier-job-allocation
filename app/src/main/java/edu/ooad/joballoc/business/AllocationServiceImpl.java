package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.event.DomainEventPublisher;
import edu.ooad.joballoc.business.policy.AllocationPolicy;
import edu.ooad.joballoc.business.policy.PolicyFactory;
import edu.ooad.joballoc.business.security.SecurityContext;
import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.domain.Skill;
import edu.ooad.joballoc.domain.Worker;
import edu.ooad.joballoc.domain.event.JobUnassigned;
import edu.ooad.joballoc.persistence.dao.AllocationDAO;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.dao.JobDAO;
import edu.ooad.joballoc.persistence.dao.SkillDAO;
import edu.ooad.joballoc.persistence.dao.WorkerDAO;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The application service behind the {@link AllocationService} facade. It orchestrates use cases:
 * load aggregates through DAOs, let domain objects apply the rules, save, publish events.
 *
 * <p>It contains no security or transaction code: {@link SecuredTransactionalProxy} adds both.
 */
public class AllocationServiceImpl implements AllocationService {

    private final JobDAO jobs;
    private final WorkerDAO workers;
    private final AllocationDAO allocations;
    private final SkillDAO skills;
    private final PolicyFactory policies;
    private final DomainEventPublisher events;
    private final Clock clock;

    public AllocationServiceImpl(DAOFactory daos, PolicyFactory policies, DomainEventPublisher events, Clock clock) {
        this.jobs = daos.jobDao();
        this.workers = daos.workerDao();
        this.allocations = daos.allocationDao();
        this.skills = daos.skillDao();
        this.policies = policies;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public BoardData loadBoard(JobCriteria criteria, String selectedJobId) {
        Job selected = selectedJobId == null ? null : jobs.findById(selectedJobId).orElse(null);
        return new BoardData(findPendingJobs(criteria), selected,
                selected == null ? List.of() : candidatesFor(selected),
                findAllocations(criteria.date()), listSkills(), policyNames());
    }

    @Override
    public List<Job> findPendingJobs(JobCriteria criteria) {
        return jobs.findPending(criteria);
    }

    @Override
    public List<Candidate> findEligibleWorkers(String jobId) {
        return candidatesFor(loadJob(jobId));
    }

    @Override
    public Allocation allocate(String jobId, String workerId, long jobVersion) {
        Job job = loadJob(jobId);
        if (job.getVersion() != jobVersion) {
            throw new ConcurrencyConflictException("Job " + jobId + " was changed by someone else "
                    + "(you saw version " + jobVersion + ", current is " + job.getVersion() + "). Refresh and retry.");
        }
        Worker worker = workers.findWithAllocations(workerId, job.getWorkDate())
                .orElseThrow(() -> new NotFoundException("Worker " + workerId + " not found"));
        return assignAndSave(job, worker);
    }

    @Override
    public List<AutoAllocationResult> autoAllocate(List<String> jobIds, String policyName) {
        AllocationPolicy policy = policies.forName(policyName);
        if (jobIds == null || jobIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one job to auto-allocate");
        }
        // Most urgent first: priority (CRITICAL..LOW), then due date.
        List<Job> selected = new LinkedHashSet<>(jobIds).stream().map(this::loadJob)
                .sorted(Comparator.comparing(Job::getPriority, Comparator.<Priority>reverseOrder())
                        .thenComparing(Job::getDueDate).thenComparing(Job::getJobId))
                .toList();

        List<AutoAllocationResult> results = new ArrayList<>();
        for (Job job : selected) {
            if (!job.isAllocatable()) {
                results.add(AutoAllocationResult.skipped(job.getJobId(), "Skipped: job is " + job.getStatus()));
                continue;
            }
            List<Worker> ranked = policy.rank(job, eligibleFor(job, workers.findAllWithAllocations(job.getWorkDate())));
            if (ranked.isEmpty()) {
                results.add(AutoAllocationResult.skipped(job.getJobId(),
                        "No eligible worker with the required skills and capacity"));
                continue;
            }
            Worker chosen = ranked.get(0);
            Allocation a = assignAndSave(job, chosen);
            results.add(new AutoAllocationResult(job.getJobId(), true, chosen.getWorkerId(), a.getAllocationId(),
                    "Allocated to " + chosen.getName() + " by " + policy.name()));
        }
        return results;
    }

    @Override
    public void unassign(String allocationId) {
        Allocation allocation = allocations.findById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation " + allocationId + " not found"));
        Job job = loadJob(allocation.getJobId());
        allocation.release();
        job.markPending();
        allocations.update(allocation);
        jobs.update(job);
        events.publish(new JobUnassigned(job.getJobId(), allocation.getWorkerId(), allocationId, currentUser(), now()));
    }

    @Override
    public List<AllocationDetail> findAllocations(LocalDate date) {
        List<Allocation> active = allocations.findActiveByDate(date);
        // Three queries in total: allocations, their jobs, their workers. Before: all workers, plus one
        // query per allocation for its job (the N+1 problem).
        Map<String, Job> jobById = byId(jobs.findByIds(active.stream().map(Allocation::getJobId).toList()),
                Job::getJobId);
        Map<String, Worker> workerById = byId(workers.findByIds(active.stream().map(Allocation::getWorkerId).toList()),
                Worker::getWorkerId);
        return active.stream()
                .map(a -> new AllocationDetail(a, jobById.get(a.getJobId()), workerById.get(a.getWorkerId())))
                .toList();
    }

    @Override
    public Job getJob(String jobId) {
        return loadJob(jobId);
    }

    @Override
    public AllocationDetail getAllocation(String allocationId) {
        Allocation a = allocations.findById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation " + allocationId + " not found"));
        return new AllocationDetail(a, loadJob(a.getJobId()), workers.findById(a.getWorkerId()).orElse(null));
    }

    @Override
    public List<Skill> listSkills() {
        return skills.findAll();
    }

    @Override
    public List<String> policyNames() {
        return policies.names();
    }

    /**
     * Eligible workers first (ranked by the default policy), then the others with the reason they
     * cannot take the job.
     */
    private List<Candidate> candidatesFor(Job job) {
        LocalDate on = job.getWorkDate();
        List<Worker> all = workers.findAllWithAllocations(on);
        List<Candidate> result = new ArrayList<>();
        for (Worker w : policies.forName(PolicyFactory.DEFAULT).rank(job, eligibleFor(job, all))) {
            result.add(new Candidate(w, w.allocatedHours(on), w.remainingCapacity(on), true, null));
        }
        all.stream()
                .filter(w -> !w.canTake(job))
                .sorted(Comparator.comparing(Worker::getWorkerId))
                .forEach(w -> result.add(new Candidate(w, w.allocatedHours(on), w.remainingCapacity(on),
                        false, w.reasonCannotTake(job))));
        return result;
    }

    /** The one definition of "eligible" (before: repeated in findEligibleWorkers and autoAllocate). */
    private static List<Worker> eligibleFor(Job job, List<Worker> workers) {
        return workers.stream().filter(w -> w.canTake(job)).toList();
    }

    private static <T> Map<String, T> byId(List<T> rows, Function<T, String> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    /** Worker applies the rules (Information Expert); the service persists and publishes. */
    private Allocation assignAndSave(Job job, Worker worker) {
        Allocation allocation = worker.assign(job, currentUser(), now()); // throws AllocationRuleException
        allocations.save(allocation);
        jobs.update(job);       // optimistic lock: the job was not allocated by someone else meanwhile
        workers.update(worker); // optimistic lock: the worker's capacity was not used up meanwhile
        events.publishAll(worker.pullEvents());
        return allocation;
    }

    private Job loadJob(String jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job " + jobId + " not found"));
    }

    private static String currentUser() {
        return SecurityContext.require().username();
    }

    private Instant now() {
        return clock.instant();
    }
}
