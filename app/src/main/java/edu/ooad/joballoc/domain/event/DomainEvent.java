package edu.ooad.joballoc.domain.event;

import java.time.Instant;

/** Something that happened in the domain that other parts of the system may react to (OBSERVER). */
public interface DomainEvent {
    String type();

    String actor();

    String entityId();

    String detail();

    Instant occurredAt();
}
