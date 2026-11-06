package edu.ooad.joballoc.business.security;

import edu.ooad.joballoc.domain.Role;

/** The authenticated user on whose behalf a business operation runs. */
public record Actor(String username, Role role, String displayName) {
    public boolean has(Role required) {
        return role.includes(required);
    }
}
