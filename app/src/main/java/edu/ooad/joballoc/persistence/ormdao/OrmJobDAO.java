package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.persistence.dao.JobDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Relational implementation of {@link JobDAO}; portable SQL, product differences from the dialect. */
public class OrmJobDAO extends AbstractOrmDAO<Job, String> implements JobDAO {

    /**
     * SQL rank of the priority column, generated from the {@link Priority} enum so the database order
     * can never drift from the Java order. A CASE expression is standard SQL, so it runs on every
     * product (before: PostgreSQL-only {@code array_position(array[...], priority)}).
     */
    private static final String PRIORITY_RANK = "case priority "
            + Arrays.stream(Priority.values()).map(p -> "when '" + p.name() + "' then " + p.ordinal())
                    .collect(Collectors.joining(" "))
            + " end";

    public OrmJobDAO(TransactionManager tx) {
        super(tx, Job.class);
    }

    @Override
    public List<Job> findPending(JobCriteria c) {
        StringBuilder where = new StringBuilder("where status = 'PENDING' and work_date = ?");
        List<Object> params = new ArrayList<>();
        params.add(c.date());
        if (c.priority() != null) {
            where.append(" and priority = ?");
            params.add(c.priority().name());
        }
        if (c.skill() != null) {
            // Portable: a correlated EXISTS on the collection table (before: PostgreSQL "? = any(array)").
            where.append(" and exists (select 1 from job_skill js where js.job_id = job.job_id and js.skill_code = ?)");
            params.add(c.skill());
        }
        where.append(" order by ").append(PRIORITY_RANK).append(" desc, due_date, job_id");
        return query(where.toString(), params.toArray());
    }
}
