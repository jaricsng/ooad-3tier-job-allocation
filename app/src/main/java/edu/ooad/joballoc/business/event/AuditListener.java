package edu.ooad.joballoc.business.event;

import edu.ooad.joballoc.domain.AuditEvent;
import edu.ooad.joballoc.domain.event.DomainEvent;
import edu.ooad.joballoc.persistence.dao.AuditDAO;

/** Writes every domain event to the append-only audit trail. */
public class AuditListener implements DomainEventListener {

    private final AuditDAO audit;

    public AuditListener(AuditDAO audit) {
        this.audit = audit;
    }

    @Override
    public void on(DomainEvent e) {
        audit.append(new AuditEvent(e.type(), e.actor(), e.entityId(), e.detail(), e.occurredAt()));
    }
}
