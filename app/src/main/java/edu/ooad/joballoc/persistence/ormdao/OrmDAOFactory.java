package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.persistence.dao.AllocationDAO;
import edu.ooad.joballoc.persistence.dao.AuditDAO;
import edu.ooad.joballoc.persistence.dao.DAOFactory;
import edu.ooad.joballoc.persistence.dao.JobDAO;
import edu.ooad.joballoc.persistence.dao.SkillDAO;
import edu.ooad.joballoc.persistence.dao.UserDAO;
import edu.ooad.joballoc.persistence.dao.WorkerDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

/**
 * Concrete factory for relational-database DAOs (ABSTRACT FACTORY). One factory serves every
 * supported product: the DAOs read the {@code SqlDialect} from the {@link TransactionManager}.
 */
public class OrmDAOFactory implements DAOFactory {

    private final JobDAO jobs;
    private final AllocationDAO allocations;
    private final WorkerDAO workers;
    private final AuditDAO audit;
    private final SkillDAO skills;
    private final UserDAO users;

    public OrmDAOFactory(TransactionManager tx) {
        this.jobs = new OrmJobDAO(tx);
        this.allocations = new OrmAllocationDAO(tx);
        this.workers = new OrmWorkerDAO(tx, allocations);
        this.audit = new OrmAuditDAO(tx);
        this.skills = new OrmSkillDAO(tx);
        this.users = new OrmUserDAO(tx);
    }

    @Override public JobDAO jobDao() { return jobs; }
    @Override public WorkerDAO workerDao() { return workers; }
    @Override public AllocationDAO allocationDao() { return allocations; }
    @Override public AuditDAO auditDao() { return audit; }
    @Override public SkillDAO skillDao() { return skills; }
    @Override public UserDAO userDao() { return users; }
}
