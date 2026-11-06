package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.business.AllocationService;
import edu.ooad.joballoc.business.ConcurrencyConflictException;
import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.web.Json;
import edu.ooad.joballoc.web.dto.AllocateRequestDTO;
import edu.ooad.joballoc.web.dto.AllocationDTO;
import edu.ooad.joballoc.web.dto.AllocationResultDTO;
import edu.ooad.joballoc.web.dto.AutoAllocateRequestDTO;
import edu.ooad.joballoc.web.dto.AutoAllocationResultDTO;
import edu.ooad.joballoc.web.dto.JobSummaryDTO;
import edu.ooad.joballoc.web.dto.WorkerCandidateDTO;
import edu.ooad.joballoc.web.http.BadRequestException;
import edu.ooad.joballoc.web.http.HttpStatus;
import edu.ooad.joballoc.web.http.PreconditionFailedException;
import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;

import java.time.Clock;

/** REST API under /api/v1: the same system operations as the board, as JSON. */
public class ApiController {

    private final AllocationService service;
    private final Clock clock;

    public ApiController(AllocationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** GET /api/v1/jobs?status=PENDING&date=&priority=&skill= */
    public Response pendingJobs(Request req) {
        String status = req.optionalParam("status");
        if (status != null && !status.equalsIgnoreCase("PENDING")) {
            throw new BadRequestException("Only status=PENDING is supported");
        }
        JobCriteria criteria = RequestBinding.jobCriteria(req, clock);
        return ok(service.findPendingJobs(criteria).stream().map(JobSummaryDTO::from).toList());
    }

    /** GET /api/v1/jobs/{id}/eligible-workers */
    public Response eligibleWorkers(Request req) {
        return ok(service.findEligibleWorkers(req.pathVar("id")).stream().map(WorkerCandidateDTO::from).toList());
    }

    /**
     * GET /api/v1/jobs/{id}. The ETag is the job's optimistic-lock version, so a client can send it back
     * as If-Match when allocating (optimistic concurrency over HTTP), or as If-None-Match to revalidate.
     */
    public Response job(Request req) {
        Job job = service.getJob(req.pathVar("id"));
        String etag = ETags.of(job.getVersion());
        if (etag.equals(req.header("If-None-Match"))) {
            return new Response().status(HttpStatus.NOT_MODIFIED).header("ETag", etag);
        }
        return ok(JobSummaryDTO.from(job)).header("ETag", etag);
    }

    /**
     * POST /api/v1/allocations {jobId, workerId, jobVersion}. The expected version comes from the body
     * or from an If-Match header carrying the job's ETag; a stale If-Match is 412, a stale body version 409.
     */
    public Response allocate(Request req) {
        AllocateRequestDTO body = Json.read(req.body(), AllocateRequestDTO.class);
        String ifMatch = req.header("If-Match");
        if (body.jobId() == null || body.workerId() == null || (body.jobVersion() == null && ifMatch == null)) {
            throw new BadRequestException("jobId, workerId and jobVersion (or an If-Match header) are required");
        }
        long version = body.jobVersion() != null ? body.jobVersion() : ETags.version(ifMatch);
        Allocation a;
        try {
            a = service.allocate(body.jobId(), body.workerId(), version);
        } catch (ConcurrencyConflictException e) {
            if (body.jobVersion() == null) {
                throw new PreconditionFailedException(e.getMessage(), e);
            }
            throw e;
        }
        return Response.json(HttpStatus.CREATED, AllocationResultDTO.from(a, "Allocated"))
                .header("Location", "/api/v1/allocations/" + a.getAllocationId());
    }

    /** GET /api/v1/allocations/{id} */
    public Response allocation(Request req) {
        return ok(AllocationDTO.from(service.getAllocation(req.pathVar("id"))));
    }

    /** POST /api/v1/allocations/auto {jobIds, policy} */
    public Response autoAllocate(Request req) {
        AutoAllocateRequestDTO body = Json.read(req.body(), AutoAllocateRequestDTO.class);
        return ok(service.autoAllocate(body.jobIds(),
                body.policy()).stream().map(AutoAllocationResultDTO::from).toList());
    }

    /** DELETE /api/v1/allocations/{id} */
    public Response unassign(Request req) {
        service.unassign(req.pathVar("id"));
        return new Response().status(HttpStatus.NO_CONTENT);
    }

    /** GET /api/v1/allocations?date= */
    public Response allocations(Request req) {
        return ok(service.findAllocations(RequestBinding.date(req, clock)).stream().map(AllocationDTO::from).toList());
    }

    private static Response ok(Object body) {
        return Response.json(HttpStatus.OK, body);
    }
}
