package edu.ooad.joballoc;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Fast tests: no database or network. Run with `make test`. */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        edu.ooad.joballoc.domain.WorkerTest.class,
        edu.ooad.joballoc.domain.JobTest.class,
        edu.ooad.joballoc.business.AllocationServiceTest.class,
        edu.ooad.joballoc.business.PolicyTest.class,
        edu.ooad.joballoc.business.SecurityTest.class,
        edu.ooad.joballoc.business.SsoAccountLinkingTest.class,
        edu.ooad.joballoc.web.WebUnitTest.class,
        edu.ooad.joballoc.web.http.HttpLayerTest.class,
        edu.ooad.joballoc.web.view.StaticAssetsTest.class,
        edu.ooad.joballoc.web.view.BoardViewTest.class,
        edu.ooad.joballoc.web.sso.IdTokenVerifierTest.class,
        edu.ooad.joballoc.persistence.ConnectionPoolTest.class,
        edu.ooad.joballoc.persistence.DialectTest.class,
        edu.ooad.joballoc.storage.InMemoryStorageTest.class,
        edu.ooad.joballoc.config.ConfigurationTest.class,
        edu.ooad.joballoc.web.api.OpenApiContractTest.class,
        edu.ooad.joballoc.arch.ArchitectureTest.class,
        edu.ooad.joballoc.arch.NamingConventionTest.class
})
public class AllUnitTests {
}
