package edu.ooad.joballoc.persistence.ormdao;

import edu.ooad.joballoc.domain.Skill;
import edu.ooad.joballoc.persistence.dao.SkillDAO;
import edu.ooad.joballoc.persistence.orm.TransactionManager;

/** Relational implementation of {@link SkillDAO}. */
public class OrmSkillDAO extends AbstractOrmDAO<Skill, String> implements SkillDAO {
    public OrmSkillDAO(TransactionManager tx) {
        super(tx, Skill.class);
    }
}
