package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.domain.Allocation;
import edu.ooad.joballoc.domain.Worker;
import edu.ooad.joballoc.persistence.dao.AllocationDAO;
import edu.ooad.joballoc.persistence.dao.WorkerDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Relational implementation of {@link WorkerDAO}: assembles the Worker aggregate with its allocations. */
public class OrmWorkerDAO extends AbstractOrmDAO<Worker, String> implements WorkerDAO {

    private final AllocationDAO allocations;

    public OrmWorkerDAO(TransactionManager tx, AllocationDAO allocations) {
        super(tx, Worker.class);
        this.allocations = allocations;
    }

    @Override
    public Optional<Worker> findWithAllocations(String workerId, LocalDate on) {
        Optional<Worker> worker = findById(workerId);
        worker.ifPresent(w -> w.attachAllocations(allocations.findActiveByWorker(workerId, on)));
        return worker;
    }

    @Override
    public List<Worker> findAllWithAllocations(LocalDate on) {
        List<Worker> workers = findAll();
        Map<String, List<Allocation>> byWorker = allocations.findActiveByDate(on).stream()
                .collect(Collectors.groupingBy(Allocation::getWorkerId));
        workers.forEach(w -> w.attachAllocations(byWorker.getOrDefault(w.getWorkerId(), List.of())));
        return workers;
    }
}
