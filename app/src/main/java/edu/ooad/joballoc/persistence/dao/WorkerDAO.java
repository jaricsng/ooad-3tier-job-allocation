package edu.ooad.joballoc.persistence.dao;

import edu.ooad.joballoc.domain.Worker;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** DAO for the Worker aggregate (a worker plus its active allocations on a date). */
public interface WorkerDAO extends GenericDAO<Worker, String> {
    Optional<Worker> findWithAllocations(String workerId, LocalDate on);

    List<Worker> findAllWithAllocations(LocalDate on);
}
