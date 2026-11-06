package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.business.AllocationService;
import edu.ooad.joballoc.business.AutoAllocationResult;
import edu.ooad.joballoc.business.BoardData;
import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.web.http.ErrorMapper;
import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;
import edu.ooad.joballoc.web.http.Session;
import edu.ooad.joballoc.web.view.BoardView;
import edu.ooad.joballoc.web.view.Html;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * CONTROLLER for the Job Allocation Board pages. It turns UI events into calls on the
 * {@link AllocationService} facade and picks the view; it contains no business rules.
 * State-changing actions use POST-Redirect-GET so a browser refresh never repeats them.
 */
public class BoardController {

    private final AllocationService service;
    private final Clock clock;

    public BoardController(AllocationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** GET /board: one facade call, so the page is a consistent snapshot. */
    public Response board(Request req) {
        JobCriteria criteria = RequestBinding.jobCriteria(req, clock);
        String jobId = req.optionalParam("jobId");
        BoardData data = service.loadBoard(criteria, jobId);
        String error = req.param("err");
        if (jobId != null && data.selected() == null && error == null) {
            error = "Job " + jobId + " not found";
        }
        BoardView.Model model = new BoardView.Model(req.actor(), req.session().map(Session::csrfToken).orElse(""),
                criteria, data, req.param("msg"), error);
        return Response.html(BoardView.render(model));
    }

    /** POST /board/allocate */
    public Response allocate(Request req) {
        String jobId = req.requireParam("jobId");
        return act(req, jobId, () -> {
            Allocation a = service.allocate(jobId, req.requireParam("workerId"), req.longParam("jobVersion"));
            return "Allocated " + a.getJobId() + " to " + a.getWorkerId() + ".";
        });
    }

    /** POST /board/auto-allocate */
    public Response autoAllocate(Request req) {
        return act(req, null, () -> {
            List<AutoAllocationResult> results = service.autoAllocate(req.params("jobIds"), req.param("policy"));
            long ok = results.stream().filter(AutoAllocationResult::allocated).count();
            StringBuilder msg = new StringBuilder("Auto-allocate: " + ok + " of " + results.size()
                    + " job(s) allocated.");
            results.stream().filter(r -> !r.allocated())
                    .forEach(r -> msg.append(" ").append(r.jobId()).append(": ").append(r.message()).append("."));
            return msg.toString();
        });
    }

    /** POST /board/unassign */
    public Response unassign(Request req) {
        return act(req, null, () -> {
            service.unassign(req.requireParam("allocationId"));
            return "Allocation released; the job is pending again.";
        });
    }

    /**
     * Runs an action and redirects back to the board with a success or error message (PRG).
     * Which errors are shown on the page is decided by {@link ErrorMapper} (before: a hand-kept
     * multi-catch list here that had to match FrontController's instanceof chain).
     */
    private Response act(Request req, String jobId, Supplier<String> action) {
        Map<String, String> back = new LinkedHashMap<>();
        back.put("date", req.param("date"));
        back.put("priority", req.param("priority"));
        back.put("skill", req.param("skill"));
        try {
            back.put("msg", action.get());
        } catch (RuntimeException e) {
            if (!ErrorMapper.isUserCorrectable(e)) {
                throw e;
            }
            back.put("jobId", jobId);
            back.put("err", e.getMessage());
        }
        return Response.redirect(Html.url("/board", back));
    }
}
