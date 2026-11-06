package edu.ooad.joballoc.persistence.dao;

import edu.ooad.joballoc.domain.AppUser;

import java.util.Optional;

/** DAO for application users. */
public interface UserDAO extends GenericDAO<AppUser, String> {

    /** The account linked to this verified email (case-insensitive), if any. */
    Optional<AppUser> findByEmail(String email);
}
