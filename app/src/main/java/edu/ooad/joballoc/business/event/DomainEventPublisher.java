package edu.ooad.joballoc.business.event;

import edu.ooad.joballoc.domain.event.DomainEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OBSERVER (GoF behavioural) subject. Before: allocate() called audit, e-mail and metrics code
 * directly, so every new side effect edited the core use case. After: listeners subscribe here.
 *
 * <p>Listeners run synchronously inside the caller's transaction, so the audit record commits or
 * rolls back together with the allocation (discussion point: what changes if listeners are async?).
 */
public class DomainEventPublisher {

    private final List<DomainEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(DomainEventListener listener) {
        listeners.add(listener);
    }

    public void publish(DomainEvent event) {
        for (DomainEventListener l : listeners) {
            l.on(event);
        }
    }

    public void publishAll(List<? extends DomainEvent> events) {
        events.forEach(this::publish);
    }
}
