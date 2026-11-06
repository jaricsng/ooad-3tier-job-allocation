package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit record (the database rejects UPDATE and DELETE on this table). */
@Entity
@Table("audit_event")
public class AuditEvent {

    @Id @Column("event_id") private String eventId;
    @Column("event_type") private String eventType;
    @Column("actor") private String actor;
    @Column("entity_id") private String entityId;
    @Column("detail") private String detail;
    @Column("occurred_at") private Instant occurredAt;

    protected AuditEvent() { }

    public AuditEvent(String eventType, String actor, String entityId, String detail, Instant occurredAt) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.actor = actor;
        this.entityId = entityId;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public String getEntityId() { return entityId; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
