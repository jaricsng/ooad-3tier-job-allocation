package edu.ooad.joballoc.business;

import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Worker;

/** Read model for the Allocations list: an allocation with its job and worker. */
public record AllocationDetail(Allocation allocation, Job job, Worker worker) { }
