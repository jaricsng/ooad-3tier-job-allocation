package edu.ooad.joballoc.persistence.dao;

import edu.ooad.joballoc.domain.AuditEvent;

import java.util.List;

/** Append-only DAO for audit events: there is deliberately no update or delete. */
public interface AuditDAO {
    void append(AuditEvent event);

    List<AuditEvent> findRecent(int limit);

    /** The whole trail, oldest first (for backups and copying between databases; not for screens). */
    List<AuditEvent> findAll();
}
