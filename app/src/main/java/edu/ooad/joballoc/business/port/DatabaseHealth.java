package edu.ooad.joballoc.business.port;

/**
 * PORT for the /health endpoint: "is the database reachable?". Before, HealthController imported
 * the persistence tier's TransactionManager and ran JDBC itself (presentation skipping the business
 * tier). Now it depends on this interface; an ADAPTER in {@code edu.ooad.joballoc.adapter} answers it.
 */
@FunctionalInterface
public interface DatabaseHealth {
    boolean isUp();
}
