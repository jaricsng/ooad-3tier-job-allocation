package edu.ooad.joballoc.business;

import edu.ooad.joballoc.domain.Worker;

/** A worker shown in the Eligible Workers panel, with why they can or cannot take the job. */
public record Candidate(Worker worker, int allocatedHours, int remainingHours, boolean eligible, String reason) { }
