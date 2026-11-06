package edu.ooad.joballoc;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.domain.Worker;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

/** Shared fixtures for unit tests. */
public final class TestData {
    public static final LocalDate DAY = LocalDate.of(2026, 11, 5);
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneOffset.UTC);

    private TestData() { }

    public static Job job(String id, int hours, Priority p, String... skills) {
        return new Job(id, "Job " + id, Set.of(skills), hours, DAY, DAY.plusDays(1), p);
    }

    public static Worker worker(String id, int capacity, String... skills) {
        return new Worker(id, "Worker " + id, capacity, Set.of(skills));
    }
}
