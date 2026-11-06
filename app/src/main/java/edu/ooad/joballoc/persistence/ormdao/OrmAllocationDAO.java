package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.persistence.dao.AllocationDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Relational implementation of {@link AllocationDAO}. */
public class OrmAllocationDAO extends AbstractOrmDAO<Allocation, String> implements AllocationDAO {

    public OrmAllocationDAO(TransactionManager tx) {
        super(tx, Allocation.class);
    }

    @Override
    public List<Allocation> findActiveByWorker(String workerId, LocalDate on) {
        return query("where status = 'ACTIVE' and worker_id = ? and work_date = ? order by allocated_at", workerId, on);
    }

    @Override
    public List<Allocation> findActiveByDate(LocalDate on) {
        return query("where status = 'ACTIVE' and work_date = ? order by worker_id, allocated_at", on);
    }

    @Override
    public Optional<Allocation> findActiveByJob(String jobId) {
        return query("where status = 'ACTIVE' and job_id = ?", jobId).stream().findFirst();
    }
}
