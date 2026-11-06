package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.domain.AppUser;
import edu.ooad.joballoc.persistence.dao.UserDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

import java.util.Locale;
import java.util.Optional;

/** Relational implementation of {@link UserDAO}. */
public class OrmUserDAO extends AbstractOrmDAO<AppUser, String> implements UserDAO {
    public OrmUserDAO(TransactionManager tx) {
        super(tx, AppUser.class);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return email == null ? Optional.empty()
                : query("where email = ?", email.toLowerCase(Locale.ROOT)).stream().findFirst();
    }
}
