package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.event.AuditListener;
import edu.ooad.joballoc.business.event.DomainEventPublisher;
import edu.ooad.joballoc.business.event.WorkloadMetricsListener;
import edu.ooad.joballoc.business.policy.PolicyFactory;
import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.persistence.dao.DAOFactory;

import java.time.Clock;

/**
 * FACTORY for the business tier: the one place that assembles the facade behind its proxy, the
 * policies, the event bus and its listeners.
 *
 * <p>Before: {@code App} and {@code AllocationServiceTest} each repeated the same eight lines of
 * wiring, so a new listener had to be added in two places (and tests could silently test a
 * differently wired service). After: both call {@link #create}.
 *
 * @param service the {@link AllocationService} FACADE, already wrapped by {@link SecuredTransactionalProxy}
 * @param auth    sign-in service
 * @param metrics event counters shown on /health
 */
public record BusinessTier(AllocationService service, AuthService auth, WorkloadMetricsListener metrics) {

    public static BusinessTier create(DAOFactory daos, TransactionRunner tx, Clock clock) {
        DomainEventPublisher events = new DomainEventPublisher();
        WorkloadMetricsListener metrics = new WorkloadMetricsListener();
        events.subscribe(new AuditListener(daos.auditDao()));
        events.subscribe(metrics);
        AllocationService real = new AllocationServiceImpl(daos, new PolicyFactory(), events, clock);
        AllocationService service = SecuredTransactionalProxy.wrap(AllocationService.class, real, tx);
        return new BusinessTier(service, new AuthService(daos.userDao(), clock), metrics);
    }
}
