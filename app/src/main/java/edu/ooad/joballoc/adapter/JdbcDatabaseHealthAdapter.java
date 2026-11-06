package edu.ooad.joballoc.adapter;

import edu.ooad.joballoc.business.port.DatabaseHealth;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * ADAPTER: answers the {@link DatabaseHealth} port by running the dialect's validation query through the
 * persistence tier's {@link TransactionManager}. Before, this JDBC code sat in HealthController
 * (presentation tier) and the architecture test needed a special exception for it.
 */
public final class JdbcDatabaseHealthAdapter implements DatabaseHealth {

    private final TransactionManager tx;

    public JdbcDatabaseHealthAdapter(TransactionManager tx) {
        this.tx = tx;
    }

    @Override
    public boolean isUp() {
        try {
            return tx.withConnection(con -> {
                try (Statement st = con.createStatement()) {
                    return st.execute(tx.dialect().validationQuery());
                } catch (SQLException e) {
                    return false;
                }
            });
        } catch (RuntimeException e) {
            return false;
        }
    }
}
