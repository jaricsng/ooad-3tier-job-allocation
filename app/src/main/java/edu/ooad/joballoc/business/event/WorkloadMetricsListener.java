package edu.ooad.joballoc.business.event;

import edu.ooad.joballoc.domain.event.DomainEvent;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Counts events by type since start-up; exposed on /health for the runbook. */
public class WorkloadMetricsListener implements DomainEventListener {

    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();

    @Override
    public void on(DomainEvent event) {
        counts.computeIfAbsent(event.type(), k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> m = new TreeMap<>();
        counts.forEach((k, v) -> m.put(k, v.get()));
        return m;
    }
}
