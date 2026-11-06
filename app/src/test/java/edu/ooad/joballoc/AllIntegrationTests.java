package edu.ooad.joballoc;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests that need PostgreSQL (database joballoc_test). Run with `make test-int`. */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        edu.ooad.joballoc.it.PersistenceIT.class,
        edu.ooad.joballoc.it.ApiSecurityIT.class,
        edu.ooad.joballoc.it.ApiIntegrationPatternsIT.class,
        edu.ooad.joballoc.it.SsoIT.class
})
public class AllIntegrationTests {
}
