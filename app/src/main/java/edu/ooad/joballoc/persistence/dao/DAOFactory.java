package edu.ooad.joballoc.persistence.dao;

/**
 * ABSTRACT FACTORY (GoF creational): creates a matching family of DAOs.
 *
 * <p>Before: services called {@code new JdbcJobDAO(...)} directly, so unit tests needed a database.
 * After: services ask a factory. {@code OrmDAOFactory} returns PostgreSQL-backed DAOs;
 * {@code InMemoryDAOFactory} returns in-memory DAOs for fast unit tests.
 */
public interface DAOFactory {
    JobDAO jobDao();

    WorkerDAO workerDao();

    AllocationDAO allocationDao();

    AuditDAO auditDao();

    SkillDAO skillDao();

    UserDAO userDao();
}
