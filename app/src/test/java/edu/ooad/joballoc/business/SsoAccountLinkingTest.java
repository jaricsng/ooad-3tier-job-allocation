package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.business.security.AuthService.Provisioning;
import edu.ooad.joballoc.business.security.ExternalIdentity;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;
import org.junit.Test;

import java.time.Clock;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Which account a verified single sign-on identity may use (business rules, no protocol). */
public class SsoAccountLinkingTest {

    private final InMemoryDAOFactory daos = new InMemoryDAOFactory().withReferenceData();
    private final AuthService auth = new AuthService(daos.userDao(), Clock.systemUTC());

    private static ExternalIdentity id(String email, boolean verified) {
        return new ExternalIdentity("google", "sub-1", email, verified, "Some One");
    }

    @Test
    public void aVerifiedEmailSignsIntoTheLinkedAccountWithItsOwnRole() {
        Actor a = auth.authenticateExternal(id("Supervisor@Example.com", true), Provisioning.NONE).orElseThrow();
        assertEquals("supervisor", a.username());
        assertEquals("role comes from this app, not from the provider", Role.SUPERVISOR, a.role());
    }

    @Test
    public void anUnverifiedEmailIsNeverTrusted() {
        assertTrue(auth.authenticateExternal(id("supervisor@example.com", false), Provisioning.VIEWER).isEmpty());
    }

    @Test
    public void anUnknownEmailIsDeniedByDefault() {
        assertTrue(auth.authenticateExternal(id("new.person@example.com", true), Provisioning.NONE).isEmpty());
        assertTrue(daos.userDao().findByEmail("new.person@example.com").isEmpty());
    }

    @Test
    public void autoProvisioningCreatesOnlyAViewerWithoutAPassword() {
        Optional<Actor> a = auth.authenticateExternal(id("new.person@example.com", true), Provisioning.VIEWER);
        assertEquals(Role.VIEWER, a.orElseThrow().role());
        assertEquals("google:sub-1", a.get().username());
        assertTrue("an SSO-only account cannot sign in with any password",
                auth.authenticate("google:sub-1", "sso-only").isEmpty());
        Actor again = auth.authenticateExternal(id("new.person@example.com", true), Provisioning.VIEWER).orElseThrow();
        assertEquals("the second sign-in links to the same account", "google:sub-1", again.username());
    }
}
