package edu.ooad.joballoc.storage;

import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.AppUser;
import edu.ooad.joballoc.domain.AuditEvent;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Skill;
import edu.ooad.joballoc.domain.Worker;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.dao.GenericDAO;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Copies all data from one storage to another, for example PostgreSQL to MySQL, or a database into
 * memory as a dry run. It is written only against {@link DAOFactory} and the transaction port, so the
 * same twenty lines work for every pair of storages: the payoff of the ABSTRACT FACTORY.
 *
 * <p>Rules: parents before children (skills, users, workers, jobs, allocations, audit); rows that
 * already exist in the target (same id) are kept, not overwritten; versions are copied as they are;
 * the whole write is one target transaction, so a failure leaves the target unchanged.
 */
public final class StorageTransfer {

    /** Rows copied and rows skipped (already in the target), per entity type, in copy order. */
    public record Report(Map<String, int[]> counts) {

        public int copied(String type) {
            return counts.get(type)[0];
        }

        public int skipped(String type) {
            return counts.get(type)[1];
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            counts.forEach((type, c) ->
                    b.append(String.format("%-12s copied %5d, already present %5d%n", type, c[0], c[1])));
            return b.toString();
        }
    }

    /** Everything read from the source in ONE transaction: a consistent snapshot. */
    private record Snapshot(List<Skill> skills, List<AppUser> users, List<Worker> workers, List<Job> jobs,
            List<Allocation> allocations, List<AuditEvent> audit) { }

    private StorageTransfer() { }

    public static Report copy(Storage from, Storage to) {
        DAOFactory src = from.daos();
        Snapshot data = from.transactions().inTransaction(() -> new Snapshot(src.skillDao().findAll(),
                src.userDao().findAll(), src.workerDao().findAll(), src.jobDao().findAll(),
                src.allocationDao().findAll(), src.auditDao().findAll()));
        DAOFactory dst = to.daos();
        return to.transactions().inTransaction(() -> {
            Map<String, int[]> counts = new LinkedHashMap<>();
            counts.put("skill", copyAll(data.skills(), dst.skillDao(), Skill::getCode));
            counts.put("app_user", copyAll(data.users(), dst.userDao(), AppUser::getUsername));
            counts.put("worker", copyAll(data.workers(), dst.workerDao(), Worker::getWorkerId));
            counts.put("job", copyAll(data.jobs(), dst.jobDao(), Job::getJobId));
            counts.put("allocation", copyAll(data.allocations(), dst.allocationDao(), Allocation::getAllocationId));
            Set<String> present = new HashSet<>();
            dst.auditDao().findAll().forEach(e -> present.add(e.getEventId()));
            int[] audit = new int[2];
            for (AuditEvent e : data.audit()) {
                if (present.contains(e.getEventId())) {
                    audit[1]++;
                } else {
                    dst.auditDao().append(e);
                    audit[0]++;
                }
            }
            counts.put("audit_event", audit);
            return new Report(counts);
        });
    }

    private static <T> int[] copyAll(List<T> rows, GenericDAO<T, String> target, Function<T, String> id) {
        int[] c = new int[2];
        for (T row : rows) {
            if (target.findById(id.apply(row)).isPresent()) {
                c[1]++;
            } else {
                target.save(row);
                c[0]++;
            }
        }
        return c;
    }
}
