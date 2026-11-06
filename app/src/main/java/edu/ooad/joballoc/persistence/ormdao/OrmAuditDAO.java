package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.domain.AuditEvent;
import edu.ooad.joballoc.persistence.dao.AuditDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.util.List;

/** Append-only audit DAO; the generic update/delete are not exposed through {@link AuditDAO}. */
public class OrmAuditDAO extends AbstractOrmDAO<AuditEvent, String> implements AuditDAO {

    /** Upper bound for findRecent, so a caller cannot load the whole audit trail at once. */
    private static final int MAX_RECENT = 500;

    public OrmAuditDAO(TransactionManager tx) {
        super(tx, AuditEvent.class);
    }

    @Override
    public void append(AuditEvent event) {
        save(event);
    }

    @Override
    public List<AuditEvent> findAll() {
        return query("order by occurred_at, event_id");
    }

    @Override
    public List<AuditEvent> findRecent(int limit) {
        return query("order by occurred_at desc " + dialect.firstRowsClause(),
                Math.max(1, Math.min(limit, MAX_RECENT)));
    }
}
