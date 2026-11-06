package edu.ooad.joballoc.business.event;

import edu.ooad.joballoc.domain.event.DomainEvent;

/** OBSERVER: reacts to domain events. */
public interface DomainEventListener {
    void on(DomainEvent event);
}
