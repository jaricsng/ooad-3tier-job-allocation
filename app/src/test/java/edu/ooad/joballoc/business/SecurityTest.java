package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.business.security.PasswordHasher;
import edu.ooad.joballoc.domain.AppUser;
import edu.ooad.joballoc.domain.Role;
import edu.ooad.joballoc.persistence.memory.InMemoryDAOFactory;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Password storage and brute-force protection (OWASP ASVS V2). */
public class SecurityTest {

    @Test
    public void hashesAreSaltedAndVerify() {
        String a = PasswordHasher.hash("s3cret!");
        String b = PasswordHasher.hash("s3cret!");
        assertNotEquals("random salt", a, b);
        assertTrue(PasswordHasher.verify("s3cret!", a));
        assertFalse(PasswordHasher.verify("wrong", a));
        assertFalse(PasswordHasher.verify("s3cret!", "garbage"));
        assertTrue(a.startsWith("pbkdf2-sha256$210000$"));
    }

    @Test
    public void lockoutAfterRepeatedFailures() {
        InMemoryDAOFactory daos = new InMemoryDAOFactory();
        daos.userDao().save(new AppUser("sam", PasswordHasher.hash("right"), Role.SUPERVISOR, "Sam"));
        AuthService auth = new AuthService(daos.userDao(), Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),
                ZoneOffset.UTC));

        assertTrue(auth.authenticate("sam", "right").isPresent());
        for (int i = 0; i < 5; i++) {
            assertFalse(auth.authenticate("sam", "wrong").isPresent());
        }
        assertFalse("locked even with the right password", auth.authenticate("sam", "right").isPresent());
        assertFalse("unknown users fail the same way", auth.authenticate("nobody", "x").isPresent());
        assertEquals(Role.SUPERVISOR, new AuthService(daos.userDao(), Clock.systemUTC())
                .authenticate("sam", "right").orElseThrow().role());
    }
}
