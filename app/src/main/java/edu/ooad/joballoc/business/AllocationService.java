package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.proxy.Transactional;
import edu.ooad.joballoc.business.security.RequiresRole;
import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.domain.Skill;

import java.time.LocalDate;
import java.util.List;

/**
 * FACADE (GoF structural): the single entry point from the presentation tier into the allocation
 * subsystem. Each method is one system operation derived from a UI event on the Job Allocation
 * Board. Controllers never touch DAOs, policies or transactions directly.
 *
 * <p>The annotations are enforced by {@link SecuredTransactionalProxy} (PROXY pattern).
 */
public interface AllocationService {

    /**
     * UI: open or refresh the board. A coarse-grained FACADE operation: one call and one transaction
     * return everything the page shows. Before: the controller made six fine-grained calls (four
     * separate transactions), so the grid and the allocations list could disagree if someone allocated in between.
     *
     * @param selectedJobId the job whose eligible workers to include, or null
     */
    @RequiresRole(Role.VIEWER) @Transactional
    BoardData loadBoard(JobCriteria criteria, String selectedJobId);

    /** UI: Pending Jobs grid / [Refresh]. */
    @RequiresRole(Role.VIEWER) @Transactional
    List<Job> findPendingJobs(JobCriteria criteria);

    /** UI: select a job row. Eligible workers first, ranked by the default policy. */
    @RequiresRole(Role.VIEWER) @Transactional
    List<Candidate> findEligibleWorkers(String jobId);

    /** UI: [Allocate]. Operation contract: see docs/ooad-walkthrough.md section 3. */
    @RequiresRole(Role.SUPERVISOR) @Transactional
    Allocation allocate(String jobId, String workerId, long jobVersion);

    /** UI: [Auto-Allocate] with a named policy (STRATEGY). */
    @RequiresRole(Role.SUPERVISOR) @Transactional
    List<AutoAllocationResult> autoAllocate(List<String> jobIds, String policyName);

    /** UI: [Unassign]. */
    @RequiresRole(Role.SUPERVISOR) @Transactional
    void unassign(String allocationId);

    /** UI: Allocations list for a date. */
    @RequiresRole(Role.VIEWER) @Transactional
    List<AllocationDetail> findAllocations(LocalDate date);

    @RequiresRole(Role.VIEWER) @Transactional
    Job getJob(String jobId);

    /** API: GET /api/v1/allocations/{id} (the resource the Location header of a create points to). */
    @RequiresRole(Role.VIEWER) @Transactional
    AllocationDetail getAllocation(String allocationId);

    @RequiresRole(Role.VIEWER)
    List<Skill> listSkills();

    @RequiresRole(Role.VIEWER)
    List<String> policyNames();
}
