package edu.ooad.joballoc.persistence;

import edu.ooad.joballoc.persistence.orm.ConnectionPool;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** SINGLETON contract. No database needed: the pool opens connections lazily. */
public class ConnectionPoolTest {

    @After
    public void reset() {
        ConnectionPool.shutdown();
    }

    @Test
    public void sameSettingsReturnTheSameInstance() {
        ConnectionPool a = ConnectionPool.initialize("jdbc:postgresql://h/db", "u", "p", 2);
        assertSame(a, ConnectionPool.initialize("jdbc:postgresql://h/db", "u", "p", 2));
        assertSame(a, ConnectionPool.getInstance());
    }

    @Test
    public void differentSettingsFailFastInsteadOfReturningTheOldPool() {
        ConnectionPool.initialize("jdbc:postgresql://h/db", "u", "p", 2);
        assertThrows(IllegalStateException.class,
                () -> ConnectionPool.initialize("jdbc:postgresql://h/other", "u", "p", 2));
    }
}
