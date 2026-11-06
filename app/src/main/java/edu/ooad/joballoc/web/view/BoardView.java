package edu.ooad.joballoc.web.view;

import edu.ooad.joballoc.business.AllocationDetail;
import edu.ooad.joballoc.business.BoardData;
import edu.ooad.joballoc.business.Candidate;
import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.domain.Skill;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static edu.ooad.joballoc.web.view.Html.esc;

/**
 * BOUNDARY: renders the Job Allocation Board (the wireframe from the OOAD walkthrough).
 * It only displays the model it is given; it makes no business decisions. Buttons that change
 * data are shown only to supervisors, and the server enforces the same rule again.
 */
public final class BoardView {

    /** Everything the board needs: the filter, the snapshot from the facade, and flash messages. */
    public record Model(Actor actor, String csrf, JobCriteria criteria, BoardData data, String message, String error) {
        public LocalDate date() { return criteria.date(); }
        public Priority priority() { return criteria.priority(); }
        public String skill() { return criteria.skill(); }
        public List<Skill> skills() { return data.skills(); }
        public List<Job> pending() { return data.pending(); }
        public Job selected() { return data.selected(); }
        public List<Candidate> candidates() { return data.candidates(); }
        public List<AllocationDetail> allocations() { return data.allocations(); }
        public List<String> policies() { return data.policies(); }
    }

    private BoardView() { }

    public static String render(Model m) {
        boolean canEdit = m.actor().has(Role.SUPERVISOR);
        StringBuilder b = new StringBuilder("<main class=\"container-fluid py-3 px-lg-4\">");
        if (m.message() != null) {
            b.append("<div class=\"alert alert-success ok\" role=\"status\">").append(esc(m.message())).append("</div>");
        }
        if (m.error() != null) {
            b.append("<div class=\"alert alert-danger\" role=\"alert\">").append(esc(m.error())).append("</div>");
        }
        b.append(filters(m));
        b.append("<div class=\"row g-3\">");
        b.append("<div class=\"col-12 col-xl-7\">").append(pendingJobs(m, canEdit)).append("</div>");
        b.append("<div class=\"col-12 col-xl-5\">").append(candidates(m, canEdit)).append("</div>");
        b.append("<div class=\"col-12\">").append(allocations(m, canEdit)).append("</div>");
        b.append("</div></main>");
        return Html.page("Allocation Board", userBar(m), b.toString());
    }

    private static String userBar(Model m) {
        return "<span class=\"text-white small\">" + esc(m.actor().displayName())
                + " <span class=\"badge rounded-pill role-badge\">" + esc(m.actor().role().name()) + "</span></span>"
                + "<form method=\"post\" action=\"/logout\" class=\"m-0\">" + Html.hiddenCsrf(m.csrf())
                + "<button type=\"submit\" class=\"btn btn-sm btn-outline-light\">Sign out</button></form>";
    }

    /** Card header with a title and an optional count badge. */
    private static String header(String id, String title, String count) {
        return "<div class=\"card-header d-flex justify-content-between align-items-center gap-2\">"
                + "<h2 class=\"h6 mb-0 text-app\" id=\"" + id + "\">" + title + "</h2>"
                + (count == null ? "" : "<span class=\"badge rounded-pill text-bg-light border\">" + count + "</span>")
                + "</div>";
    }

    private static String empty(String text) {
        return "<p class=\"text-body-secondary p-3 mb-0\">" + text + "</p>";
    }

    private static String filters(Model m) {
        StringBuilder b = new StringBuilder("<form method=\"get\" action=\"/board\" class=\"card panel shadow-sm mb-3\""
                + " aria-label=\"Filters\"><div class=\"card-body row g-2 align-items-end\">");
        b.append("<div class=\"col-12 col-sm-6 col-md-3\"><label class=\"form-label small mb-1\" for=\"f-date\">Date</label>"
                + "<input class=\"form-control form-control-sm\" type=\"date\" id=\"f-date\" name=\"date\" value=\"")
                .append(m.date()).append("\"></div>");
        b.append("<div class=\"col-6 col-md-3\"><label class=\"form-label small mb-1\" for=\"f-priority\">Priority</label>"
                + "<select class=\"form-select form-select-sm\" id=\"f-priority\" name=\"priority\"><option value=\"\">All</option>");
        for (Priority p : Priority.values()) {
            b.append(option(p.name(), p.name(), p == m.priority()));
        }
        b.append("</select></div><div class=\"col-6 col-md-3\"><label class=\"form-label small mb-1\" for=\"f-skill\">Skill</label>"
                + "<select class=\"form-select form-select-sm\" id=\"f-skill\" name=\"skill\"><option value=\"\">Any</option>");
        for (Skill s : m.skills()) {
            b.append(option(s.getCode(), s.getName(), s.getCode().equals(m.skill())));
        }
        b.append("</select></div><div class=\"col-12 col-md-3 d-grid d-md-block\">"
                + "<button type=\"submit\" class=\"btn btn-sm btn-outline-primary\">Refresh</button></div></div></form>");
        return b.toString();
    }

    /** Bootstrap badge colour per priority: the most urgent stands out. */
    private static String priorityBadge(Priority p) {
        String colour = switch (p) {
            case CRITICAL -> "text-bg-danger";
            case HIGH -> "text-bg-warning";
            case MEDIUM -> "text-bg-info";
            default -> "text-bg-secondary";
        };
        return "<span class=\"badge " + colour + "\">" + p + "</span>";
    }

    private static String pendingJobs(Model m, boolean canEdit) {
        StringBuilder b = new StringBuilder("<section class=\"card panel shadow-sm h-100\" aria-labelledby=\"pending-h\">")
                .append(header("pending-h", "Pending Jobs", Integer.toString(m.pending().size())));
        if (m.pending().isEmpty()) {
            return b.append(empty("No pending jobs for this date and filter.")).append("</section>").toString();
        }
        b.append("<form method=\"post\" action=\"/board/auto-allocate\" class=\"d-flex flex-column h-100\">")
                .append(Html.hiddenCsrf(m.csrf())).append(hiddenFilters(m))
                .append("<div class=\"table-responsive\"><table id=\"pending-jobs\" class=\"table table-sm table-hover"
                        + " align-middle mb-0\"><thead class=\"table-light\"><tr>")
                .append(canEdit ? "<th scope=\"col\"><span class=\"visually-hidden\">Auto</span></th>" : "")
                .append("<th scope=\"col\">Job</th><th scope=\"col\">Title</th>"
                        + "<th scope=\"col\" class=\"d-none d-md-table-cell\">Skills</th><th scope=\"col\">Effort</th>"
                        + "<th scope=\"col\" class=\"d-none d-sm-table-cell\">Due</th><th scope=\"col\">Priority</th>"
                        + "<th scope=\"col\"><span class=\"visually-hidden\">Action</span></th></tr></thead><tbody>");
        for (Job j : m.pending()) {
            boolean sel = m.selected() != null && m.selected().getJobId().equals(j.getJobId());
            b.append("<tr").append(sel ? " class=\"selected\" aria-current=\"true\"" : "")
                    .append(" data-job=\"").append(esc(j.getJobId())).append("\">");
            if (canEdit) {
                b.append("<td><input class=\"form-check-input\" type=\"checkbox\" name=\"jobIds\" value=\"")
                        .append(esc(j.getJobId())).append("\" aria-label=\"Include ").append(esc(j.getJobId()))
                        .append(" in auto-allocate\"></td>");
            }
            b.append("<td class=\"nowrap fw-semibold\">").append(esc(j.getJobId())).append("</td><td>").append(esc(j.getTitle()))
                    .append("</td><td class=\"d-none d-md-table-cell small\">").append(esc(String.join(", ", j.getRequiredSkills())))
                    .append("</td><td>").append(j.getEffortHours()).append("h</td><td class=\"nowrap d-none d-sm-table-cell\">")
                    .append(j.getDueDate()).append("</td><td>").append(priorityBadge(j.getPriority())).append("</td><td class=\"text-end\">")
                    .append("<a class=\"btn btn-sm ").append(sel ? "btn-primary" : "btn-outline-primary").append("\" href=\"")
                    .append(esc(boardUrl(m, j.getJobId()))).append("\">").append(sel ? "Selected" : "Select").append("</a></td></tr>");
        }
        b.append("</tbody></table></div>");
        if (canEdit) {
            b.append("<div class=\"card-footer bg-white mt-auto\"><div class=\"input-group input-group-sm\">"
                    + "<label class=\"input-group-text\" for=\"policy\">Policy</label>"
                    + "<select class=\"form-select\" id=\"policy\" name=\"policy\">");
            for (String p : m.policies()) {
                b.append(option(p, p, false));
            }
            b.append("</select><button type=\"submit\" class=\"btn btn-primary\" id=\"auto-allocate\">Auto-Allocate</button>"
                    + "</div><div class=\"form-text\">Tick jobs, choose a policy, then auto-allocate.</div></div>");
        }
        return b.append("</form></section>").toString();
    }

    private static String candidates(Model m, boolean canEdit) {
        StringBuilder b = new StringBuilder("<section class=\"card panel shadow-sm h-100\" aria-labelledby=\"cand-h\">");
        if (m.selected() == null) {
            return b.append(header("cand-h", "Eligible Workers", null))
                    .append(empty("Select a job to see who can take it.")).append("</section>").toString();
        }
        Job j = m.selected();
        b.append(header("cand-h", "Eligible Workers for " + esc(j.getJobId()), null))
                .append("<p class=\"small text-body-secondary px-3 pt-2 mb-2\">").append(esc(j.getTitle()))
                .append(" · needs ").append(esc(String.join(", ", j.getRequiredSkills()))).append(" · ")
                .append(j.getEffortHours()).append("h on ").append(j.getWorkDate()).append("</p>");
        b.append("<div class=\"table-responsive\"><table id=\"candidates\" class=\"table table-sm align-middle mb-0\">"
                + "<thead class=\"table-light\"><tr><th scope=\"col\">Worker</th>"
                + "<th scope=\"col\" class=\"d-none d-sm-table-cell\">Skills</th><th scope=\"col\">Load</th>"
                + "<th scope=\"col\">Left</th><th scope=\"col\"><span class=\"visually-hidden\">Action</span></th></tr></thead><tbody>");
        for (Candidate c : m.candidates()) {
            int capacity = c.worker().getDailyCapacityHours();
            b.append("<tr class=\"").append(c.eligible() ? "eligible" : "ineligible").append("\" data-worker=\"")
                    .append(esc(c.worker().getWorkerId())).append("\"><td class=\"fw-semibold\">").append(esc(c.worker().getName()))
                    .append("</td><td class=\"d-none d-sm-table-cell small\">").append(esc(String.join(", ", c.worker().getSkills())))
                    .append("</td><td class=\"nowrap\"><meter class=\"load\" min=\"0\" max=\"").append(capacity)
                    .append("\" low=\"").append(capacity / 2).append("\" high=\"").append(capacity - 1).append("\" value=\"")
                    .append(c.allocatedHours()).append("\" title=\"").append(c.allocatedHours()).append("h of ").append(capacity)
                    .append("h\"></meter> <span class=\"small\">").append(c.allocatedHours()).append("/").append(capacity)
                    .append("h</span></td><td>").append(c.remainingHours()).append("h</td><td class=\"text-end\">");
            if (c.eligible() && canEdit) {
                b.append("<form method=\"post\" action=\"/board/allocate\" class=\"m-0\">").append(Html.hiddenCsrf(m.csrf()))
                        .append(hiddenFilters(m))
                        .append(hidden("jobId", j.getJobId())).append(hidden("workerId", c.worker().getWorkerId()))
                        .append(hidden("jobVersion", Long.toString(j.getVersion())))
                        .append("<button type=\"submit\" class=\"btn btn-sm btn-success\">Allocate</button></form>");
            } else if (c.eligible()) {
                b.append("<span class=\"badge text-bg-success\">eligible</span>");
            } else {
                b.append("<span class=\"reason\">").append(esc(c.reason())).append("</span>");
            }
            b.append("</td></tr>");
        }
        return b.append("</tbody></table></div></section>").toString();
    }

    private static String allocations(Model m, boolean canEdit) {
        StringBuilder b = new StringBuilder("<section class=\"card panel panel-allocations shadow-sm\" aria-labelledby=\"alloc-h\">")
                .append(header("alloc-h", "Allocations on " + m.date(), Integer.toString(m.allocations().size())));
        if (m.allocations().isEmpty()) {
            return b.append(empty("Nothing allocated yet for this date.")).append("</section>").toString();
        }
        b.append("<div class=\"table-responsive\"><table id=\"allocations\" class=\"table table-sm table-striped align-middle mb-0\">"
                + "<thead class=\"table-light\"><tr><th scope=\"col\">Job</th><th scope=\"col\">Title</th>"
                + "<th scope=\"col\">Worker</th><th scope=\"col\">Hours</th><th scope=\"col\" class=\"d-none d-md-table-cell\">By</th>"
                + "<th scope=\"col\"><span class=\"visually-hidden\">Action</span></th></tr></thead><tbody>");
        for (AllocationDetail d : m.allocations()) {
            b.append("<tr data-job=\"").append(esc(d.job().getJobId())).append("\"><td class=\"nowrap fw-semibold\">")
                    .append(esc(d.job().getJobId())).append("</td><td>").append(esc(d.job().getTitle())).append("</td><td>")
                    .append(esc(d.worker() == null ? d.allocation().getWorkerId() : d.worker().getName()))
                    .append("</td><td>").append(d.allocation().getHours()).append("h</td><td class=\"d-none d-md-table-cell\">")
                    .append(esc(d.allocation().getAllocatedBy())).append("</td><td class=\"text-end\">");
            if (canEdit) {
                b.append("<form method=\"post\" action=\"/board/unassign\" class=\"m-0\">").append(Html.hiddenCsrf(m.csrf()))
                        .append(hiddenFilters(m)).append(hidden("allocationId", d.allocation().getAllocationId()))
                        .append("<button type=\"submit\" class=\"btn btn-sm btn-outline-danger\">Unassign</button></form>");
            }
            b.append("</td></tr>");
        }
        return b.append("</tbody></table></div></section>").toString();
    }

    private static String boardUrl(Model m, String jobId) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("date", m.date().toString());
        p.put("priority", m.priority() == null ? null : m.priority().name());
        p.put("skill", m.skill());
        p.put("jobId", jobId);
        return Html.url("/board", p);
    }

    private static String hiddenFilters(Model m) {
        return hidden("date", m.date().toString())
                + (m.priority() == null ? "" : hidden("priority", m.priority().name()))
                + (m.skill() == null ? "" : hidden("skill", m.skill()));
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + esc(name) + "\" value=\"" + esc(value) + "\">";
    }

    private static String option(String value, String label, boolean selected) {
        return "<option value=\"" + esc(value) + "\"" + (selected ? " selected" : "") + ">" + esc(label) + "</option>";
    }
}
