package edu.ooad.joballoc.persistence.dao;

import edu.ooad.joballoc.domain.Allocation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** DAO for {@link Allocation}. */
public interface AllocationDAO extends GenericDAO<Allocation, String> {
    List<Allocation> findActiveByWorker(String workerId, LocalDate on);

    List<Allocation> findActiveByDate(LocalDate on);

    Optional<Allocation> findActiveByJob(String jobId);
}
